package com.github.serezhka.airplay.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

final class ServerProcessManager implements AutoCloseable {
    enum State {
        STOPPED,
        STARTING,
        RUNNING,
        STOPPING,
        FAILED
    }

    enum Detail {
        SERVICE_STOPPED,
        STARTING_SERVICE,
        WAITING_FOR_CONTROL,
        START_FAILED,
        STOPPING_SERVICE,
        FULLSCREEN_MODE,
        WINDOWED_MODE,
        SERVICE_RUNNING,
        SERVICE_INITIALIZING,
        CONTROL_UNAVAILABLE,
        ABNORMAL_EXIT
    }

    enum LogType {
        RAW,
        PROCESS_STARTED,
        QUIT_ACCEPTED,
        QUIT_FALLBACK,
        SWITCHED_FULLSCREEN,
        SWITCHED_WINDOWED,
        CONTROL_DISCONNECTED,
        LOG_READ_FAILED,
        PROCESS_EXITED,
        LISTENER_FAILED
    }

    record LogEntry(LogType type, Object argument1, Object argument2) {
        static LogEntry raw(String line) {
            return new LogEntry(LogType.RAW, line, null);
        }

        static LogEntry message(LogType type, Object... arguments) {
            Object first = arguments.length > 0 ? arguments[0] : null;
            Object second = arguments.length > 1 ? arguments[1] : null;
            return new LogEntry(type, first, second);
        }
    }

    record Snapshot(
            State state,
            long pid,
            boolean controlConnected,
            boolean fullscreenAvailable,
            boolean fullscreen,
            Instant startedAt,
            Detail detail,
            Object detailArgument) {

        static Snapshot stopped() {
            return new Snapshot(
                    State.STOPPED, 0, false, false, false, null, Detail.SERVICE_STOPPED, null);
        }

        String uptime() {
            if (startedAt == null) {
                return "--:--:--";
            }
            Duration duration = Duration.between(startedAt, Instant.now());
            long seconds = Math.max(0, duration.toSeconds());
            return "%02d:%02d:%02d".formatted(seconds / 3600, seconds / 60 % 60, seconds % 60);
        }
    }

    private record ControlSession(int port, String token) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Object monitor = new Object();
    private final Path baseDirectory;
    private final Path javaExecutable;
    private final Path serverJar;
    private final Path configuration;
    private final ControlClient controlClient = new ControlClient();
    private final Consumer<Snapshot> stateListener;
    private final Consumer<LogEntry> logListener;
    private final ExecutorService lifecycleExecutor = Executors.newSingleThreadExecutor(
            namedDaemonFactory("airplay-launcher-lifecycle"));
    private final ScheduledExecutorService statusExecutor = Executors.newSingleThreadScheduledExecutor(
            namedDaemonFactory("airplay-launcher-status"));

    private Process process;
    private ControlSession controlSession;
    private Snapshot snapshot = Snapshot.stopped();
    private boolean expectedStop;
    private boolean closed;
    private long generation;

    ServerProcessManager(
            Path baseDirectory,
            Path configuration,
            Consumer<Snapshot> stateListener,
            Consumer<LogEntry> logListener) {
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
        this.javaExecutable = this.baseDirectory.resolve("jre/bin/javaw.exe");
        this.serverJar = this.baseDirectory.resolve("java-airplay-server-fixed.jar");
        this.configuration = configuration.toAbsolutePath().normalize();
        this.stateListener = Objects.requireNonNull(stateListener, "stateListener");
        this.logListener = Objects.requireNonNull(logListener, "logListener");
        statusExecutor.scheduleWithFixedDelay(this::pollStatus, 300, 700, TimeUnit.MILLISECONDS);
    }

    Snapshot snapshot() {
        synchronized (monitor) {
            return snapshot;
        }
    }

    CompletableFuture<Void> startAsync() {
        return runLifecycle(this::startBlocking);
    }

    CompletableFuture<Void> stopAsync() {
        return runLifecycle(this::stopBlocking);
    }

    CompletableFuture<Void> restartAsync() {
        return runLifecycle(() -> {
            stopBlocking();
            startBlocking();
        });
    }

