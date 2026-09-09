package com.github.serezhka.airplay.launcher;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class AutomaticUpdateTest {
    private static final String TAG = "v1.2.3";
    private static final String NAME = "AirPlayReceiver_Portable_1.2.3.zip";
    private static final String URL = "https://github.com/Druadach/java-airplay/releases/download/" + TAG + "/" + NAME;
    private static final String DIGEST = "a".repeat(64);
    private static final AppVersion VERSION = AppVersion.parse(TAG);

    static void runAll() throws Exception {
        UpdateInstaller.validateResources();
        onlyTrustedCompleteAssetsAreSelected();
        downloadsAreBoundedVerifiedAndCancellable();
        downloadRedirectsAndTimeoutsAreSafe();
        stagingNeverOverwritesSettings();
        unsafeArchivesAreRejected();
        versionsAndProtocolsMustMatch();
        workspaceCleanupIsScoped();
        concurrentUpdateLaunchIsRejected();
        nativeHelperCanBeCancelledBeforeExit();
        System.out.println("Automatic update preparation tests passed");
    }

    private static void onlyTrustedCompleteAssetsAreSelected() throws Exception {
        String valid = assetJson();
        GitHubUpdateChecker.Result result = release(valid);
        equal(true, result.automaticUpdateAvailable(), "verified portable package is available");
        equal(URI.create(URL), result.asset().downloadUri(), "trusted package URI");
        equal(DIGEST, result.asset().sha256(), "GitHub SHA-256 digest");
        expect(LauncherMessages.Key.ERROR_UPDATE_RESPONSE, () -> release(valid.replace(
                "\"digest\":", "\"digest\":null,\"digest\":")));
        for (String invalid : List.of(valid.replace(NAME, "Other.zip"),
                valid.replace("sha256:" + DIGEST, ""), valid.replace("sha256:", "md5:"),
                valid.replace("\"uploaded\"", "\"new\""), valid.replace("\"size\":100", "\"size\":0"),
                valid.replace("\"size\":100", "\"size\":1e99"),
                valid.replace(URL, URL.replace("Druadach/", "somebody-else/")),
                valid.replace(URL, "http://github.com/Druadach/java-airplay/update.zip"),
                valid.replace(URL, URL + "?redirect=evil"), "{\"name\":\"" + NAME + "\"}",
                valid + "," + valid)) {
            GitHubUpdateChecker.Result manual = release(invalid);
            equal(true, manual.updateAvailable(), "manual update remains available");
            equal(false, manual.automaticUpdateAvailable(), "unsafe or ambiguous asset is not auto-installed");
        }
        equal(false, GitHubUpdateChecker.parseResponse("{\"tag_name\":\"v1.2.3\",\"draft\":false,"
                + "\"prerelease\":false,\"assets\":null}", AppVersion.parse("1.2.2")).automaticUpdateAvailable(),
                "missing assets keep manual checks usable");
        for (String location : List.of("http://github.com/file", "https://github.com.evil.example/file",
                "https://user@github.com/file", "https://github.com:444/file", "https:/broken",
                "https://release-assets.githubusercontent.com/file#fragment", "file:///C:/Windows/test")) {
            equal(false, UpdateDownloader.isTrustedDownloadUri(URI.create(location)), "untrusted redirect " + location);
        }
        equal(true, UpdateDownloader.isTrustedDownloadUri(URI.create(
                "https://release-assets.githubusercontent.com/github-production-release-asset/file?token=test")),
                "signed GitHub CDN redirect is allowed");
    }

    private static void downloadsAreBoundedVerifiedAndCancellable() throws Exception {
        byte[] bytes = "verified update bytes".getBytes(StandardCharsets.UTF_8);
        UpdateAsset asset = asset(bytes, bytes.length);
        try (Fixture fixture = new Fixture(); DownloadServer server = new DownloadServer(exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        })) {
            List<UpdateDownloader.Progress> progress = new ArrayList<>();
            Path destination = fixture.root.resolve("good.zip");
            new UpdateDownloader().downloadFromLoopback(asset, server.uri(), destination,
                    new UpdateDownloader.Cancellation(), progress::add);
            equal(HexFormat.of().formatHex(bytes), HexFormat.of().formatHex(Files.readAllBytes(destination)),
                    "download bytes preserved");
            equal((long) bytes.length, progress.get(progress.size() - 1).completed(), "progress reaches total");
            expect(LauncherMessages.Key.ERROR_UPDATE_CHECKSUM, () -> new UpdateDownloader().downloadFromLoopback(
                    new UpdateAsset(NAME, bytes.length, DIGEST, URI.create(URL)), server.uri(),
                    fixture.root.resolve("checksum.zip"), new UpdateDownloader.Cancellation(), ignored -> { }));
            for (int difference : List.of(-1, 1)) {
                expect(LauncherMessages.Key.ERROR_UPDATE_PACKAGE, () -> new UpdateDownloader().downloadFromLoopback(
                        asset(bytes, bytes.length + difference), server.uri(), fixture.root.resolve("size" + difference),
                        new UpdateDownloader.Cancellation(), ignored -> { }));
            }
            UpdateDownloader.Cancellation cancellation = new UpdateDownloader.Cancellation();
            try {
                new UpdateDownloader().downloadFromLoopback(asset, server.uri(), fixture.root.resolve("cancel.zip"),
                        cancellation, ignored -> cancellation.cancel());
                throw new AssertionError("Cancelled download was accepted");
            } catch (CancellationException expected) {
                equal(true, cancellation.isCancelled(), "cancellation observed");
            }
        }
    }

    private static void downloadRedirectsAndTimeoutsAreSafe() throws Exception {
        try (Fixture fixture = new Fixture(); DownloadServer redirect = new DownloadServer(exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:9/private");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        })) {
            expect(LauncherMessages.Key.ERROR_UPDATE_PACKAGE, () -> new UpdateDownloader().downloadFromLoopback(
                    asset(new byte[]{1}, 1), redirect.uri(), fixture.root.resolve("redirect.zip"),
                    new UpdateDownloader.Cancellation(), ignored -> { }));
        }
        try (Fixture fixture = new Fixture(); DownloadServer delayed = new DownloadServer(exchange -> {
            try {
                Thread.sleep(300);
                exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        })) {
            expect(LauncherMessages.Key.ERROR_UPDATE_DOWNLOAD, () -> new UpdateDownloader(200, 50).downloadFromLoopback(
                    asset(new byte[]{1}, 1), delayed.uri(), fixture.root.resolve("timeout.zip"),
                    new UpdateDownloader.Cancellation(), ignored -> { }));
        }
    }

    private static void stagingNeverOverwritesSettings() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path configuration = fixture.root.resolve("application.properties");
            Files.writeString(configuration, "server.name=我的 PC\ncustom.setting=keep\n", StandardCharsets.UTF_8);
            byte[] before = Files.readAllBytes(configuration);
            Path payload = fixture.root.resolve("payload");
            UpdateArchive.extract(archive(fixture.root, packageFiles("1.2.3", "1")), payload, VERSION,
                    new UpdateDownloader.Cancellation());
            equal(false, Files.exists(payload.resolve("application.properties")), "configuration excluded from payload");
            equal(false, Files.exists(payload.resolve("README.md")), "unmanaged documentation is not replaced");
            equal(HexFormat.of().formatHex(before), HexFormat.of().formatHex(Files.readAllBytes(configuration)),
                    "user settings remain byte-for-byte unchanged");
            for (String name : UpdateArchive.MANAGED_NAMES) {
                equal(true, Files.exists(payload.resolve(name)), "managed payload " + name);
            }
        }
    }

    private static void unsafeArchivesAreRejected() throws Exception {
        for (String unsafe : List.of("../outside.txt", "/absolute/file", "C:/outside/file", "root/../outside",
                "root/jre/../../outside", "root/jre/back\\slash", "root/jre/stream:evil", "root/jre/NUL.txt",
                "root/jre/COM1", "root/jre/LPT¹.txt", "root/jre/trailing.", "root/jre/trailing ",
                "root//empty", "root/jre/control\u0001", "root/jre/" + "long".repeat(60))) {
            try (Fixture fixture = new Fixture()) {
                Map<String, byte[]> files = packageFiles("1.2.3", "1");
                files.put(unsafe, new byte[]{1});
                expect(LauncherMessages.Key.ERROR_UPDATE_PACKAGE, () -> UpdateArchive.extract(
                        archive(fixture.root, files), fixture.root.resolve("payload"), VERSION,
                        new UpdateDownloader.Cancellation()));
            }
        }
        for (String collision : List.of("AirPlay接收器/JRE/bin/java.exe", "AirPlay接收器/jre",
                "AirPlay接收器/jre/bin/java.exe/child")) {
            try (Fixture fixture = new Fixture()) {
                Map<String, byte[]> files = packageFiles("1.2.3", "1");
                files.put(collision, new byte[]{1});
                expect(LauncherMessages.Key.ERROR_UPDATE_PACKAGE, () -> UpdateArchive.extract(
                        archive(fixture.root, files), fixture.root.resolve("payload"), VERSION,
                        new UpdateDownloader.Cancellation()));
            }
        }
    }

    private static void versionsAndProtocolsMustMatch() throws Exception {
        for (List<String> values : List.of(List.of("9.9.9", "1"), List.of("1.2.3", "2"), List.of("1.2.3", ""))) {
            try (Fixture fixture = new Fixture()) {
                expect(LauncherMessages.Key.ERROR_UPDATE_PACKAGE, () -> UpdateArchive.extract(
                        archive(fixture.root, packageFiles(values.get(0), values.get(1))), fixture.root.resolve("payload"),
                        VERSION, new UpdateDownloader.Cancellation()));
            }
        }
        try (Fixture fixture = new Fixture()) {
            Map<String, byte[]> missing = packageFiles("1.2.3", "1");
            missing.remove("AirPlay接收器/jre/bin/javaw.exe");
            expect(LauncherMessages.Key.ERROR_UPDATE_PACKAGE, () -> UpdateArchive.extract(
                    archive(fixture.root, missing), fixture.root.resolve("payload"), VERSION,
                    new UpdateDownloader.Cancellation()));
        }
    }

    private static void workspaceCleanupIsScoped() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path unrelated = Files.createDirectory(fixture.root.resolve("keep"));
            Path settings = Files.writeString(unrelated.resolve("config.txt"), "keep me");
            UpdateInstaller.Prepared invalid = new UpdateInstaller.Prepared(fixture.root, unrelated, VERSION);
            expect(LauncherMessages.Key.ERROR_UPDATE_PACKAGE, invalid::close);
            equal("keep me", Files.readString(settings), "cleanup never touches unowned paths");
            Path job = fixture.root.resolve(UpdateInstaller.WORK_DIRECTORY).resolve(UUID.randomUUID().toString());
            Files.createDirectories(job.resolve("payload/subdir"));
            Files.writeString(job.resolve("payload/subdir/file"), "temporary");
            new UpdateInstaller.Prepared(fixture.root, job, VERSION).close();
            equal(false, Files.exists(job), "owned cancelled update cleaned");
            equal(true, Files.exists(settings), "neighbouring user files preserved");
            expect(LauncherMessages.Key.ERROR_UPDATE_STARTUP, () -> UpdateInstaller.startup(fixture.root,
                    new String[]{"--update-job", "../../outside", "--update-token", UUID.randomUUID().toString()}));
        }
    }

    private static void concurrentUpdateLaunchIsRejected() throws Exception {
        try (Fixture fixture = new Fixture()) {
            Path work = Files.createDirectory(fixture.root.resolve(UpdateInstaller.WORK_DIRECTORY));
            Path lock = work.resolve("update.lock");
            try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                 var held = channel.lock()) {
                expect(LauncherMessages.Key.ERROR_UPDATE_BUSY, () -> UpdateInstaller.checkLaunchAllowed(fixture.root, null));
            }
            UpdateInstaller.checkLaunchAllowed(fixture.root, null);
        }
    }

    private static String assetJson() {
        return "{\"name\":\"" + NAME + "\",\"size\":100,\"state\":\"uploaded\",\"digest\":\"sha256:"
                + DIGEST + "\",\"browser_download_url\":\"" + URL + "\"}";
    }

    private static void nativeHelperCanBeCancelledBeforeExit() throws Exception {
        if (!UpdateInstaller.supported()) {
            return;
        }
        try (Fixture fixture = new Fixture()) {
            Path job = fixture.root.resolve(UpdateInstaller.WORK_DIRECTORY).resolve(UUID.randomUUID().toString());
            Files.createDirectories(job);
            Path settings = Files.writeString(fixture.root.resolve("application.properties"), "server.name=keep");
            UpdateArchive.extract(archive(fixture.root, packageFiles("1.2.3", "1")), job.resolve("payload"), VERSION,
                    new UpdateDownloader.Cancellation());
            try (var resource = UpdateInstaller.class.getResourceAsStream(UpdateInstaller.HELPER_RESOURCE)) {
                Files.copy(resource, job.resolve("AirPlayUpdater.exe"));
            }
            UpdateInstaller.Pending pending = UpdateInstaller.launchHelper(
                    new UpdateInstaller.Prepared(fixture.root, job, VERSION), UiLanguage.EN_US, false);
            try {
                equal(true, pending.helper().isAlive(), "independent native helper is ready");
                expect(LauncherMessages.Key.ERROR_UPDATE_BUSY, () -> UpdateInstaller.checkLaunchAllowed(fixture.root, null));
            } finally {
                pending.cancel();
                if (!pending.helper().waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    pending.helper().destroyForcibly();
                    pending.helper().waitFor();
                    throw new AssertionError("The native helper did not acknowledge cancellation");
                }
            }
            equal(0, pending.helper().exitValue(), "native cancellation exit status");
            equal("server.name=keep", Files.readString(settings), "native cancellation preserves settings");
            equal(false, Files.exists(job.resolve("backup")), "native helper never applied before parent exit");
            UpdateInstaller.checkLaunchAllowed(fixture.root, null);
        }
    }

    private static GitHubUpdateChecker.Result release(String assets) throws IOException {
        return GitHubUpdateChecker.parseResponse("{\"tag_name\":\"" + TAG
                + "\",\"draft\":false,\"prerelease\":false,\"assets\":[" + assets + "]}", AppVersion.parse("1.2.2"));
    }

    private static UpdateAsset asset(byte[] bytes, long size) throws Exception {
        return new UpdateAsset(NAME, size, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                URI.create(URL));
    }

    private static Map<String, byte[]> packageFiles(String version, String protocol) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("AirPlay接收器/jre/bin/java.exe", new byte[]{1});
        files.put("AirPlay接收器/jre/bin/javaw.exe", new byte[]{1});
        files.put("AirPlay接收器/gstreamer/bin/runtime.dll", new byte[]{1});
        Map<String, byte[]> resources = new LinkedHashMap<>();
        resources.put("airplay-version.txt", version.getBytes(StandardCharsets.UTF_8));
        if (!protocol.isEmpty()) {
            resources.put(UpdateArchive.PROTOCOL_RESOURCE, protocol.getBytes(StandardCharsets.UTF_8));
        }
        resources.put("com/github/serezhka/airplay/launcher/AirPlayLauncher.class", new byte[]{1});
        resources.put("updater/AirPlayUpdater.exe", new byte[]{'M', 'Z'});
        files.put("AirPlay接收器/java-airplay-launcher.jar", jar(resources));
        files.put("AirPlay接收器/java-airplay-server-fixed.jar", jar(Map.of()));
        for (String name : List.of("AirPlayReceiver.exe", "run_airplay_gui.bat", "run_airplay_server.bat",
                "application.properties", "README.md")) {
            files.put("AirPlay接收器/" + name, ("new " + name).getBytes(StandardCharsets.UTF_8));
        }
        return files;
    }

    private static byte[] jar(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream output = new JarOutputStream(bytes, manifest)) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static Path archive(Path directory, Map<String, byte[]> entries) throws IOException {
        Path archive = directory.resolve("package.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (var entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return archive;
    }

    private static void expect(LauncherMessages.Key key, CheckedAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected " + key);
        } catch (LauncherIOException failure) {
            equal(key, failure.messageKey(), "automatic update error");
        }
    }

    private static void equal(Object expected, Object actual, String message) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    private static final class DownloadServer implements AutoCloseable {
        private final HttpServer server;

        DownloadServer(HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/package", handler);
            server.start();
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/package");
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final Path root = Files.createTempDirectory("ap-update-test-").toAbsolutePath().normalize();

        Fixture() throws IOException {
        }

        @Override
        public void close() throws IOException {
            Path temporary = Path.of(System.getProperty("java.io.tmpdir")).toRealPath();
            if (!root.toRealPath().startsWith(temporary) || !root.getFileName().toString().startsWith("ap-update-test-")) {
                throw new IOException("Refusing to clean an unexpected test directory");
            }
            try (var entries = Files.walk(root)) {
                for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(entry);
                }
            }
        }
    }
}
