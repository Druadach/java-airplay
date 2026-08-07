package com.github.serezhka.airplay.app.control;

import com.github.serezhka.airplay.app.lifecycle.ApplicationShutdown;
import com.github.serezhka.airplay.player.gstreamer.FullscreenController;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

public final class LocalControlServer implements SmartLifecycle, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(LocalControlServer.class);
    private static final int CLIENT_TIMEOUT_MILLIS = 500;
    private static final int MAX_REQUEST_BYTES = 512;

    private final int configuredPort;
    private final byte[] expectedToken;
    private final FullscreenController fullscreenController;
    private final ApplicationShutdown applicationShutdown;
    private final Runnable testQuitAction;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean quitStarted = new AtomicBoolean();

    private volatile ServerSocket serverSocket;
    private volatile Socket activeClient;
    private volatile Thread acceptThread;

    public LocalControlServer(
            int port,
            String token,
            ApplicationShutdown applicationShutdown,
            AirPlayConsumer airPlayConsumer) {
        this(port, token, applicationShutdown,
                airPlayConsumer instanceof FullscreenController controller ? controller : null,
                null);
    }

    LocalControlServer(
            int port,
            String token,
            FullscreenController fullscreenController,
            Runnable quitAction) {
        this(port, token, null, fullscreenController,
                Objects.requireNonNull(quitAction, "quitAction"));
    }

    private LocalControlServer(
            int port,
            String token,
            ApplicationShutdown applicationShutdown,
            FullscreenController fullscreenController,
            Runnable testQuitAction) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (token == null || token.isEmpty() || token.length() > 256
                || token.chars().anyMatch(character -> character < 0x21 || character > 0x7e)) {
            throw new IllegalArgumentException(
                    "A printable ASCII control token of at most 256 characters is required");
        }
        this.configuredPort = port;
        this.expectedToken = token.getBytes(StandardCharsets.UTF_8);
        this.applicationShutdown = applicationShutdown;
        this.fullscreenController = fullscreenController;
        this.testQuitAction = testQuitAction;
    }

    @Override
    public synchronized void start() {
        if (running.get()) {
            return;
        }

        ServerSocket socket = null;
        try {
            socket = new ServerSocket();
            socket.setReuseAddress(false);
            socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), configuredPort), 8);
        } catch (IOException exception) {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException closeException) {
                    exception.addSuppressed(closeException);
                }
            }
            throw new IllegalStateException("Unable to start local launcher control", exception);
        }

        serverSocket = socket;
        running.set(true);
        ServerSocket listener = socket;
        acceptThread = new Thread(() -> acceptConnections(listener), "airplay-local-control");
        acceptThread.setDaemon(true);
        acceptThread.start();
        log.info("Local launcher control is listening on 127.0.0.1:{}", socket.getLocalPort());
    }

    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? configuredPort : socket.getLocalPort();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void stop() {
        close();
    }

    @Override
    public void stop(Runnable callback) {
        try {
            close();
        } finally {
            callback.run();
        }
    }

    private void acceptConnections(ServerSocket listener) {
        while (running.get() && serverSocket == listener) {
            try {
                Socket client = listener.accept();
                if (!registerClient(listener, client)) {
                    client.close();
                    continue;
                }
                try {
                    handleClient(client);
                } finally {
                    clearClient(client);
                }
            } catch (IOException exception) {
                if (running.get() && serverSocket == listener) {
                    log.warn("Local launcher control connection failed", exception);
                }
            } catch (RuntimeException exception) {
                if (running.get() && serverSocket == listener) {
                    log.warn("Invalid local launcher control request", exception);
                }
            }
        }
    }

    private synchronized boolean registerClient(ServerSocket listener, Socket client) {
        if (!running.get() || serverSocket != listener) {
            return false;
        }
        activeClient = client;
        return true;
    }

    private synchronized void clearClient(Socket client) {
        if (activeClient == client) {
            activeClient = null;
        }
    }

    private void handleClient(Socket client) throws IOException {
        boolean quitRequested = false;
        try (client;
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                     client.getOutputStream(), StandardCharsets.UTF_8))) {
            CommandResult result;
            try {
                result = execute(readRequest(client));
            } catch (RequestRejectedException exception) {
                result = CommandResult.response("ERR\tCOMMAND");
            }
            writer.write(result.response());
            writer.newLine();
            writer.flush();
            quitRequested = result.quitRequested();
        }

        if (quitRequested) {
            requestQuit();
        }
    }

    private String readRequest(Socket client) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CLIENT_TIMEOUT_MILLIS);
        InputStream input = client.getInputStream();
        StringBuilder request = new StringBuilder();
        while (true) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                throw new SocketTimeoutException("Local control request timed out");
            }
            long remainingMillis = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            client.setSoTimeout((int) Math.max(1, Math.min(CLIENT_TIMEOUT_MILLIS, remainingMillis + 1)));

            int next = input.read();
            if (next < 0 || next == '\n') {
                return next < 0 && request.isEmpty() ? null : request.toString();
            }
            if (next == '\r') {
                continue;
            }
            if (next > 0x7f || request.length() >= MAX_REQUEST_BYTES) {
                throw new RequestRejectedException();
            }
            request.append((char) next);
        }
    }

    private CommandResult execute(String request) {
        if (request == null) {
            return CommandResult.response("ERR\tCOMMAND");
        }

        String[] fields = request.split("\\t", -1);
        if (fields.length < 2 || !tokenMatches(fields[0])) {
            return CommandResult.response("ERR\tAUTH");
        }

        return switch (fields[1]) {
            case "STATUS" -> fields.length == 2
                    ? CommandResult.response(statusResponse())
                    : CommandResult.response("ERR\tCOMMAND");
            case "FULLSCREEN" -> setFullscreen(fields);
            case "QUIT" -> fields.length == 2
                    ? new CommandResult("OK\tQUIT", true)
                    : CommandResult.response("ERR\tCOMMAND");
            default -> CommandResult.response("ERR\tCOMMAND");
        };
    }

    private String statusResponse() {
        boolean available = fullscreenController != null;
        boolean fullscreen = available && fullscreenController.isFullscreen();
        return "OK\tSTATUS\tRUNNING\ttrue\tFULLSCREEN_AVAILABLE\t" + available
                + "\tFULLSCREEN\t" + fullscreen;
    }

    private CommandResult setFullscreen(String[] fields) {
        if (fields.length != 3) {
            return CommandResult.response("ERR\tCOMMAND");
        }
        if (fullscreenController == null) {
            return CommandResult.response("ERR\tUNAVAILABLE");
        }
        if (!"true".equals(fields[2]) && !"false".equals(fields[2])) {
            return CommandResult.response("ERR\tVALUE");
        }

        boolean fullscreen = Boolean.parseBoolean(fields[2]);
        fullscreenController.setFullscreen(fullscreen);
        return CommandResult.response("OK\tFULLSCREEN\t" + fullscreenController.isFullscreen());
    }

    private boolean tokenMatches(String token) {
        return MessageDigest.isEqual(expectedToken, token.getBytes(StandardCharsets.UTF_8));
    }

    private void requestQuit() {
        if (testQuitAction != null) {
            if (quitStarted.compareAndSet(false, true)) {
                close();
                testQuitAction.run();
            }
            return;
        }

        applicationShutdown.request(this::close);
    }

    @Override
    public synchronized void close() {
        if (!running.getAndSet(false)) {
            return;
        }

        ServerSocket socket = serverSocket;
        Socket client = activeClient;
        serverSocket = null;
        activeClient = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException exception) {
                log.debug("Unable to close local launcher control socket", exception);
            }
        }
        if (client != null) {
            try {
                client.close();
            } catch (IOException exception) {
                log.debug("Unable to close active local launcher control client", exception);
            }
        }
    }

    private record CommandResult(String response, boolean quitRequested) {
        private static CommandResult response(String response) {
            return new CommandResult(response, false);
        }
    }

    private static final class RequestRejectedException extends IOException {
    }
}
