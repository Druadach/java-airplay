package com.github.serezhka.airplay.launcher;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class LauncherCoreTest {
    private LauncherCoreTest() {
    }

    public static void main(String[] arguments) throws Exception {
        languagesAndMessagesAreComplete();
        statusTextSwitchesLanguageImmediately();
        trayLabelsSwitchLanguageImmediately();
        editableNumericFieldsAcceptCustomValues();
        settingsValidationRejectsInvalidValues();
        configStorePreservesExternalContent();
        guiSavePreservesHiddenPort();
        languageLoadsBeforeInvalidSettings();
        controlClientUsesAuthenticatedLoopbackProtocol();
        System.out.println("Launcher core tests passed");
    }

    private static void settingsValidationRejectsInvalidValues() {
        expectFailure(() -> new LauncherSettings(
                "", 5001, 1920, 1080, 60, "gstreamer", false, UiLanguage.ZH_CN));
        expectFailure(() -> new LauncherSettings(
                "AirPlay", 0, 1920, 1080, 60, "gstreamer", false, UiLanguage.ZH_CN));
        expectFailure(() -> new LauncherSettings(
                "AirPlay", 5001, 10, 1080, 60, "gstreamer", false, UiLanguage.ZH_CN));
        expectFailure(() -> new LauncherSettings(
                "AirPlay", 5001, 1920, 1080, 0, "gstreamer", false, UiLanguage.ZH_CN));
        expectFailure(() -> new LauncherSettings(
                "AirPlay", 5001, 1920, 1080, 60, "unknown", false, UiLanguage.ZH_CN));
    }

    private static void editableNumericFieldsAcceptCustomValues() {
        assertEquals(List.of("1280 (1K)", "1920 (2K)", "2560 (2.5K)", "3840 (4K)"),
                LauncherFrame.WIDTH_CANDIDATES,
                "width candidates");
        assertEquals(List.of("720 (1K)", "1080 (2K)", "1440 (2.5K)", "2160 (4K)"),
                LauncherFrame.HEIGHT_CANDIDATES,
                "height candidates");
        assertEquals(List.of(24, 30, 60), LauncherFrame.FPS_CANDIDATES,
                "frame-rate candidates");
        assertEquals(3840, LauncherFrame.parseEditableInteger(3840, "Width"),
                "numeric candidate");
        assertEquals(3840, LauncherFrame.parseEditableInteger("3840 (4K)", "Width"),
                "labeled width candidate");
        assertEquals(2560, LauncherFrame.parseEditableInteger("2560 (2.5K)", "Width"),
                "decimal K width candidate");
        assertEquals(2160, LauncherFrame.parseEditableInteger("2160 (4K)", "Height"),
                "labeled height candidate");
        assertEquals(720, LauncherFrame.parseEditableInteger("720 (1K)", "Height"),
                "1K height candidate");
        assertEquals(3440, LauncherFrame.parseEditableInteger(" 3440 ", "Width"),
                "custom keyboard input");
        expectFailure(() -> LauncherFrame.parseEditableInteger("wide", "Width"));
        expectFailure(() -> LauncherFrame.parseEditableInteger("", "Width"));
        expectFailure(() -> LauncherFrame.parseEditableInteger("60.5", "Frame Rate"));
        expectFailure(() -> LauncherFrame.parseEditableInteger("2147483648", "Width"));
        expectFailure(() -> LauncherFrame.parseEditableInteger(Long.MAX_VALUE, "Width"));
    }

    private static void configStorePreservesExternalContent() throws Exception {
        Path directory = Files.createTempDirectory("airplay-launcher-config-test-");
        try {
            Path path = directory.resolve("application.properties");
            Files.write(path, List.of(
                    "# retained comment",
                    "custom.setting=keep-me",
                    "airplay.serverName=Old",
                    "airplay.width=1280",
                    "airplay.height=720",
                    "airplay.fps=30",
                    "player.implementation=gstreamer",
                    "player.gstreamer.fullscreen=false",
                    "launcher.language=zh-CN",
                    "player.tray.enabled=true"), StandardCharsets.UTF_8);

            ConfigStore store = new ConfigStore(path);
            LauncherSettings loaded = store.load();
            assertEquals("Old", loaded.serverName(), "loaded server name");
            assertEquals(1280, loaded.width(), "loaded width");
            assertEquals(UiLanguage.ZH_CN, loaded.language(), "loaded language");

            LauncherSettings replacement = new LauncherSettings(
                    "Living Room", 7000, 3840, 2160, 60, "gstreamer", true, UiLanguage.EN_US);
            store.save(replacement);
            String saved = Files.readString(path, StandardCharsets.UTF_8);
            assertContains(saved, "# retained comment", "comment");
            assertContains(saved, "custom.setting=keep-me", "unknown property");
            assertContains(saved, "player.tray.enabled=true", "preserved service tray property");
            assertContains(saved, "airplay.serverName=Living Room", "updated name");
            assertContains(saved, "airplay.airtunesPort=7000", "updated service port");
            assertContains(saved, "player.gstreamer.fullscreen=true", "updated fullscreen");
            assertContains(saved, "launcher.language=en-US", "updated language");
            assertEquals(replacement, store.load(), "reloaded settings");

            store.saveLanguage(UiLanguage.ZH_CN);
            String languageOnly = Files.readString(path, StandardCharsets.UTF_8);
            assertContains(languageOnly, "# retained comment", "comment after language save");
            assertContains(languageOnly, "custom.setting=keep-me", "unknown property after language save");
            assertContains(languageOnly, "player.tray.enabled=true", "tray property after language save");
            assertContains(languageOnly, "airplay.width=3840", "width after language save");
            assertContains(languageOnly, "airplay.height=2160", "height after language save");
            assertContains(languageOnly, "launcher.language=zh-CN", "language-only update");
            assertEquals(UiLanguage.ZH_CN, store.load().language(), "language-only reload");
        } finally {
            deleteTree(directory);
        }
    }

    private static void languagesAndMessagesAreComplete() {
        assertEquals(UiLanguage.ZH_CN, UiLanguage.fromCode("zh-CN"), "Chinese language code");
        assertEquals(UiLanguage.EN_US, UiLanguage.fromCode("en-US"), "English language code");
        assertEquals(UiLanguage.systemDefault(), UiLanguage.fromCode("not-a-language"),
                "unknown language fallback");
        for (LauncherMessages.Key key : LauncherMessages.Key.values()) {
            for (UiLanguage language : UiLanguage.values()) {
                String message = LauncherMessages.text(language, key, "value", "second");
                if (message == null || message.isBlank()) {
                    throw new AssertionError("Missing " + language + " message for " + key);
                }
            }
        }
    }

    private static void guiSavePreservesHiddenPort() throws Exception {
        Path directory = Files.createTempDirectory("airplay-launcher-hidden-port-test-");
        try {
            Path path = directory.resolve("application.properties");
            Files.write(path, List.of(
                    "# custom port remains externally managed",
                    "airplay.serverName=Old",
                    "airplay.airtunesPort = 7001",
                    "airplay.width=1920",
                    "airplay.height=1080",
                    "airplay.fps=60",
                    "player.implementation=gstreamer",
                    "player.gstreamer.fullscreen=false"), StandardCharsets.UTF_8);
            ConfigStore store = new ConfigStore(path);
            store.saveGuiSettings(new LauncherSettings(
                    "Updated", 5001, 3440, 1440, 75,
                    "gstreamer", false, UiLanguage.EN_US));

            String saved = Files.readString(path, StandardCharsets.UTF_8);
            assertContains(saved, "airplay.airtunesPort = 7001", "hidden port source line");
            LauncherSettings reloaded = store.load();
            assertEquals(7001, reloaded.airtunesPort(), "hidden port value");
            assertEquals(3440, reloaded.width(), "custom width");
            assertEquals(1440, reloaded.height(), "custom height");
            assertEquals(75, reloaded.fps(), "custom frame rate");

            Path noPortPath = directory.resolve("no-port.properties");
            Files.write(noPortPath, List.of("airplay.serverName=No Port"), StandardCharsets.UTF_8);
            ConfigStore noPortStore = new ConfigStore(noPortPath);
            noPortStore.saveGuiSettings(new LauncherSettings(
                    "No Port", 5001, 1920, 1080, 60,
                    "gstreamer", false, UiLanguage.ZH_CN));
            String noPortSaved = Files.readString(noPortPath, StandardCharsets.UTF_8);
            assertNotContains(noPortSaved, "airplay.airtunesPort", "absent hidden port");
            assertEquals(5001, noPortStore.load().airtunesPort(), "default hidden port");
        } finally {
            deleteTree(directory);
        }
    }

    private static void statusTextSwitchesLanguageImmediately() {
        ServerProcessManager.Snapshot running = new ServerProcessManager.Snapshot(
                ServerProcessManager.State.RUNNING,
                123,
                true,
                true,
                false,
                null,
                ServerProcessManager.Detail.SERVICE_RUNNING,
                null);
        LauncherStatusText.Display chinese = LauncherStatusText.render(UiLanguage.ZH_CN, running);
        LauncherStatusText.Display english = LauncherStatusText.render(UiLanguage.EN_US, running);
        assertNotEquals(chinese.state(), english.state(), "localized state");
        assertNotEquals(chinese.detail(), english.detail(), "localized detail");
        assertNotEquals(chinese.uptime(), english.uptime(), "localized uptime");
        assertContains(chinese.uptime(), "--:--:--", "Chinese uptime value");
        assertContains(english.uptime(), "--:--:--", "English uptime value");

        ServerProcessManager.Snapshot failed = new ServerProcessManager.Snapshot(
                ServerProcessManager.State.FAILED,
                0,
                false,
                false,
                false,
                null,
                ServerProcessManager.Detail.START_FAILED,
                new LauncherIOException(LauncherMessages.Key.ERROR_MISSING_JAVA_RUNTIME, "runtime"));
        assertContains(LauncherStatusText.render(UiLanguage.ZH_CN, failed).detail(),
                "缺少 Java", "Chinese nested failure");
        assertContains(LauncherStatusText.render(UiLanguage.EN_US, failed).detail(),
                "Java runtime is missing", "English nested failure");
    }

    private static void trayLabelsSwitchLanguageImmediately() {
        ServerProcessManager.Snapshot running = new ServerProcessManager.Snapshot(
                ServerProcessManager.State.RUNNING,
                123,
                true,
                true,
                false,
                null,
                ServerProcessManager.Detail.SERVICE_RUNNING,
                null);
        LauncherTray.Labels chinese = LauncherTray.labels(UiLanguage.ZH_CN, running);
        LauncherTray.Labels english = LauncherTray.labels(UiLanguage.EN_US, running);

        assertEquals("打开", chinese.open(), "Chinese tray open");
        assertEquals("启动", chinese.start(), "Chinese tray start");
        assertEquals("停止", chinese.stop(), "Chinese tray stop");
        assertEquals("重启", chinese.restart(), "Chinese tray restart");
        assertEquals("全屏", chinese.fullscreen(), "Chinese tray fullscreen");
        assertEquals("窗口模式", chinese.windowed(), "Chinese tray windowed");
        assertEquals("退出", chinese.exit(), "Chinese tray exit");
        assertContains(chinese.tooltip(), "Java AirPlay 启动器", "Chinese tray title");
        assertContains(chinese.tooltip(), "运行中", "Chinese tray state");

        assertEquals("Open", english.open(), "English tray open");
        assertEquals("Start", english.start(), "English tray start");
        assertEquals("Stop", english.stop(), "English tray stop");
        assertEquals("Restart", english.restart(), "English tray restart");
        assertEquals("Fullscreen", english.fullscreen(), "English tray fullscreen");
        assertEquals("Windowed", english.windowed(), "English tray windowed");
        assertEquals("Exit", english.exit(), "English tray exit");
        assertContains(english.tooltip(), "Java AirPlay Launcher", "English tray title");
        assertContains(english.tooltip(), "Running", "English tray state");
    }

    private static void controlClientUsesAuthenticatedLoopbackProtocol() throws Exception {
        String token = "test-token-0123456789";
        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
                try {
                    respond(server, token + "\tSTATUS",
                            "OK\tSTATUS\tRUNNING\ttrue\tFULLSCREEN_AVAILABLE\ttrue\tFULLSCREEN\tfalse");
                    respond(server, token + "\tFULLSCREEN\ttrue", "OK\tFULLSCREEN\ttrue");
                    respond(server, token + "\tQUIT", "OK\tQUIT");
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });

            ControlClient client = new ControlClient();
            ControlClient.Status status = client.status(server.getLocalPort(), token);
            assertEquals(true, status.running(), "running status");
            assertEquals(true, status.fullscreenAvailable(), "fullscreen availability");
            assertEquals(false, status.fullscreen(), "initial fullscreen");
            assertEquals(true, client.setFullscreen(server.getLocalPort(), token, true), "fullscreen response");
            client.quit(server.getLocalPort(), token);
            serverTask.get(2, TimeUnit.SECONDS);
        }
    }

    private static void languageLoadsBeforeInvalidSettings() throws Exception {
        Path directory = Files.createTempDirectory("airplay-launcher-language-error-test-");
        try {
            Path path = directory.resolve("application.properties");
            Files.write(path, List.of(
                    "launcher.language=en-US",
                    "airplay.width=not-an-integer"), StandardCharsets.UTF_8);
            ConfigStore store = new ConfigStore(path);
            assertEquals(UiLanguage.EN_US, store.loadLanguage(), "language before invalid settings");
            try {
                store.load();
                throw new AssertionError("Expected invalid configuration failure");
            } catch (LauncherInputException expected) {
                assertContains(LauncherMessages.failureText(UiLanguage.EN_US, expected),
                        "must be an integer", "localized invalid configuration");
            }
        } finally {
            deleteTree(directory);
        }
    }

    private static void respond(ServerSocket server, String expectedRequest, String response) throws IOException {
        try (Socket socket = server.accept();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            assertEquals(expectedRequest, reader.readLine(), "control request");
            writer.write(response);
            writer.newLine();
            writer.flush();
        }
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected validation failure");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void assertContains(String actual, String expected, String description) {
        if (!actual.contains(expected)) {
            throw new AssertionError(description + " expected to contain " + expected + " but was " + actual);
        }
    }

    private static void assertNotContains(String actual, String unexpected, String description) {
        if (actual.contains(unexpected)) {
            throw new AssertionError(description + " expected not to contain " + unexpected
                    + " but was " + actual);
        }
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertNotEquals(Object first, Object second, String description) {
        if (first.equals(second)) {
            throw new AssertionError(description + " expected different values but both were " + first);
        }
    }
}
