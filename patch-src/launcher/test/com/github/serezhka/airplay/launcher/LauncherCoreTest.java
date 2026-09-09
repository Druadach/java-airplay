package com.github.serezhka.airplay.launcher;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class LauncherCoreTest {
    private LauncherCoreTest() {
    }

    public static void main(String[] arguments) throws Exception {
        languagesAndMessagesAreComplete();
        GitHubUpdateCheckerTest.runAll();
        AutomaticUpdateTest.runAll();
        settingLabelsExplainReceiverChoices();
        statusTextSwitchesLanguageImmediately();
        trayLabelsSwitchLanguageImmediately();
        editableNumericFieldsAcceptCustomValues();
        settingsValidationRejectsInvalidValues();
        configStorePreservesExternalContent();
        guiSavePreservesHiddenPort();
        languageLoadsBeforeInvalidSettings();
        startupUsesBundledRuntimeAndExplicitDirectory();
        defaultsAndSystemLanguageRoundTrip();
        resolutionPresetsKeepDimensionsTogether();
        onlyServiceChangesRequireRestart();
        resettingGuiSettingsPreservesAdvancedConfiguration();
        externalPlayersAreCheckedBeforeStartup();
        trayPreferencesPersistAndControlWindowBehavior();
        resolutionAndFrameRateLabelsFit();
        advancedPlayerRowsDoNotOverlap();
        runtimeLogsAreCollapsedUntilExpanded();
        controlClientUsesAuthenticatedLoopbackProtocol();
        System.out.println("Launcher core tests passed");
    }

    private static void startupUsesBundledRuntimeAndExplicitDirectory() throws Exception {
        Path directory = Files.createTempDirectory("AirPlay startup with spaces ");
        try {
            Path java = directory.resolve("jre/bin/javaw.exe");
            Files.createDirectories(java.getParent());
            Files.createFile(java);
            Files.createFile(directory.resolve("java-airplay-server-fixed.jar"));
            Path jar = Files.createFile(directory.resolve("java-airplay-launcher.jar"));
            Path exe = Files.createFile(directory.resolve("AirPlayReceiver.exe"));
            assertEquals(directory, AirPlayLauncher.resolveBaseDirectory(
                    new String[]{"--base-dir", directory.toString()}), "separate directory argument");
            assertEquals(directory, AirPlayLauncher.resolveBaseDirectory(
                    new String[]{"--base-dir=" + directory}), "joined directory argument");
            try {
                AirPlayLauncher.resolveBaseDirectory(new String[]{"--base-dir"});
                throw new AssertionError("Missing explicit directory was ignored");
            } catch (IOException expected) {
                // Missing arguments must not silently use another installation.
            }
            String jarCommand = AutoStartManager.startupCommand(jar);
            assertContains(jarCommand, "\"" + java + "\" -Dfile.encoding=UTF-8 -jar \"" + jar + "\"",
                    "startup uses bundled Java for jar");
            assertContains(jarCommand, "\"--base-dir=" + directory + "\"", "startup directory");
            assertContains(jarCommand, "--auto-start", "jar startup marker");
            assertNotContains(jarCommand, "--minimized", "startup visibility comes from preferences");
            assertEquals("\"" + exe + "\" --auto-start",
                    AutoStartManager.startupCommand(exe), "exe startup flags");
            LauncherSettings defaults = LauncherSettings.defaults();
            ConfigStore store = new ConfigStore(directory.resolve("application.properties"));
            store.save(new LauncherSettings(defaults.serverName(), defaults.airtunesPort(),
                    defaults.width(), defaults.height(), defaults.fps(), defaults.playerImplementation(),
                    defaults.startFullscreen(), true, true,
                    defaults.startMinimized(), defaults.closeToTray(), defaults.language()));
            assertEquals(true, store.load().autoStartEnabled(), "startup preference persisted");
            assertEquals(true, store.load().autoRunService(), "auto-run preference persisted");
        } finally {
            deleteTree(directory);
        }
    }

    private static void settingsValidationRejectsInvalidValues() {
        expectFailure(() -> new LauncherSettings(
                "", 5001, 1920, 1080, 60, "gstreamer", false, false, false, false, true, UiLanguage.ZH_CN));
        expectFailure(() -> new LauncherSettings(
                "AirPlay", 0, 1920, 1080, 60, "gstreamer", false, false, false, false, true, UiLanguage.ZH_CN));
        expectFailure(() -> new LauncherSettings(
                "AirPlay", 5001, 10, 1080, 60, "gstreamer", false, false, false, false, true, UiLanguage.ZH_CN));
        expectFailure(() -> new LauncherSettings(
                "AirPlay", 5001, 1920, 1080, 0, "gstreamer", false, false, false, false, true, UiLanguage.ZH_CN));
        expectFailure(() -> new LauncherSettings(
                "AirPlay", 5001, 1920, 1080, 60, "unknown", false, false, false, false, true, UiLanguage.ZH_CN));
    }

    private static void editableNumericFieldsAcceptCustomValues() {
        assertEquals(List.of(1280, 1920, 2560, 3840),
                LauncherFrame.WIDTH_CANDIDATES,
                "width candidates");
        assertEquals(List.of(720, 1080, 1440, 2160),
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
        assertEquals(720, LauncherFrame.parseEditableInteger("720 (HD)", "Height"),
                "HD height candidate");
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
                    "Living Room", 7000, 3840, 2160, 60, "gstreamer", true, false, false, false, true, UiLanguage.EN_US);
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
        assertEquals(UiLanguage.SYSTEM, UiLanguage.fromCode("not-a-language"),
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

    private static void settingLabelsExplainReceiverChoices() {
        assertEquals("投屏设置", LauncherMessages.text(UiLanguage.ZH_CN,
                LauncherMessages.Key.CONFIGURATION_SECTION), "settings describe their purpose");
        assertEquals("投屏名称", LauncherMessages.text(UiLanguage.ZH_CN,
                LauncherMessages.Key.SERVER_NAME_LABEL), "receiver name label");
        assertEquals("AirPlay Name", LauncherMessages.text(UiLanguage.EN_US,
                LauncherMessages.Key.SERVER_NAME_LABEL), "English receiver name label");
        assertEquals("最高帧率", LauncherMessages.text(UiLanguage.ZH_CN, LauncherMessages.Key.FPS_LABEL),
                "concise Chinese frame rate label");
        assertContains(LauncherMessages.text(UiLanguage.EN_US, LauncherMessages.Key.FPS_LABEL),
                "fps", "English frame rate unit");
        assertContains(UiLanguage.SYSTEM.label(), "跟随系统", "Chinese system language choice");
        assertContains(UiLanguage.SYSTEM.label(), "System default", "English system language choice");
        assertEquals("开机自动启动", LauncherMessages.text(UiLanguage.ZH_CN,
                LauncherMessages.Key.AUTO_START_LABEL), "requested Windows startup wording");
        assertEquals("软件启动时最小化到托盘", LauncherMessages.text(UiLanguage.ZH_CN,
                LauncherMessages.Key.START_MINIMIZED), "requested minimized startup wording");
        assertEquals("软件关闭时最小化到托盘", LauncherMessages.text(UiLanguage.ZH_CN,
                LauncherMessages.Key.CLOSE_TO_TRAY), "requested close to tray wording");
        assertEquals("启动AirPlay接收", LauncherMessages.text(UiLanguage.ZH_CN,
                LauncherMessages.Key.START), "explicit reception start wording");
        assertEquals("停止AirPlay接收", LauncherMessages.text(UiLanguage.ZH_CN,
                LauncherMessages.Key.STOP), "explicit reception stop wording");
        for (LauncherMessages.Key key : List.of(LauncherMessages.Key.RESTART, LauncherMessages.Key.RESTART_AND_APPLY)) {
            assertEquals("重启AirPlay接收", LauncherMessages.text(UiLanguage.ZH_CN, key),
                    "consistent reception restart wording");
        }
        assertContains(LauncherMessages.text(UiLanguage.ZH_CN, LauncherMessages.Key.UPDATE_CURRENT_MESSAGE,
                "1.2.2", "v1.2.2"), "当前已是最新版本。", "concise current-version result");
        assertEquals("展开运行日志", LauncherMessages.text(UiLanguage.ZH_CN,
                LauncherMessages.Key.EXPAND_RUNTIME_LOG), "expand logs wording");
        assertEquals("收起运行日志", LauncherMessages.text(UiLanguage.ZH_CN,
                LauncherMessages.Key.COLLAPSE_RUNTIME_LOG), "collapse logs wording");
        assertEquals("Start AirPlay in fullscreen", LauncherMessages.text(UiLanguage.EN_US,
                LauncherMessages.Key.START_FULLSCREEN), "requested AirPlay fullscreen wording");
        assertContains(LauncherMessages.text(UiLanguage.ZH_CN, LauncherMessages.Key.AUTO_START_HINT),
                "登录 Windows", "Windows startup is distinct from reception");
        assertContains(LauncherMessages.text(UiLanguage.ZH_CN, LauncherMessages.Key.AUTO_START_AND_RUN_HINT),
                "仍需", "automatic reception still requires a sender connection");
        assertContains(LauncherMessages.text(UiLanguage.EN_US, LauncherMessages.Key.AUTO_START_AND_RUN_HINT),
                "still need to select this PC", "English automatic reception explanation");
        for (UiLanguage language : List.of(UiLanguage.ZH_CN, UiLanguage.EN_US)) {
            assertContains(LauncherMessages.text(language, LauncherMessages.Key.SERVER_NAME_HINT),
                    "AirPlay", "name discovery hint: " + language);
            assertContains(PlayerOption.GSTREAMER.hint(language), "GStreamer",
                    "built-in player remains identifiable: " + language);
            assertContains(PlayerOption.FFMPEG.label(language), "FFplay",
                    "external player names the actual application: " + language);
            assertContains(PlayerOption.FFMPEG.hint(language), "FFmpeg",
                    "FFplay hint identifies the package: " + language);
            assertContains(PlayerOption.H264_DUMP.hint(language), "dump.h264",
                    "debug mode explains its output: " + language);
        }
        assertEquals("gstreamer", PlayerOption.GSTREAMER.implementation(), "built-in player value unchanged");
        assertEquals("ffmpeg", PlayerOption.FFMPEG.implementation(), "FFplay setting value unchanged");
    }

    private static void runtimeLogsAreCollapsedUntilExpanded() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (UiLanguage language : List.of(UiLanguage.ZH_CN, UiLanguage.EN_US)) {
                JButton start = new JButton(LauncherMessages.text(language, LauncherMessages.Key.START));
                JButton stop = new JButton(LauncherMessages.text(language, LauncherMessages.Key.STOP));
                JButton restart = new JButton(LauncherMessages.text(language, LauncherMessages.Key.RESTART));
                JPanel buttons = LauncherFrame.createServiceButtons(start, stop, restart);
                JPanel configuration = new JPanel(new BorderLayout());
                configuration.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
                configuration.add(buttons, BorderLayout.SOUTH);
                JTextArea logArea = new JTextArea("earlier log\n");
                JPanel logs = new JPanel(new BorderLayout());
                logs.setMinimumSize(new Dimension(180, 0));
                logs.add(new JScrollPane(logArea), BorderLayout.CENTER);
                CollapsibleLogPane pane = new CollapsibleLogPane(configuration, logs);
                assertEquals(false, pane.isExpanded(), "logs start collapsed");
                assertEquals(false, logs.isVisible(), "log panel starts hidden");
                assertEquals(false, SwingUtilities.isDescendingFrom(logs, pane), "hidden logs occupy no layout space");
                assertEquals(configuration, pane.getComponent(0), "settings are the only collapsed content");
                pane.setSize(1040, 600);
                pane.doLayout();
                assertEquals(1040, configuration.getWidth(), "collapsed settings use the available width");
                logArea.append("received while hidden\n");

                pane.setExpanded(true);
                pane.doLayout();
                JSplitPane splitPane = (JSplitPane) pane.getComponent(0);
                splitPane.doLayout();
                configuration.doLayout();
                buttons.doLayout();
                assertEquals(true, logs.isVisible(), "expanded logs visible");
                assertEquals(true, SwingUtilities.isDescendingFrom(logs, pane), "expanded logs attached");
                assertServiceButtonsFit(buttons);

                restart.setText(LauncherMessages.text(language, LauncherMessages.Key.RESTART_AND_APPLY));
                pane.doLayout();
                splitPane.doLayout();
                configuration.doLayout();
                buttons.doLayout();
                assertServiceButtonsFit(buttons);

                splitPane.setDividerLocation(650);
                pane.setExpanded(false);
                pane.setExpanded(true);
                assertEquals(650, splitPane.getDividerLocation(), "expansion preserves divider position");
                pane.setSize(1440, 600);
                pane.doLayout();
                splitPane.setDividerLocation(1000);
                pane.setExpanded(false);
                pane.setSize(900, 600);
                pane.doLayout();
                pane.setExpanded(true);
                pane.doLayout();
                splitPane.doLayout();
                configuration.doLayout();
                buttons.doLayout();
                assertServiceButtonsFit(buttons);
                assertEquals(true, logs.getWidth() >= logs.getMinimumSize().width,
                        "logs remain visible after resizing while collapsed");
                pane.setExpanded(false);
                pane.setExpanded(false);
                assertEquals(1, pane.getComponentCount(), "repeated collapse does not duplicate content");
                assertEquals("earlier log\nreceived while hidden\n", logArea.getText(), "toggling preserves log history");
            }
        });
    }

    private static void assertServiceButtonsFit(JPanel buttons) {
        for (var component : buttons.getComponents()) {
            JButton button = (JButton) component;
            if (button.getWidth() < button.getPreferredSize().width) {
                throw new AssertionError("Service button text clipped: " + button.getText());
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
                    "gstreamer", false, false, false, false, true, UiLanguage.EN_US));

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
                    "gstreamer", false, false, false, false, true, UiLanguage.ZH_CN));
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

        assertEquals("显示主窗口", chinese.open(), "Chinese tray open");
        assertEquals("启动AirPlay接收", chinese.start(), "Chinese tray start");
        assertEquals("停止AirPlay接收", chinese.stop(), "Chinese tray stop");
        assertEquals("全屏", chinese.fullscreen(), "Chinese tray fullscreen");
        assertEquals("设置...", chinese.settings(), "Chinese tray settings");
        assertEquals("检查更新", chinese.checkUpdates(), "Chinese tray update check");
        assertEquals("关于", chinese.about(), "Chinese tray about");
        assertEquals("退出", chinese.exit(), "Chinese tray exit");
        assertContains(chinese.tooltip(), "AirPlay 接收器", "Chinese tray title");
        assertContains(chinese.tooltip(), "运行中", "Chinese tray state");

        assertEquals("Show Main Window", english.open(), "English tray open");
        assertEquals("Start Service", english.start(), "English tray start");
        assertEquals("Stop Service", english.stop(), "English tray stop");
        assertEquals("Fullscreen", english.fullscreen(), "English tray fullscreen");
        assertEquals("Settings...", english.settings(), "English tray settings");
        assertEquals("Check for Updates", english.checkUpdates(), "English tray update check");
        assertEquals("About", english.about(), "English tray about");
        assertEquals("Exit", english.exit(), "English tray exit");
        assertContains(english.tooltip(), "AirPlay Receiver", "English tray title");
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

    private static void defaultsAndSystemLanguageRoundTrip() throws Exception {
        LauncherSettings defaults = LauncherSettings.defaults();
        assertEquals("AirPlay - PC", defaults.serverName(), "default receiver name");
        assertEquals(1920, defaults.width(), "default width");
        assertEquals(1080, defaults.height(), "default height");
        assertEquals(60, defaults.fps(), "default maximum FPS");
        assertEquals("gstreamer", defaults.playerImplementation(), "default player");
        assertEquals(false, defaults.startFullscreen(), "default windowed mode");
        assertEquals(false, defaults.autoStartEnabled(), "default sign-in startup");
        assertEquals(false, defaults.autoRunService(), "default service startup");
        assertEquals(false, defaults.startMinimized(), "show window on startup by default");
        assertEquals(true, defaults.closeToTray(), "close to tray by default");
        assertEquals(UiLanguage.SYSTEM, defaults.language(), "default language preference");
        assertEquals(UiLanguage.SYSTEM, UiLanguage.fromCode(null), "missing language preference");
        assertEquals(UiLanguage.SYSTEM, UiLanguage.fromCode("system"), "system language code");

        Locale original = Locale.getDefault();
        Path directory = Files.createTempDirectory("airplay-system-language-test-");
        try {
            ConfigStore store = new ConfigStore(directory.resolve("application.properties"));
            assertEquals(defaults, store.load(), "missing configuration defaults");
            store.saveGuiSettings(defaults);
            assertContains(Files.readString(store.path()), "launcher.language=system", "system preference saved");
            Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
            assertEquals(UiLanguage.ZH_CN, store.load().language().resolved(), "Chinese system language");
            assertEquals("启动AirPlay接收", LauncherMessages.text(store.load().language(), LauncherMessages.Key.START),
                    "Chinese system message");
            Locale.setDefault(Locale.US);
            assertEquals(UiLanguage.SYSTEM, store.load().language(), "system preference remains automatic");
            assertEquals("Start", LauncherMessages.text(store.load().language(), LauncherMessages.Key.START),
                    "English system message");
            store.saveLanguage(UiLanguage.ZH_CN);
            assertEquals(UiLanguage.ZH_CN, store.load().language().resolved(), "explicit language overrides system");
        } finally {
            Locale.setDefault(original);
            deleteTree(directory);
        }
    }

    private static void resolutionPresetsKeepDimensionsTogether() {
        for (ResolutionPreset preset : ResolutionPreset.values()) {
            if (preset != ResolutionPreset.CUSTOM) {
                assertEquals(preset, ResolutionPreset.forSize(preset.width(), preset.height()),
                        "paired resolution " + preset);
                assertEquals(preset.width() * 9, preset.height() * 16, "preset aspect ratio " + preset);
                for (UiLanguage language : List.of(UiLanguage.ZH_CN, UiLanguage.EN_US)) {
                    assertContains(preset.label(language), preset.width() + " × " + preset.height(),
                            "preset shows its pixel dimensions: " + preset + " / " + language);
                }
            }
        }
        assertEquals(ResolutionPreset.FULL_HD, ResolutionPreset.forSize(1920, 1080), "default preset");
        assertEquals(ResolutionPreset.CUSTOM, ResolutionPreset.forSize(3440, 1440), "ultrawide custom mode");
        assertEquals(ResolutionPreset.CUSTOM, ResolutionPreset.forSize(3840, 720), "mixed dimensions stay custom");
        assertEquals("自定义", ResolutionPreset.CUSTOM.label(UiLanguage.ZH_CN), "Chinese custom label");
        assertContains(ResolutionPreset.QUAD_HD.label(UiLanguage.EN_US), "1440p", "1440p terminology");
        assertContains(ResolutionPreset.ULTRA_HD.label(UiLanguage.EN_US), "4K", "4K terminology");
    }

    private static void onlyServiceChangesRequireRestart() {
        LauncherSettings baseline = LauncherSettings.defaults();
        LauncherSettings preferencesOnly = new LauncherSettings(baseline.serverName(), 5001, 1920, 1080, 60,
                "gstreamer", false, true, true, true, false, UiLanguage.EN_US);
        assertEquals(true, baseline.sameServiceConfiguration(preferencesOnly), "launcher preferences apply live");
        assertEquals(true, baseline.sameServiceConfiguration(baseline.withLanguage(UiLanguage.ZH_CN)),
                "language does not restart casting");
        assertEquals(false, baseline.sameServiceConfiguration(null), "no applied service configuration");
        List<LauncherSettings> serviceChanges = List.of(
                new LauncherSettings("Bedroom", 5001, 1920, 1080, 60, "gstreamer", false, false, false, false, true, UiLanguage.SYSTEM),
                new LauncherSettings(baseline.serverName(), 7001, 1920, 1080, 60, "gstreamer", false, false, false, false, true, UiLanguage.SYSTEM),
                new LauncherSettings(baseline.serverName(), 5001, 2560, 1080, 60, "gstreamer", false, false, false, false, true, UiLanguage.SYSTEM),
                new LauncherSettings(baseline.serverName(), 5001, 1920, 1440, 60, "gstreamer", false, false, false, false, true, UiLanguage.SYSTEM),
                new LauncherSettings(baseline.serverName(), 5001, 1920, 1080, 30, "gstreamer", false, false, false, false, true, UiLanguage.SYSTEM),
                new LauncherSettings(baseline.serverName(), 5001, 1920, 1080, 60, "ffmpeg", false, false, false, false, true, UiLanguage.SYSTEM),
                new LauncherSettings(baseline.serverName(), 5001, 1920, 1080, 60, "gstreamer", true, false, false, false, true, UiLanguage.SYSTEM));
        for (LauncherSettings changed : serviceChanges) {
            assertEquals(false, baseline.sameServiceConfiguration(changed), "service change needs restart: " + changed);
        }
        assertEquals(false, baseline.unverifiedVideoMode(), "default video mode verified");
        assertEquals(false, new LauncherSettings(baseline.serverName(), 5001, 3840, 2160, 60,
                "gstreamer", false, false, false, false, true, UiLanguage.SYSTEM).unverifiedVideoMode(), "4K/60 verified");
        assertEquals(true, new LauncherSettings(baseline.serverName(), 5001, 1920, 1080, 120,
                "gstreamer", false, false, false, false, true, UiLanguage.SYSTEM).unverifiedVideoMode(), "120 FPS is experimental");
        assertEquals(true, new LauncherSettings(baseline.serverName(), 5001, 7680, 4320, 240,
                "gstreamer", false, false, false, false, true, UiLanguage.SYSTEM).unverifiedVideoMode(), "8K/240 is experimental");
    }

    private static void resettingGuiSettingsPreservesAdvancedConfiguration() throws Exception {
        Path directory = Files.createTempDirectory("airplay-reset-settings-test-");
        try {
            ConfigStore store = new ConfigStore(directory.resolve("application.properties"));
            Files.write(store.path(), List.of("airplay.airtunesPort = 7001", "custom.setting=keep-me",
                    "player.tray.enabled=true", "launcher.language=en-US"), StandardCharsets.UTF_8);
            LauncherSettings restored = LauncherSettings.defaults(store.load().airtunesPort());
            store.saveGuiSettings(restored);
            assertEquals(restored, store.load(), "GUI defaults restored");
            assertEquals(7001, store.load().airtunesPort(), "custom port survives reset");
            String saved = Files.readString(store.path());
            assertContains(saved, "airplay.airtunesPort = 7001", "hidden port line preserved on reset");
            assertContains(saved, "custom.setting=keep-me", "unknown option preserved on reset");
            assertContains(saved, "player.tray.enabled=true", "legacy tray option preserved on reset");
            assertContains(saved, "launcher.language=system", "reset restores system language");
        } finally {
            deleteTree(directory);
        }
    }

    private static void externalPlayersAreCheckedBeforeStartup() throws Exception {
        Path directory = Files.createTempDirectory("airplay-player-availability-test-");
        try {
            Path base = Files.createDirectory(directory.resolve("application"));
            Path tools = Files.createDirectory(directory.resolve("external tools"));
            assertEquals(true, PlayerOption.FFMPEG.findExecutable(base, null) == null, "missing FFplay");
            PlayerOption.GSTREAMER.requireAvailable(base, null);
            PlayerOption.H264_DUMP.requireAvailable(base, null);
            try {
                PlayerOption.FFMPEG.requireAvailable(base, null);
                throw new AssertionError("Missing FFplay should fail before starting the server");
            } catch (LauncherIOException expected) {
                assertContains(LauncherMessages.failureText(UiLanguage.EN_US, expected), "ffplay.exe",
                        "actionable dependency error");
            }
            Path ffplay = Files.createFile(tools.resolve("ffplay.exe"));
            String searchPath = "\"" + tools + "\"" + java.io.File.pathSeparator + "invalid\u0000path";
            assertEquals(ffplay, PlayerOption.FFMPEG.findExecutable(base, searchPath), "quoted PATH with spaces");
            PlayerOption.FFMPEG.requireAvailable(base, searchPath);
            Path vlc = Files.createFile(base.resolve("vlc.exe"));
            assertEquals(vlc, PlayerOption.VLC.findExecutable(base, ""), "player in application directory");
            assertEquals(PlayerOption.FFMPEG, PlayerOption.fromImplementation("ffmpeg"), "player setting lookup");
            assertContains(PlayerOption.H264_DUMP.hint(UiLanguage.EN_US), "without a playback window",
                    "dump mode clearly labeled");
        } finally {
            deleteTree(directory);
        }
    }

    private static void trayPreferencesPersistAndControlWindowBehavior() throws Exception {
        Path directory = Files.createTempDirectory("airplay-window-preferences-test-");
        try {
            ConfigStore store = new ConfigStore(directory.resolve("application.properties"));
            Files.writeString(store.path(), "launcher.language=en-US\n", StandardCharsets.UTF_8);
            LauncherSettings previous = store.load();
            assertEquals(false, previous.startMinimized(), "legacy settings show the window by default");
            assertEquals(true, previous.closeToTray(), "legacy settings retain close-to-tray behavior");
            LauncherSettings changed = new LauncherSettings(previous.serverName(), previous.airtunesPort(),
                    previous.width(), previous.height(), previous.fps(), previous.playerImplementation(),
                    previous.startFullscreen(), previous.autoStartEnabled(), previous.autoRunService(),
                    true, false, previous.language());
            store.saveGuiSettings(changed);
            assertEquals(changed, store.load(), "tray preferences round trip");
            assertEquals(true, previous.sameServiceConfiguration(changed), "window preferences do not restart service");
            String saved = Files.readString(store.path());
            assertContains(saved, "launcher.startMinimized=true", "startup preference saved");
            assertContains(saved, "launcher.closeToTray=false", "close preference saved");
            store.saveLanguage(UiLanguage.ZH_CN);
            assertEquals(changed.withLanguage(UiLanguage.ZH_CN), store.load(), "language preserves tray preferences");
            store.saveGuiSettings(LauncherSettings.defaults(previous.airtunesPort()));
            assertEquals(false, store.load().startMinimized(), "reset restores visible startup");
            assertEquals(true, store.load().closeToTray(), "reset restores close to tray");
        } finally {
            deleteTree(directory);
        }

        assertEquals(false, LauncherFrame.startsInTray(false, false, false, true), "normal visible startup");
        assertEquals(true, LauncherFrame.startsInTray(true, false, false, true), "manual launch follows tray preference");
        assertEquals(true, LauncherFrame.startsInTray(true, false, true, true), "Windows startup follows tray preference");
        assertEquals(false, LauncherFrame.startsInTray(false, false, true, true), "Windows startup can show window");
        assertEquals(false, LauncherFrame.startsInTray(false, true, true, true), "legacy startup flag cannot override preference");
        assertEquals(true, LauncherFrame.startsInTray(false, true, false, true), "explicit manual minimized argument");
        assertEquals(false, LauncherFrame.startsInTray(true, true, true, false), "no tray means visible startup");
        assertEquals(true, LauncherFrame.closesToTray(true, true), "close hides to tray when enabled");
        assertEquals(false, LauncherFrame.closesToTray(false, true), "close exits when disabled");
        assertEquals(false, LauncherFrame.closesToTray(true, false), "close exits without a tray");
    }

    private static void resolutionAndFrameRateLabelsFit() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (UiLanguage language : List.of(UiLanguage.ZH_CN, UiLanguage.EN_US)) {
                JPanel panel = new JPanel(new GridBagLayout());
                panel.setBorder(BorderFactory.createTitledBorder(
                        LauncherMessages.text(language, LauncherMessages.Key.CONFIGURATION_SECTION)));
                GridBagConstraints constraints = LauncherFrame.formConstraints(new Insets(6, 8, 6, 8));
                JLabel resolutionLabel = new JLabel(LauncherMessages.text(language, LauncherMessages.Key.RESOLUTION_LABEL));
                JComboBox<String> resolutionCombo = new JComboBox<>(new String[]{ResolutionPreset.FULL_HD.label(language)});
                JLabel fpsLabel = new JLabel(LauncherMessages.text(language, LauncherMessages.Key.FPS_LABEL));
                JComboBox<Integer> fpsCombo = new JComboBox<>(new Integer[]{60});
                fpsCombo.setEditable(true);
                LauncherFrame.addRow(panel, constraints, resolutionLabel, resolutionCombo);
                LauncherFrame.addRow(panel, constraints, fpsLabel, fpsCombo);
                for (int width : List.of(340, 420)) {
                    String context = language.code() + " / " + width;
                    panel.setSize(width, panel.getPreferredSize().height);
                    panel.doLayout();
                    assertEquals(true, resolutionLabel.getWidth() >= resolutionLabel.getPreferredSize().width,
                            "resolution label fits: " + context);
                    assertEquals(true, resolutionCombo.getWidth() >= resolutionCombo.getPreferredSize().width,
                            "resolution pixel dimensions fit: " + context);
                    assertEquals(true, fpsLabel.getWidth() >= fpsLabel.getPreferredSize().width,
                            "frame rate label and unit fit: " + context);
                    assertEquals(true, fpsCombo.getWidth() >= fpsCombo.getPreferredSize().width,
                            "frame rate editor fits: " + context);
                }
            }
        });
    }

    private static void advancedPlayerRowsDoNotOverlap() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (UiLanguage language : List.of(UiLanguage.ZH_CN, UiLanguage.EN_US)) {
                for (PlayerOption player : PlayerOption.values()) {
                    GridBagLayout layout = new GridBagLayout();
                    JPanel panel = new JPanel(layout);
                    GridBagConstraints constraints = LauncherFrame.formConstraints(new Insets(4, 0, 4, 0));
                    JLabel playerLabel = new JLabel(LauncherMessages.text(language, LauncherMessages.Key.PLAYER_LABEL));
                    JComboBox<String> playerCombo = new JComboBox<>(new String[]{player.label(language)});
                    JLabel hint = new JLabel("<html><body style='width: 230px'>"
                            + player.hint(language) + "</body></html>");
                    JLabel dependency = new JLabel(player.executable() == null ? ""
                            : LauncherMessages.text(language, LauncherMessages.Key.PLAYER_AVAILABLE,
                                    player.executable()));
                    dependency.setVisible(player.executable() != null);
                    LauncherFrame.addRow(panel, constraints, playerLabel, playerCombo);
                    LauncherFrame.addWideRow(panel, constraints, hint);
                    LauncherFrame.addWideRow(panel, constraints, dependency);
                    assertEquals(0, layout.getConstraints(playerLabel).gridy, "player label starts on row zero");
                    assertEquals(0, layout.getConstraints(playerCombo).gridy, "player selector shares label row");
                    assertEquals(1, layout.getConstraints(hint).gridy, "hint uses its own row");
                    assertEquals(2, layout.getConstraints(dependency).gridy, "dependency status uses another row");
                    for (int width : List.of(320, 340, 420)) {
                        String context = player + " / " + language.code() + " / " + width;
                        panel.setSize(width, panel.getPreferredSize().height);
                        panel.doLayout();
                        assertEquals(true, playerCombo.getWidth() > 0, "player selector remains visible: " + context);
                        if (width >= 340) {
                            assertEquals(true, playerCombo.getWidth() >= playerCombo.getPreferredSize().width,
                                    "player choice is not clipped at normal widths: " + context);
                        }
                        assertEquals(true, playerCombo.getY() + playerCombo.getHeight() <= hint.getY(),
                                "selector and hint do not overlap: " + context);
                        assertEquals(true, playerLabel.getY() + playerLabel.getHeight() <= hint.getY(),
                                "label and hint do not overlap: " + context);
                        if (dependency.isVisible()) {
                            assertEquals(true, hint.getY() + hint.getHeight() <= dependency.getY(),
                                    "hint and dependency status do not overlap: " + context);
                        }
                    }
                }
            }
        });
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
