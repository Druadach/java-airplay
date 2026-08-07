package com.github.serezhka.airplay.launcher;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LauncherFrame extends JFrame implements LauncherTray.Actions {
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_LOG_CHARACTERS = 300_000;
    private static final int MAX_PENDING_LOG_LINES = 2_000;
    private static final int LOG_FLUSH_BATCH_SIZE = 500;
    private static final int AUTO_SAVE_DELAY_MILLIS = 600;
    private static final Pattern LABELED_INTEGER = Pattern.compile(
            "^\\s*([+-]?\\d+)\\s*(?:\\([^)]*\\))?\\s*$");
    static final List<String> WIDTH_CANDIDATES = List.of(
            "1280 (1K)", "1920 (2K)", "2560 (2.5K)", "3840 (4K)");
    static final List<String> HEIGHT_CANDIDATES = List.of(
            "720 (1K)", "1080 (2K)", "1440 (2.5K)", "2160 (4K)");
    static final List<Integer> FPS_CANDIDATES = List.of(24, 30, 60);

    private final ConfigStore configStore;
    private final ServerProcessManager processManager;
    private final Path baseDirectory;
    private final AtomicBoolean quitting = new AtomicBoolean();
    private final AtomicBoolean logFlushScheduled = new AtomicBoolean();
    private final AtomicInteger pendingLogLines = new AtomicInteger();
    private final AtomicInteger droppedLogLines = new AtomicInteger();
    private final ConcurrentLinkedQueue<String> pendingLogs = new ConcurrentLinkedQueue<>();

    private volatile UiLanguage language = UiLanguage.systemDefault();
    private boolean changingLanguage;
    private boolean languageChangesEnabled;
    private final JLabel stateLabel = new JLabel();
    private final JLabel detailLabel = new JLabel();
    private final JLabel pidLabel = new JLabel("PID --");
    private final JLabel uptimeLabel = new JLabel();
    private final JLabel languageLabel = new JLabel();
    private final JComboBox<UiLanguage> languageCombo = new JComboBox<>(UiLanguage.values());
    private final JTextField serverNameField = new JTextField();
    private final JComboBox<String> widthCombo = editableNumberCombo(WIDTH_CANDIDATES);
    private final JComboBox<String> heightCombo = editableNumberCombo(HEIGHT_CANDIDATES);
    private final JComboBox<Integer> fpsCombo = editableNumberCombo(FPS_CANDIDATES);
    private final JComboBox<String> playerCombo = new JComboBox<>(
            new String[]{"gstreamer", "ffmpeg", "vlc", "h264-dump"});
    private final JCheckBox startFullscreenCheck = new JCheckBox();
    private final JButton startButton = new JButton();
    private final JButton stopButton = new JButton();
    private final JButton restartButton = new JButton();
    private final JButton fullscreenButton = new JButton();
    private final JButton windowedButton = new JButton();
    private final JButton clearButton = new JButton();
    private final JLabel serverNameLabel = new JLabel();
    private final JLabel widthLabel = new JLabel();
    private final JLabel heightLabel = new JLabel();
    private final JLabel fpsLabel = new JLabel();
    private final JLabel playerLabel = new JLabel();
    private final JLabel baseLabel = new JLabel();
    private final JLabel logTitleLabel = new JLabel();
    private final TitledBorder configurationBorder = BorderFactory.createTitledBorder("");
    private final TitledBorder displayBorder = BorderFactory.createTitledBorder("");
    private final JTextArea logArea = new JTextArea();
    private final LauncherTray tray;
    private final Timer autoSaveTimer;
    private boolean autoSaveErrorShown;
    private int airtunesPort = 5001;

    LauncherFrame(Path baseDirectory) throws IOException {
        super("Java AirPlay Launcher");
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
        configStore = new ConfigStore(this.baseDirectory.resolve("application.properties"));
        LauncherSettings settings = configStore.load();
        language = settings.language();
        AirPlayLauncher.setCurrentLanguage(language);
        processManager = new ServerProcessManager(
                this.baseDirectory,
                configStore.path(),
                this::receiveSnapshot,
                this::receiveLog);
        autoSaveTimer = new Timer(AUTO_SAVE_DELAY_MILLIS, event -> autoSaveConfiguration());
        autoSaveTimer.setRepeats(false);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setIconImage(LauncherTray.applicationIcon());
        setMinimumSize(new Dimension(900, 580));
        setSize(1040, 680);
        setLocationRelativeTo(null);
        setContentPane(createContent());
        bindActions();
        applySettings(settings);
        bindAutoSave();

        tray = LauncherTray.install(this, language);
        applyLanguage();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (tray != null) {
                    hideToTray();
                } else {
                    quit();
                }
            }

            @Override
            public void windowIconified(WindowEvent event) {
                if (tray != null) {
                    SwingUtilities.invokeLater(LauncherFrame.this::hideToTray);
                }
            }
        });

        Timer uptimeTimer = new Timer(1_000, event -> {
            ServerProcessManager.Snapshot snapshot = processManager.snapshot();
            uptimeLabel.setText(message(LauncherMessages.Key.UPTIME, snapshot.uptime()));
        });
        uptimeTimer.start();
        applySnapshot(processManager.snapshot());
        receiveLocalizedLog(LauncherMessages.Key.LOG_LAUNCHER_READY, configStore.path());
        SwingUtilities.invokeLater(this::enableLanguageChanges);
    }

    private JPanel createContent() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBorder(new EmptyBorder(0, 0, 0, 0));
        root.add(createStatusBar(), BorderLayout.NORTH);

        JPanel configuration = createConfigurationPanel();
        JPanel logs = createLogPanel();
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, configuration, logs);
        splitPane.setResizeWeight(0.40);
        splitPane.setDividerLocation(390);
        splitPane.setBorder(null);
        root.add(splitPane, BorderLayout.CENTER);
        return root;
    }

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout(16, 0));
        panel.setBackground(new Color(31, 38, 41));
        panel.setBorder(new EmptyBorder(14, 18, 14, 18));

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
        stateLabel.setForeground(new Color(156, 163, 175));
        stateLabel.setFont(stateLabel.getFont().deriveFont(Font.BOLD, 15f));
        detailLabel.setForeground(new Color(209, 213, 219));
        labels.add(stateLabel);
        labels.add(Box.createVerticalStrut(3));
        labels.add(detailLabel);
        panel.add(labels, BorderLayout.CENTER);

        JPanel metadata = new JPanel();
        metadata.setOpaque(false);
        metadata.setLayout(new BoxLayout(metadata, BoxLayout.Y_AXIS));
        pidLabel.setForeground(new Color(209, 213, 219));
        uptimeLabel.setForeground(new Color(156, 163, 175));
        pidLabel.setAlignmentX(RIGHT_ALIGNMENT);
        uptimeLabel.setAlignmentX(RIGHT_ALIGNMENT);
        metadata.add(pidLabel);
        metadata.add(Box.createVerticalStrut(3));
        metadata.add(uptimeLabel);

        JPanel languagePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        languagePanel.setOpaque(false);
        languageLabel.setForeground(new Color(209, 213, 219));
        languageCombo.setPreferredSize(new Dimension(105, 26));
        languagePanel.add(languageLabel);
        languagePanel.add(languageCombo);

        JPanel right = new JPanel(new BorderLayout(18, 0));
        right.setOpaque(false);
        right.add(languagePanel, BorderLayout.WEST);
        right.add(metadata, BorderLayout.EAST);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private JPanel createConfigurationPanel() {
        JPanel outer = new JPanel(new BorderLayout());
        outer.setBorder(new EmptyBorder(16, 16, 16, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(configurationBorder);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(6, 8, 6, 8);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;
        addRow(form, constraints, serverNameLabel, serverNameField);
        addRow(form, constraints, widthLabel, widthCombo);
        addRow(form, constraints, heightLabel, heightCombo);
        addRow(form, constraints, fpsLabel, fpsCombo);
        addRow(form, constraints, playerLabel, playerCombo);

        constraints.gridwidth = 2;
        constraints.gridx = 0;
        form.add(startFullscreenCheck, constraints);
        constraints.gridy++;
        constraints.weighty = 1;
        constraints.anchor = GridBagConstraints.NORTH;
        baseLabel.setToolTipText(baseDirectory.toString());
        form.add(baseLabel, constraints);

        JPanel buttons = new JPanel(new GridLayout(1, 3, 7, 7));
        buttons.setBorder(new EmptyBorder(8, 7, 8, 7));
        buttons.add(startButton);
        buttons.add(stopButton);
        buttons.add(restartButton);

        JPanel displayButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        displayButtons.setBorder(displayBorder);
        displayButtons.add(fullscreenButton);
        displayButtons.add(windowedButton);

        outer.add(form, BorderLayout.CENTER);
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.add(buttons);
        footer.add(displayButtons);
        outer.add(footer, BorderLayout.SOUTH);
        return outer;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(new EmptyBorder(16, 12, 16, 16));
        JPanel heading = new JPanel(new BorderLayout());
        logTitleLabel.setFont(logTitleLabel.getFont().deriveFont(Font.BOLD));
        clearButton.addActionListener(event -> logArea.setText(""));
        heading.add(logTitleLabel, BorderLayout.WEST);
        heading.add(clearButton, BorderLayout.EAST);

        logArea.setEditable(false);
        logArea.setLineWrap(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(248, 249, 250));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(209, 213, 219)));
        panel.add(heading, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private static void addRow(
            JPanel panel,
            GridBagConstraints constraints,
            JLabel label,
            java.awt.Component component) {
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        constraints.gridx = 0;
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(component, constraints);
        constraints.gridy++;
    }

    private static <T> JComboBox<T> editableNumberCombo(List<T> candidates) {
        JComboBox<T> combo = new JComboBox<>();
        candidates.forEach(combo::addItem);
        combo.setEditable(true);
        return combo;
    }

    private void bindActions() {
        startButton.addActionListener(event -> start());
        stopButton.addActionListener(event -> stop());
        restartButton.addActionListener(event -> restart());
        fullscreenButton.addActionListener(event -> fullscreen(true));
        windowedButton.addActionListener(event -> fullscreen(false));
        playerCombo.addActionListener(event -> updateConfigAvailability());
        languageCombo.addActionListener(event -> {
            if (languageChangesEnabled) {
                changeLanguage();
            }
        });
    }

    private void bindAutoSave() {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                scheduleAutoSave();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                scheduleAutoSave();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                scheduleAutoSave();
            }
        };
        serverNameField.getDocument().addDocumentListener(listener);
        bindEditorDocument(widthCombo, listener);
        bindEditorDocument(heightCombo, listener);
        bindEditorDocument(fpsCombo, listener);
        playerCombo.addActionListener(event -> scheduleAutoSave());
        startFullscreenCheck.addActionListener(event -> scheduleAutoSave());
    }

    private static void bindEditorDocument(JComboBox<?> combo, DocumentListener listener) {
        if (combo.getEditor().getEditorComponent() instanceof JTextField editor) {
            editor.getDocument().addDocumentListener(listener);
        }
    }

    private void scheduleAutoSave() {
        if (!quitting.get()) {
            autoSaveTimer.restart();
        }
    }

    private void autoSaveConfiguration() {
        if (quitting.get()) {
            return;
        }
        try {
            configStore.saveGuiSettings(readSettings());
            autoSaveErrorShown = false;
        } catch (IllegalArgumentException ignored) {
            // Editable fields may be temporarily incomplete while the user is typing.
        } catch (IOException exception) {
            if (!autoSaveErrorShown) {
                autoSaveErrorShown = true;
                showError(LauncherMessages.Key.DIALOG_SAVE_ERROR_TITLE, exception);
            }
        }
    }

    private LauncherSettings readSettings() {
        return new LauncherSettings(
                serverNameField.getText(),
                airtunesPort,
                readEditableInteger(widthCombo, LauncherMessages.Key.WIDTH_LABEL),
                readEditableInteger(heightCombo, LauncherMessages.Key.HEIGHT_LABEL),
                readEditableInteger(fpsCombo, LauncherMessages.Key.FPS_LABEL),
                (String) playerCombo.getSelectedItem(),
                startFullscreenCheck.isSelected(),
                language);
    }

    private void applySettings(LauncherSettings settings) {
        serverNameField.setText(settings.serverName());
        airtunesPort = settings.airtunesPort();
        widthCombo.setSelectedItem(candidateLabel(settings.width(), WIDTH_CANDIDATES));
        heightCombo.setSelectedItem(candidateLabel(settings.height(), HEIGHT_CANDIDATES));
        fpsCombo.setSelectedItem(settings.fps());
        playerCombo.setSelectedItem(settings.playerImplementation());
        startFullscreenCheck.setSelected(settings.startFullscreen());
        changingLanguage = true;
        try {
            language = settings.language();
            languageCombo.setSelectedItem(language);
        } finally {
            changingLanguage = false;
        }
        updateConfigAvailability();
    }

    private int readEditableInteger(
            JComboBox<?> combo,
            LauncherMessages.Key fieldLabelKey) {
        return parseEditableInteger(combo.getEditor().getItem(), message(fieldLabelKey));
    }

    static int parseEditableInteger(Object value, String fieldName) {
        Matcher matcher = LABELED_INTEGER.matcher(String.valueOf(value));
        if (!matcher.matches()) {
            throw new LauncherInputException(
                    LauncherMessages.Key.CONFIG_INTEGER_REQUIRED, fieldName);
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            throw new LauncherInputException(
                    LauncherMessages.Key.CONFIG_INTEGER_REQUIRED, fieldName);
        }
    }

    private static String candidateLabel(int value, List<String> candidates) {
        String prefix = value + " ";
        return candidates.stream()
                .filter(candidate -> candidate.startsWith(prefix))
                .findFirst()
                .orElse(Integer.toString(value));
    }

    private void changeLanguage() {
        if (changingLanguage) {
            return;
        }
        UiLanguage selected = (UiLanguage) languageCombo.getSelectedItem();
        if (selected == null || selected == language) {
            return;
        }
        language = selected;
        AirPlayLauncher.setCurrentLanguage(language);
        applyLanguage();
        try {
            configStore.saveLanguage(language);
        } catch (IOException exception) {
            showError(LauncherMessages.Key.DIALOG_SAVE_ERROR_TITLE, exception);
        }
    }

    private void enableLanguageChanges() {
        changingLanguage = true;
        try {
            languageCombo.setSelectedItem(language);
        } finally {
            changingLanguage = false;
        }
        languageChangesEnabled = true;
    }

    private boolean saveConfiguration() {
        autoSaveTimer.stop();
        try {
            configStore.saveGuiSettings(readSettings());
            autoSaveErrorShown = false;
            receiveLocalizedLog(LauncherMessages.Key.LOG_CONFIGURATION_SAVED);
            return true;
        } catch (IllegalArgumentException | IOException exception) {
            showError(LauncherMessages.Key.DIALOG_SAVE_ERROR_TITLE, exception);
            return false;
        }
    }

    @Override
    public void start() {
        if (quitting.get()) {
            return;
        }
        if (!saveConfiguration()) {
            return;
        }
        observe(processManager.startAsync(), LauncherMessages.Key.DIALOG_START_ERROR_TITLE);
    }

    @Override
    public void stop() {
        if (quitting.get()) {
            return;
        }
        observe(processManager.stopAsync(), LauncherMessages.Key.DIALOG_STOP_ERROR_TITLE);
    }

    @Override
    public void restart() {
        if (quitting.get()) {
            return;
        }
        if (!saveConfiguration()) {
            return;
        }
        observe(processManager.restartAsync(), LauncherMessages.Key.DIALOG_RESTART_ERROR_TITLE);
    }

    @Override
    public void fullscreen(boolean fullscreen) {
        if (quitting.get()) {
            return;
        }
        processManager.setFullscreenAsync(fullscreen).whenComplete((actual, failure) -> {
            if (failure != null) {
                SwingUtilities.invokeLater(() -> showError(
                        fullscreen
                                ? LauncherMessages.Key.DIALOG_FULLSCREEN_ERROR_TITLE
                                : LauncherMessages.Key.DIALOG_WINDOWED_ERROR_TITLE,
                        unwrap(failure)));
            }
        });
    }

    @Override
    public void showWindow() {
        if (quitting.get()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
            toFront();
            requestFocus();
        });
    }

    @Override
    public void quit() {
        if (!quitting.compareAndSet(false, true)) {
            return;
        }
        flushPendingAutoSave();
        setEnabled(false);
        receiveLocalizedLog(LauncherMessages.Key.LOG_EXITING);
        processManager.stopAsync().whenComplete((ignored, failure) -> {
            processManager.close();
            if (tray != null) {
                tray.close();
            }
            SwingUtilities.invokeLater(() -> {
                dispose();
                System.exit(failure == null ? 0 : 1);
            });
        });
    }

    private void flushPendingAutoSave() {
        boolean pending = autoSaveTimer.isRunning();
        autoSaveTimer.stop();
        if (!pending) {
            return;
        }
        try {
            configStore.saveGuiSettings(readSettings());
        } catch (IllegalArgumentException | IOException ignored) {
            // Invalid partial input or an I/O failure must not prevent shutdown.
        }
    }

    void shutdownFromHook() {
        if (!quitting.compareAndSet(false, true)) {
            return;
        }
        autoSaveTimer.stop();
        try {
            processManager.stopAsync().get(6, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // The process manager already applies graceful and forced stop timeouts.
        } finally {
            processManager.close();
        }
    }

    private void hideToTray() {
        setVisible(false);
    }

    private void applyLanguage() {
        setTitle(message(LauncherMessages.Key.APPLICATION_TITLE));
        languageLabel.setText(message(LauncherMessages.Key.LANGUAGE_LABEL));
        configurationBorder.setTitle(message(LauncherMessages.Key.CONFIGURATION_SECTION));
        serverNameLabel.setText(message(LauncherMessages.Key.SERVER_NAME_LABEL));
        widthLabel.setText(message(LauncherMessages.Key.WIDTH_LABEL));
        heightLabel.setText(message(LauncherMessages.Key.HEIGHT_LABEL));
        fpsLabel.setText(message(LauncherMessages.Key.FPS_LABEL));
        playerLabel.setText(message(LauncherMessages.Key.PLAYER_LABEL));
        Path name = baseDirectory.getFileName();
        baseLabel.setText(message(
                LauncherMessages.Key.DIRECTORY_LABEL, name == null ? baseDirectory : name));
        startFullscreenCheck.setText(message(LauncherMessages.Key.START_FULLSCREEN));
        startButton.setText(message(LauncherMessages.Key.START));
        stopButton.setText(message(LauncherMessages.Key.STOP));
        restartButton.setText(message(LauncherMessages.Key.RESTART));
        displayBorder.setTitle(message(LauncherMessages.Key.DISPLAY_SECTION));
        fullscreenButton.setText(message(LauncherMessages.Key.FULLSCREEN));
        windowedButton.setText(message(LauncherMessages.Key.WINDOWED));
        logTitleLabel.setText(message(LauncherMessages.Key.RUNTIME_LOG));
        clearButton.setText(message(LauncherMessages.Key.CLEAR));
        if (tray != null) {
            tray.setLanguage(language);
        }
        applySnapshot(processManager.snapshot());
        revalidate();
        repaint();
    }

    private void receiveSnapshot(ServerProcessManager.Snapshot snapshot) {
        SwingUtilities.invokeLater(() -> applySnapshot(snapshot));
    }

    private void applySnapshot(ServerProcessManager.Snapshot snapshot) {
        LauncherStatusText.Display display = LauncherStatusText.render(language, snapshot);
        stateLabel.setText(display.state());
        stateLabel.setForeground(switch (snapshot.state()) {
            case RUNNING -> new Color(74, 222, 128);
            case STARTING, STOPPING -> new Color(251, 191, 36);
            case FAILED -> new Color(248, 113, 113);
            case STOPPED -> new Color(156, 163, 175);
        });
        detailLabel.setText(display.detail());
        pidLabel.setText(snapshot.pid() == 0 ? "PID --" : "PID " + snapshot.pid());
        uptimeLabel.setText(display.uptime());

        boolean active = snapshot.state() == ServerProcessManager.State.RUNNING
                || snapshot.state() == ServerProcessManager.State.STARTING;
        boolean changing = snapshot.state() == ServerProcessManager.State.STARTING
                || snapshot.state() == ServerProcessManager.State.STOPPING;
        startButton.setEnabled(!active && !changing);
        stopButton.setEnabled(active && !changing);
        restartButton.setEnabled(active && !changing);
        boolean fullscreenControl = snapshot.controlConnected() && snapshot.fullscreenAvailable();
        fullscreenButton.setEnabled(fullscreenControl && !snapshot.fullscreen());
        windowedButton.setEnabled(fullscreenControl && snapshot.fullscreen());
        if (tray != null) {
            tray.update(snapshot);
        }
    }

    private void updateConfigAvailability() {
        startFullscreenCheck.setEnabled("gstreamer".equals(playerCombo.getSelectedItem()));
    }

    private void receiveLog(ServerProcessManager.LogEntry entry) {
        UiLanguage logLanguage = language;
        if (entry.type() == ServerProcessManager.LogType.RAW) {
            receiveLog(String.valueOf(entry.argument1()));
            return;
        }
        LauncherMessages.Key key = switch (entry.type()) {
            case PROCESS_STARTED -> LauncherMessages.Key.LOG_PROCESS_STARTED;
            case QUIT_ACCEPTED -> LauncherMessages.Key.LOG_QUIT_ACCEPTED;
            case QUIT_FALLBACK -> LauncherMessages.Key.LOG_QUIT_FALLBACK;
            case SWITCHED_FULLSCREEN -> LauncherMessages.Key.LOG_SWITCHED_FULLSCREEN;
            case SWITCHED_WINDOWED -> LauncherMessages.Key.LOG_SWITCHED_WINDOWED;
            case CONTROL_DISCONNECTED -> LauncherMessages.Key.LOG_CONTROL_DISCONNECTED;
            case LOG_READ_FAILED -> LauncherMessages.Key.LOG_SERVER_OUTPUT_READ_FAILED;
            case PROCESS_EXITED -> LauncherMessages.Key.LOG_PROCESS_EXITED;
            case LISTENER_FAILED -> LauncherMessages.Key.LOG_STATE_LISTENER_FAILED;
            case RAW -> throw new IllegalStateException("RAW log entry was not handled");
        };
        receiveLog(LauncherMessages.text(
                logLanguage,
                key,
                localizeArgument(logLanguage, entry.argument1()),
                localizeArgument(logLanguage, entry.argument2())));
    }

    private void receiveLocalizedLog(LauncherMessages.Key key, Object... arguments) {
        receiveLog(message(key, arguments));
    }

    private void receiveLog(String message) {
        if (pendingLogLines.incrementAndGet() > MAX_PENDING_LOG_LINES) {
            pendingLogLines.decrementAndGet();
            droppedLogLines.incrementAndGet();
        } else {
            pendingLogs.add("[" + LocalTime.now().format(LOG_TIME) + "] "
                    + message + System.lineSeparator());
        }
        scheduleLogFlush();
    }

    private void scheduleLogFlush() {
        if (logFlushScheduled.compareAndSet(false, true)) {
            SwingUtilities.invokeLater(this::flushLogs);
        }
    }

    private void flushLogs() {
        StringBuilder batch = new StringBuilder();
        int dropped = droppedLogLines.getAndSet(0);
        if (dropped > 0) {
            batch.append('[').append(LocalTime.now().format(LOG_TIME)).append("] ")
                    .append(message(LauncherMessages.Key.LOG_EXCESS_DROPPED, dropped))
                    .append(System.lineSeparator());
        }
        for (int count = 0; count < LOG_FLUSH_BATCH_SIZE; count++) {
            String line = pendingLogs.poll();
            if (line == null) {
                break;
            }
            pendingLogLines.decrementAndGet();
            batch.append(line);
        }

        if (!batch.isEmpty()) {
            logArea.append(batch.toString());
            int excess = logArea.getDocument().getLength() - MAX_LOG_CHARACTERS;
            if (excess > 0) {
                try {
                    logArea.getDocument().remove(0, excess);
                } catch (javax.swing.text.BadLocationException ignored) {
                    logArea.setText("");
                }
            }
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }

        logFlushScheduled.set(false);
        if (!pendingLogs.isEmpty() || droppedLogLines.get() > 0) {
            scheduleLogFlush();
        }
    }

    private void observe(
            java.util.concurrent.CompletableFuture<Void> future,
            LauncherMessages.Key titleKey) {
        future.whenComplete((ignored, failure) -> {
            if (failure != null) {
                SwingUtilities.invokeLater(() -> showError(titleKey, unwrap(failure)));
            }
        });
    }

    private void showError(LauncherMessages.Key titleKey, Throwable failure) {
        String title = message(titleKey);
        String failureMessage = LauncherMessages.failureText(language, failure);
        receiveLog(title + ": " + failureMessage);
        JOptionPane.showMessageDialog(this, failureMessage, title, JOptionPane.ERROR_MESSAGE);
    }

    private String message(LauncherMessages.Key key, Object... arguments) {
        return LauncherMessages.text(language, key, arguments);
    }

    private Object localizeArgument(UiLanguage targetLanguage, Object argument) {
        return argument instanceof Throwable failure
                ? LauncherMessages.failureText(targetLanguage, failure)
                : argument;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
