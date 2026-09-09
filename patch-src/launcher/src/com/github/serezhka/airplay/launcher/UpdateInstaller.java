package com.github.serezhka.airplay.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;

final class UpdateInstaller {
    static final String WORK_DIRECTORY = ".airplay-update";
    static final String HELPER_RESOURCE = "/updater/AirPlayUpdater.exe";
    private final Path baseDirectory;

    UpdateInstaller(Path baseDirectory) {
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    static boolean supported() {
        String architecture = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return System.getProperty("os.name", "").startsWith("Windows")
                && (architecture.equals("amd64") || architecture.equals("x86_64"))
                && UpdateInstaller.class.getResource(HELPER_RESOURCE) != null;
    }

    static void validateResources() throws IOException {
        try (InputStream protocol = UpdateInstaller.class.getResourceAsStream("/" + UpdateArchive.PROTOCOL_RESOURCE);
             InputStream helper = UpdateInstaller.class.getResourceAsStream(HELPER_RESOURCE)) {
            if (protocol == null || helper == null
                    || !UpdateArchive.PROTOCOL.equals(new String(protocol.readNBytes(16), StandardCharsets.UTF_8).strip())
                    || helper.read() != 'M' || helper.read() != 'Z') {
                throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_UNSUPPORTED);
            }
        }
    }