    CompletableFuture<Boolean> setFullscreenAsync(boolean fullscreen) {
        return CompletableFuture.supplyAsync(() -> setFullscreen(fullscreen), lifecycleExecutor);
    }

    private CompletableFuture<Void> runLifecycle(CheckedRunnable action) {
        return CompletableFuture.runAsync(() -> {
            try {
                action.run();
            } catch (Exception exception) {
                throw new CompletionException(exception);
            }
        }, lifecycleExecutor);
    }

    private void startBlocking() throws IOException {
        synchronized (monitor) {
            if (closed) {
                throw new LauncherIOException(LauncherMessages.Key.ERROR_LAUNCHER_CLOSING);
            }
            if (process != null && process.isAlive()) {
                return;
            }
        }
        requireFile(javaExecutable, LauncherMessages.Key.ERROR_MISSING_JAVA_RUNTIME);
        requireFile(serverJar, LauncherMessages.Key.ERROR_MISSING_SERVER_JAR);
        requireFile(configuration, LauncherMessages.Key.ERROR_MISSING_CONFIGURATION);

        int airPlayPort = new ConfigStore(configuration).load().airtunesPort();
        ControlSession newSession = new ControlSession(findAvailablePort(airPlayPort), randomToken());
        long newGeneration;
        synchronized (monitor) {
            expectedStop = false;
            newGeneration = ++generation;
            snapshot = new Snapshot(
                    State.STARTING, 0, false, false, false, Instant.now(),
                    Detail.STARTING_SERVICE, null);
        }
        publishSnapshot();

        List<String> command = List.of(
                javaExecutable.toString(),
                "-Dfile.encoding=UTF-8",
                "-jar",
                serverJar.toString(),
                "--spring.config.additional-location=" + configuration.toUri(),
                "--spring.output.ansi.enabled=NEVER",
                "--logging.file.name=",
                "--player.tray.enabled=false",
                "--launcher.control.enabled=true",
                "--launcher.control.port=" + newSession.port(),
                "--launcher.control.token=" + newSession.token());
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .directory(baseDirectory.toFile())
                .redirectErrorStream(true);
        configureEnvironment(processBuilder);

        try {
            Process startedProcess = processBuilder.start();
            boolean rejectStartedProcess;
            synchronized (monitor) {
                rejectStartedProcess = closed;
                if (!rejectStartedProcess) {
                    process = startedProcess;
                    controlSession = newSession;
                    snapshot = new Snapshot(
                            State.STARTING,
                            startedProcess.pid(),
                            false,
                            false,
                            false,
                            Instant.now(),
                            Detail.WAITING_FOR_CONTROL,
                            null);
                }
            }
            if (rejectStartedProcess) {
                terminateProcessTreeForcibly(startedProcess);
                throw new LauncherIOException(
                        LauncherMessages.Key.ERROR_LAUNCHER_CLOSED_DURING_START);
            }
            logMessage(LogType.PROCESS_STARTED, startedProcess.pid(), newSession.port());
            publishSnapshot();
            startLogReader(startedProcess, newGeneration);
            startedProcess.onExit().thenAccept(exited -> handleProcessExit(exited, newGeneration));
        } catch (IOException exception) {
            synchronized (monitor) {
                process = null;
                controlSession = null;
                snapshot = new Snapshot(State.FAILED, 0, false, false, false, null,
                        Detail.START_FAILED, exception);
            }
            publishSnapshot();
            throw exception;
        }
    }