    Prepared prepare(GitHubUpdateChecker.Result release, UpdateDownloader.Cancellation cancellation,
                     Consumer<UpdateDownloader.Progress> progress) throws IOException {
        if (!supported()) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_UNSUPPORTED);
        }
        if (!release.automaticUpdateAvailable()) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_PACKAGE);
        }
        UpdateArchive.requireOrdinaryPath(baseDirectory);
        checkLaunchAllowed(baseDirectory, null);
        UpdateArchive.requireFreeSpace(baseDirectory, release.asset().size());
        Path work = baseDirectory.resolve(WORK_DIRECTORY);
        Files.createDirectories(work);
        UpdateArchive.requireOrdinaryPath(work);
        Path job = Files.createDirectory(work.resolve(UUID.randomUUID().toString()));
        Prepared prepared = new Prepared(baseDirectory, job, release.latestVersion());
        boolean success = false;
        try {
            Path archive = job.resolve("download.zip");
            new UpdateDownloader().download(release.asset(), archive, cancellation, progress);
            progress.accept(new UpdateDownloader.Progress(true, 0, 0));
            UpdateArchive.extract(archive, job.resolve("payload"), release.latestVersion(), cancellation);
            try (InputStream helper = UpdateInstaller.class.getResourceAsStream(HELPER_RESOURCE)) {
                if (helper == null) {
                    throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_UNSUPPORTED);
                }
                Files.copy(helper, job.resolve("AirPlayUpdater.exe"));
            }
            Files.delete(archive);
            cancellation.check();
            success = true;
            return prepared;
        } finally {
            if (!success) {
                prepared.close();
            }
        }
    }

    static Pending launchHelper(Prepared prepared, UiLanguage language, boolean resumeService) throws IOException {
        requireOwnedJob(prepared.baseDirectory, prepared.jobDirectory);
        UpdateArchive.validatePayload(prepared.jobDirectory.resolve("payload"), prepared.version);
        String token = UUID.randomUUID().toString();
        ProcessHandle parent = ProcessHandle.current();
        Properties metadata = new Properties();
        metadata.setProperty("base", prepared.baseDirectory.toString());
        metadata.setProperty("token", token);
        metadata.setProperty("version", prepared.version.toString());
        metadata.setProperty("parentPid", Long.toString(parent.pid()));
        metadata.setProperty("parentStarted", Long.toString(parent.info().startInstant()
                .orElseThrow(() -> new IOException("Cannot identify the launcher process")).toEpochMilli()));
        metadata.setProperty("resumeService", Boolean.toString(resumeService));
        metadata.setProperty("language", language.name());
        try (OutputStream output = Files.newOutputStream(prepared.jobDirectory.resolve("job.xml"),
                StandardOpenOption.CREATE_NEW)) {
            metadata.storeToXML(output, null, StandardCharsets.UTF_8);
        }
        Process helper = new ProcessBuilder(prepared.jobDirectory.resolve("AirPlayUpdater.exe").toString())
                .directory(prepared.jobDirectory.toFile())
                .redirectErrorStream(true)
                .redirectOutput(prepared.jobDirectory.resolve("helper-output.log").toFile())
                .start();
        prepared.handedOff = true;
        Pending pending = new Pending(prepared, token, helper);
        try {
            long deadline = System.nanoTime() + 30_000_000_000L;
            while (System.nanoTime() < deadline) {
                if (matchesToken(prepared.jobDirectory.resolve("ready"), token) && helper.isAlive()) {
                    return pending;
                }
                if (!helper.isAlive()) {
                    throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_HELPER,
                            prepared.jobDirectory.resolve("update.log"));
                }
                sleep();
            }
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_HELPER,
                    prepared.jobDirectory.resolve("update.log"));
        } catch (IOException exception) {
            try {
                pending.cancel();
            } catch (IOException cancelFailure) {
                exception.addSuppressed(cancelFailure);
            }
            throw exception;
        }
    }

    static Startup startup(Path base, String[] arguments) throws IOException {
        String identifier = argument(arguments, "--update-job");
        String token = argument(arguments, "--update-token");
        if (identifier == null && token == null) {
            checkLaunchAllowed(base, null);
            return null;
        }
        if (!isIdentifier(identifier) || !isIdentifier(token)) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_STARTUP);
        }
        Path job = base.resolve(WORK_DIRECTORY).resolve(identifier);
        requireOwnedJob(base, job);
        Properties metadata = new Properties();
        try (InputStream input = Files.newInputStream(job.resolve("job.xml"))) {
            metadata.loadFromXML(input);
        }
        if (!token.equals(metadata.getProperty("token"))
                || !base.equals(Path.of(metadata.getProperty("base", "")).toAbsolutePath().normalize())
                || !AppVersion.current().toString().replaceFirst("^[vV]", "")
                .equals(metadata.getProperty("version", "").replaceFirst("^[vV]", ""))
                || !matchesToken(job.resolve("ready"), token)) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_STARTUP);
        }
        return new Startup(job, token);
    }

    static void acknowledgeStartup(Startup startup) throws IOException {
        if (startup == null) {
            return;
        }
        writeToken(startup.jobDirectory().resolve("started"), startup.token());
        long deadline = System.nanoTime() + 60_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (matchesToken(startup.jobDirectory().resolve("commit"), startup.token())) {
                return;
            }
            if (Files.exists(startup.jobDirectory().resolve("error.txt"))) {
                break;
            }
            sleep();
        }
        throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_STARTUP);
    }

    static void checkLaunchAllowed(Path base, Startup startup) throws IOException {
        if (startup != null) {
            return;
        }
        Path lockPath = base.resolve(WORK_DIRECTORY).resolve("update.lock");
        if (!Files.exists(lockPath)) {
            return;
        }
        UpdateArchive.requireOrdinaryPath(lockPath);
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) {
                throw new IOException("Update lock is held");
            }
        } catch (IOException | OverlappingFileLockException exception) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_BUSY);
        }
    }

    static void requireOwnedJob(Path base, Path job) throws IOException {
        Path absoluteBase = base.toAbsolutePath().normalize();
        Path absoluteJob = job.toAbsolutePath().normalize();
        if (!absoluteJob.getParent().equals(absoluteBase.resolve(WORK_DIRECTORY))
                || !isIdentifier(absoluteJob.getFileName().toString())) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_PACKAGE);
        }
        UpdateArchive.requireOrdinaryPath(absoluteJob);
    }

    private static boolean isIdentifier(String text) {
        return text != null && text.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private static String argument(String[] arguments, String name) throws IOException {
        String value = null;
        for (int index = 0; index < arguments.length; index++) {
            if (arguments[index].equals(name)) {
                if (value != null || ++index == arguments.length) {
                    throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_STARTUP);
                }
                value = arguments[index];
            }
        }
        return value;
    }

    private static void sleep() throws IOException {
        try {
            Thread.sleep(100);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Update interrupted", exception);
        }
    }

    private static boolean matchesToken(Path marker, String token) throws IOException {
        if (!Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        UpdateArchive.requireOrdinaryPath(marker);
        try (InputStream input = Files.newInputStream(marker)) {
            return token.equals(new String(input.readNBytes(64), StandardCharsets.UTF_8));
        }
    }

    private static void writeToken(Path marker, String token) throws IOException {
        UpdateArchive.requireOrdinaryPath(marker.getParent());
        Path temporary = marker.resolveSibling(marker.getFileName() + ".tmp");
        Files.writeString(temporary, token, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        Files.move(temporary, marker, StandardCopyOption.ATOMIC_MOVE);
    }

    record Startup(Path jobDirectory, String token) {
    }

    static final class Prepared implements AutoCloseable {
        private final Path baseDirectory;
        private final Path jobDirectory;
        private final AppVersion version;
        private boolean handedOff;

        Prepared(Path baseDirectory, Path jobDirectory, AppVersion version) {
            this.baseDirectory = baseDirectory;
            this.jobDirectory = jobDirectory;
            this.version = version;
        }

        AppVersion version() {
            return version;
        }

        @Override
        public void close() throws IOException {
            if (handedOff || !Files.exists(jobDirectory, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            requireOwnedJob(baseDirectory, jobDirectory);
            Path resolved = jobDirectory.toRealPath();
            Path expected = baseDirectory.toRealPath().resolve(WORK_DIRECTORY).resolve(jobDirectory.getFileName());
            if (!resolved.equals(expected)) {
                throw new IOException("Refusing to clean an update workspace outside the installation");
            }
            Files.walkFileTree(resolved, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    UpdateArchive.requireOrdinaryPath(directory);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                    if (failure != null) {
                        throw failure;
                    }
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    record Pending(Prepared prepared, String token, Process helper) {
        void applyAfterExit() throws IOException {
            if (!helper.isAlive() || !matchesToken(prepared.jobDirectory.resolve("ready"), token)) {
                throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_HELPER,
                        prepared.jobDirectory.resolve("update.log"));
            }
            writeToken(prepared.jobDirectory.resolve("apply"), token);
        }

        void cancel() throws IOException {
            writeToken(prepared.jobDirectory.resolve("cancel"), token);
        }
    }
}