    private void stopBlocking() {
        Process target;
        ControlSession session;
        Map<Long, ProcessHandle> descendants = new LinkedHashMap<>();
        synchronized (monitor) {
            target = process;
            session = controlSession;
            if (target == null || !target.isAlive()) {
                process = null;
                controlSession = null;
                snapshot = Snapshot.stopped();
                publishSnapshotLater(snapshot);
                return;
            }
            expectedStop = true;
            snapshot = new Snapshot(
                    State.STOPPING,
                    target.pid(),
                    snapshot.controlConnected(),
                    snapshot.fullscreenAvailable(),
                    snapshot.fullscreen(),
                    snapshot.startedAt(),
                    Detail.STOPPING_SERVICE,
                    null);
        }
        publishSnapshot();
        rememberDescendants(target, descendants);

        if (session != null) {
            try {
                controlClient.quit(session.port(), session.token());
                logMessage(LogType.QUIT_ACCEPTED);
            } catch (IOException exception) {
                logMessage(LogType.QUIT_FALLBACK, exception);
            }
        }
        if (!waitFor(target, 1_500)) {
            rememberDescendants(target, descendants);
            terminateDescendants(descendants, false);
            target.destroy();
        }
        if (!waitFor(target, 800)) {
            rememberDescendants(target, descendants);
            terminateDescendants(descendants, true);
            target.destroyForcibly();
            waitFor(target, 500);
        }
        terminateDescendants(descendants, true);
    }

    private boolean setFullscreen(boolean fullscreen) {
        ControlSession session;
        Process target;
        synchronized (monitor) {
            session = controlSession;
            target = process;
        }
        if (session == null || target == null || !target.isAlive()) {
            throw new CompletionException(
                    new LauncherInputException(LauncherMessages.Key.ERROR_SERVICE_NOT_RUNNING));
        }
        try {
            boolean actual = controlClient.setFullscreen(session.port(), session.token(), fullscreen);
            synchronized (monitor) {
                if (session.equals(controlSession) && !expectedStop
                        && snapshot.state() != State.STOPPING) {
                    snapshot = new Snapshot(
                            snapshot.state(), snapshot.pid(), true, true, actual,
                            snapshot.startedAt(), actual ? Detail.FULLSCREEN_MODE : Detail.WINDOWED_MODE, null);
                }
            }
            logMessage(actual ? LogType.SWITCHED_FULLSCREEN : LogType.SWITCHED_WINDOWED);
            publishSnapshot();
            return actual;
        } catch (IOException exception) {
            throw new CompletionException(exception);
        }
    }

    private void pollStatus() {
        Process target;
        ControlSession session;
        synchronized (monitor) {
            target = process;
            session = controlSession;
            if (expectedStop || snapshot.state() == State.STOPPING) {
                return;
            }
        }
        if (target == null || session == null || !target.isAlive()) {
            return;
        }

        try {
            ControlClient.Status status = controlClient.status(session.port(), session.token());
            synchronized (monitor) {
                if (target != process || !session.equals(controlSession) || expectedStop
                        || snapshot.state() == State.STOPPING) {
                    return;
                }
                State state = status.running() ? State.RUNNING : State.STARTING;
                snapshot = new Snapshot(
                        state,
                        target.pid(),
                        true,
                        status.fullscreenAvailable(),
                        status.fullscreen(),
                        snapshot.startedAt(),
                        status.running() ? Detail.SERVICE_RUNNING : Detail.SERVICE_INITIALIZING,
                        null);
            }
            publishSnapshot();
        } catch (IOException exception) {
            boolean changed;
            synchronized (monitor) {
                if (target != process || !session.equals(controlSession) || expectedStop
                        || snapshot.state() == State.STOPPING) {
                    return;
                }
                changed = snapshot.controlConnected();
                snapshot = new Snapshot(
                        snapshot.state() == State.RUNNING ? State.RUNNING : State.STARTING,
                        target.pid(),
                        false,
                        false,
                        snapshot.fullscreen(),
                        snapshot.startedAt(),
                        snapshot.state() == State.RUNNING
                                ? Detail.CONTROL_UNAVAILABLE : Detail.WAITING_FOR_CONTROL,
                        null);
            }
            if (changed) {
                logMessage(LogType.CONTROL_DISCONNECTED, exception);
            }
            publishSnapshot();
        }
    }

    private void startLogReader(Process target, long processGeneration) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(target.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (monitor) {
                        if (generation != processGeneration) {
                            return;
                        }
                    }
                    logRaw(line);
                }
            } catch (IOException exception) {
                if (target.isAlive()) {
                    logMessage(LogType.LOG_READ_FAILED, exception);
                }
            }
        }, "airplay-server-log-" + target.pid());
        thread.setDaemon(true);
        thread.start();
    }

    private void handleProcessExit(Process exited, long processGeneration) {
        Snapshot next;
        synchronized (monitor) {
            if (process != exited || generation != processGeneration) {
                return;
            }
            int exitCode = exited.exitValue();
            boolean normal = expectedStop || exitCode == 0;
            process = null;
            controlSession = null;
            next = normal
                    ? Snapshot.stopped()
                    : new Snapshot(State.FAILED, 0, false, false, false, null,
                            Detail.ABNORMAL_EXIT, Integer.toString(exitCode));
            snapshot = next;
        }
        logMessage(LogType.PROCESS_EXITED, exited.exitValue());
        publishSnapshotLater(next);
    }

    private void configureEnvironment(ProcessBuilder builder) {
        Path javaBin = javaExecutable.getParent();
        Path javaHome = javaBin.getParent();
        Path gstreamer = baseDirectory.resolve("gstreamer");
        Path gstreamerBin = gstreamer.resolve("bin");
        String originalPath = builder.environment().getOrDefault("PATH", "");
        builder.environment().put("PATH",
                javaBin + File.pathSeparator + gstreamerBin + File.pathSeparator + originalPath);
        builder.environment().put("JAVA_HOME", javaHome.toString());
        builder.environment().put("GSTREAMER_PATH", gstreamer.toString());
        builder.environment().put("GST_PLUGIN_PATH", gstreamer.resolve("lib/gstreamer-1.0").toString());
    }

    private static int findAvailablePort(int excludedPort) throws IOException {
        int port;
        do {
            port = bindAvailablePort();
        } while (port == excludedPort);
        return port;
    }

    private static int bindAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            return socket.getLocalPort();
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static void requireFile(Path path, LauncherMessages.Key messageKey) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new LauncherIOException(messageKey, path);
        }
    }

    private static boolean waitFor(Process process, long timeoutMillis) {
        try {
            return process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void rememberDescendants(Process target, Map<Long, ProcessHandle> descendants) {
        target.descendants().forEach(process -> descendants.put(process.pid(), process));
    }

    private static void terminateDescendants(
            Map<Long, ProcessHandle> descendants,
            boolean forcibly) {
        List<ProcessHandle> ordered = new ArrayList<>(descendants.values());
        ordered.sort(Comparator.comparingInt(ServerProcessManager::processDepth).reversed());
        for (ProcessHandle descendant : ordered) {
            if (descendant.isAlive()) {
                if (forcibly) {
                    descendant.destroyForcibly();
                } else {
                    descendant.destroy();
                }
            }
        }
    }

    private static void terminateProcessTreeForcibly(Process target) {
        Map<Long, ProcessHandle> descendants = new LinkedHashMap<>();
        rememberDescendants(target, descendants);
        terminateDescendants(descendants, true);
        if (target.isAlive()) {
            target.destroyForcibly();
            waitFor(target, 500);
        }
    }

    private static int processDepth(ProcessHandle process) {
        int depth = 0;
        ProcessHandle current = process;
        while (current.parent().isPresent() && depth < 64) {
            current = current.parent().orElseThrow();
            depth++;
        }
        return depth;
    }

    private void publishSnapshot() {
        Snapshot current = snapshot();
        publishSnapshotLater(current);
    }

    private void publishSnapshotLater(Snapshot current) {
        try {
            stateListener.accept(current);
        } catch (RuntimeException exception) {
            logMessage(LogType.LISTENER_FAILED, exception);
        }
    }

    private void logRaw(String message) {
        publishLog(LogEntry.raw(message));
    }

    private void logMessage(LogType type, Object... arguments) {
        publishLog(LogEntry.message(type, arguments));
    }

    private void publishLog(LogEntry entry) {
        try {
            logListener.accept(entry);
        } catch (RuntimeException ignored) {
            // A failed view must not take down the managed service.
        }
    }

    private static ThreadFactory namedDaemonFactory(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    @Override
    public void close() {
        Process target;
        synchronized (monitor) {
            closed = true;
            target = process;
        }
        statusExecutor.shutdownNow();
        lifecycleExecutor.shutdownNow();
        if (target != null && target.isAlive()) {
            terminateProcessTreeForcibly(target);
        }
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
