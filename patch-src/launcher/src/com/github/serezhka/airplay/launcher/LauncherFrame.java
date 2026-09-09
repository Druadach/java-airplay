package com.github.serezhka.airplay.launcher;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
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
import java.net.URI;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
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
    static final List<Integer> WIDTH_CANDIDATES = List.of(1280, 1920, 2560, 3840);
    static final List<Integer> HEIGHT_CANDIDATES = List.of(720, 1080, 1440, 2160);
    static final List<Integer> FPS_CANDIDATES = List.of(24, 30, 60);

    private final ConfigStore configStore;
    private final ServerProcessManager processManager;
    private final Path baseDirectory;
    private final AppVersion currentVersion;
    private final GitHubUpdateChecker updateChecker = new GitHubUpdateChecker();
    private final AtomicBoolean quitting = new AtomicBoolean();
    private final AtomicBoolean logFlushScheduled = new AtomicBoolean();
    private final AtomicInteger pendingLogLines = new AtomicInteger();
    private final AtomicInteger droppedLogLines = new AtomicInteger();
    private final ConcurrentLinkedQueue<String> pendingLogs = new ConcurrentLinkedQueue<>();

    private volatile UiLanguage language = UiLanguage.systemDefault();
    private boolean changingLanguage;
    private boolean languageChangesEnabled;
    private boolean checkingUpdates;
    private boolean downloadingUpdate;
    private boolean installingUpdate;
    private UpdateDownloader.Cancellation updateCancellation;
    private final JLabel stateLabel = new JLabel();
    private final JLabel detailLabel = new JLabel();
    private final JLabel pidLabel = new JLabel("PID --");
    private final JLabel uptimeLabel = new JLabel();
    private final JLabel languageLabel = new JLabel();
    private final JComboBox<UiLanguage> languageCombo = new JComboBox<>(UiLanguage.values());
    private final JTextField serverNameField = new JTextField();
    private final JComboBox<ResolutionPreset> resolutionCombo = new JComboBox<>(ResolutionPreset.values());
    private final JComboBox<Integer> widthCombo = editableNumberCombo(WIDTH_CANDIDATES);
    private final JComboBox<Integer> heightCombo = editableNumberCombo(HEIGHT_CANDIDATES);
    private final JComboBox<Integer> fpsCombo = editableNumberCombo(FPS_CANDIDATES);
    private final JComboBox<PlayerOption> playerCombo = new JComboBox<>(PlayerOption.values());
    private final JToggleButton advancedToggle = new JToggleButton();
    private final JToggleButton logsToggle = new JToggleButton();
    private final JButton restoreDefaultsButton = new JButton();
    private final JPanel advancedPanel = new JPanel(new GridBagLayout());
    private final JLabel videoWarningLabel = new JLabel();
    private final JLabel playerHintLabel = new JLabel();
    private final JLabel playerDependencyLabel = new JLabel();
    private final JLabel configurationStatusLabel = new JLabel();
    private final JLabel versionLabel = new JLabel();
    private final JButton checkUpdatesButton = new JButton();
    private final JCheckBox startFullscreenCheck = new JCheckBox();
    private final JCheckBox autoStartCheck = new JCheckBox();
    private final JCheckBox autoRunServiceCheck = new JCheckBox();
    private final JCheckBox startMinimizedCheck = new JCheckBox();
    private final JCheckBox closeToTrayCheck = new JCheckBox();
    private final JButton startButton = new JButton();
    private final JButton stopButton = new JButton();
    private final JButton restartButton = new JButton();
    private final JButton fullscreenButton = new JButton();
    private final JButton windowedButton = new JButton();
    private final JButton clearButton = new JButton();
    private final JLabel serverNameLabel = new JLabel();
    private final JLabel resolutionLabel = new JLabel();
    private final JLabel widthLabel = new JLabel();
    private final JLabel heightLabel = new JLabel();
    private final JLabel fpsLabel = new JLabel();
    private final JLabel playerLabel = new JLabel();
    private final JLabel logTitleLabel = new JLabel();
    private final TitledBorder configurationBorder = BorderFactory.createTitledBorder("");
    private final TitledBorder displayBorder = BorderFactory.createTitledBorder("");
    private final JTextArea logArea = new JTextArea();
    private CollapsibleLogPane logPane;
    private final LauncherTray tray;
    private final Timer autoSaveTimer;
    private LauncherSettings savedSettings;
    private IOException saveFailure;
    private boolean updatingSettings;
    private boolean autoStartUpdating;
    private boolean serviceActionPending;
    private int playerCheckGeneration;
    private LauncherMessages.Key playerCheckKey;
    private Object playerCheckArgument;
    private int airtunesPort = 5001;

    LauncherFrame(Path baseDirectory, boolean awaitingUpdateCommit) throws IOException {
        super("AirPlay Receiver");
        installingUpdate = awaitingUpdateCommit;
        setEnabled(!awaitingUpdateCommit);
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
        configStore = new ConfigStore(this.baseDirectory.resolve("application.properties"));
        LauncherSettings settings = configStore.load();
        savedSettings = settings;
        language = settings.language();
        AirPlayLauncher.setCurrentLanguage(language);
        currentVersion = AppVersion.current();
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
                if (closesToTray(closeToTrayCheck.isSelected(), tray != null)) {
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

        logPane = new CollapsibleLogPane(createConfigurationPanel(), createLogPanel());
        root.add(logPane, BorderLayout.CENTER);
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
        languageCombo.setPreferredSize(new Dimension(languageCombo.getPreferredSize().width, 26));
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
        GridBagConstraints constraints = formConstraints(new Insets(6, 8, 6, 8));
        installLabels(resolutionCombo, preset -> preset.label(language));
        installLabels(playerCombo, player -> player.label(language));
        addRow(form, constraints, serverNameLabel, serverNameField);
        addRow(form, constraints, resolutionLabel, resolutionCombo);
        addRow(form, constraints, widthLabel, widthCombo);
        addRow(form, constraints, heightLabel, heightCombo);
        addRow(form, constraints, fpsLabel, fpsCombo);
        videoWarningLabel.setForeground(new Color(153, 92, 0));
        addWideRow(form, constraints, videoWarningLabel);
        addWideRow(form, constraints, startFullscreenCheck);
        addWideRow(form, constraints, autoStartCheck);
        addWideRow(form, constraints, autoRunServiceCheck);
        addWideRow(form, constraints, startMinimizedCheck);
        addWideRow(form, constraints, closeToTrayCheck);

        JPanel preferences = new JPanel(new GridLayout(1, 2, 7, 0));
        preferences.add(advancedToggle);
        preferences.add(restoreDefaultsButton);
        addWideRow(form, constraints, preferences);

        GridBagConstraints advancedConstraints = formConstraints(new Insets(4, 0, 4, 0));
        addRow(advancedPanel, advancedConstraints, playerLabel, playerCombo);
        playerHintLabel.setForeground(new Color(90, 99, 110));
        addWideRow(advancedPanel, advancedConstraints, playerHintLabel);
        addWideRow(advancedPanel, advancedConstraints, playerDependencyLabel);
        addWideRow(form, constraints, advancedPanel);
        constraints.weighty = 1;
        form.add(Box.createVerticalGlue(), constraints);

        JPanel buttons = createServiceButtons(startButton, stopButton, restartButton);

        JPanel displayButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        displayButtons.setBorder(displayBorder);
        displayButtons.add(fullscreenButton);
        displayButtons.add(windowedButton);

        JScrollPane settingsScroll = new JScrollPane(form);
        settingsScroll.setBorder(null);
        settingsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        settingsScroll.getVerticalScrollBar().setUnitIncrement(16);
        outer.add(settingsScroll, BorderLayout.CENTER);
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        configurationStatusLabel.setBorder(new EmptyBorder(6, 7, 4, 7));
        configurationStatusLabel.setAlignmentX(LEFT_ALIGNMENT);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        displayButtons.setAlignmentX(LEFT_ALIGNMENT);
        footer.add(configurationStatusLabel);
        footer.add(buttons);
        footer.add(displayButtons);
        JPanel updates = createVersionPanel(versionLabel, checkUpdatesButton);
        updates.setAlignmentX(LEFT_ALIGNMENT);
        footer.add(updates);
        JPanel logsControl = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        logsControl.setBorder(new EmptyBorder(8, 0, 0, 0));
        logsControl.setAlignmentX(LEFT_ALIGNMENT);
        logsControl.add(logsToggle);
        footer.add(logsControl);
        outer.add(footer, BorderLayout.SOUTH);
        return outer;
    }

    static JPanel createServiceButtons(JButton startButton, JButton stopButton, JButton restartButton) {
        JPanel panel = new JPanel(new GridLayout(1, 3, 7, 7));
        panel.setBorder(new EmptyBorder(8, 7, 8, 7));
        panel.add(startButton);
        panel.add(stopButton);
        panel.add(restartButton);
        return panel;
    }

    static JPanel createVersionPanel(JLabel versionLabel, JButton checkUpdatesButton) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(new EmptyBorder(8, 7, 0, 7));
        versionLabel.setForeground(new Color(90, 99, 110));
        panel.add(versionLabel, BorderLayout.CENTER);
        panel.add(checkUpdatesButton, BorderLayout.EAST);
        return panel;
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

    static GridBagConstraints formConstraints(Insets insets) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = insets;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        constraints.gridx = 0;
        constraints.gridy = 0;
        return constraints;
    }

    static void addRow(
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

    static void addWideRow(
            JPanel panel, GridBagConstraints constraints, java.awt.Component component) {
        constraints.gridwidth = 2;
        constraints.gridx = 0;
        constraints.weightx = 1;
        panel.add(component, constraints);
        constraints.gridy++;
    }

    private static <T> void installLabels(JComboBox<T> combo, Function<T, String> labelText) {
        DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        combo.setRenderer((list, value, index, selected, focused) -> {
            JLabel label = (JLabel) renderer.getListCellRendererComponent(list, value, index, selected, focused);
            label.setText(value == null ? "" : labelText.apply(value));
            return label;
        });
    }

    private void bindActions() {
        startButton.addActionListener(event -> start());
        stopButton.addActionListener(event -> stop());
        restartButton.addActionListener(event -> restart());
        fullscreenButton.addActionListener(event -> toggleFullscreen(true));
        windowedButton.addActionListener(event -> toggleFullscreen(false));
        resolutionCombo.addActionListener(event -> selectResolution());
        playerCombo.addActionListener(event -> {
            if (!updatingSettings) {
                updateConfigAvailability();
                checkSelectedPlayer();
            }
        });
        advancedToggle.addActionListener(event -> {
            advancedPanel.setVisible(advancedToggle.isSelected());
            revalidate();
        });
        logsToggle.addActionListener(event -> {
            logPane.setExpanded(logsToggle.isSelected());
            refreshLogToggle();
        });
        restoreDefaultsButton.addActionListener(event -> restoreDefaults());
        checkUpdatesButton.addActionListener(event -> checkUpdates());
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
        autoStartCheck.addActionListener(event -> {
            applyAutoStartRegistry(!autoStartCheck.isSelected());
        });
        autoRunServiceCheck.addActionListener(event -> scheduleAutoSave());
        startMinimizedCheck.addActionListener(event -> scheduleAutoSave());
        closeToTrayCheck.addActionListener(event -> scheduleAutoSave());
    }

    private static void bindEditorDocument(JComboBox<?> combo, DocumentListener listener) {
        if (combo.getEditor().getEditorComponent() instanceof JTextField editor) {
            editor.getDocument().addDocumentListener(listener);
        }
    }

    private void scheduleAutoSave() {
        if (!quitting.get() && !updatingSettings) {
            saveFailure = null;
            autoSaveTimer.restart();
            updateConfigurationStatus();
        }
    }

    private void autoSaveConfiguration() {
        if (quitting.get() || autoStartUpdating) {
            return;
        }
        try {
            LauncherSettings settings = readSettings();
            configStore.saveGuiSettings(settings);
            savedSettings = settings;
            saveFailure = null;
        } catch (IllegalArgumentException ignored) {
        } catch (IOException exception) {
            saveFailure = exception;
        }
        updateConfigurationStatus();
    }

    private LauncherSettings readSettings() {
        return new LauncherSettings(
                serverNameField.getText(),
                airtunesPort,
                readEditableInteger(widthCombo, LauncherMessages.Key.WIDTH_LABEL),
                readEditableInteger(heightCombo, LauncherMessages.Key.HEIGHT_LABEL),
                readEditableInteger(fpsCombo, LauncherMessages.Key.FPS_LABEL),
                ((PlayerOption) playerCombo.getSelectedItem()).implementation(),
                startFullscreenCheck.isSelected(),
                autoStartCheck.isSelected(),
                autoRunServiceCheck.isSelected(),
                startMinimizedCheck.isSelected(),
                closeToTrayCheck.isSelected(),
                language);
    }

    private void applySettings(LauncherSettings settings) {
        updatingSettings = true;
        changingLanguage = true;
        try {
            serverNameField.setText(settings.serverName());
            airtunesPort = settings.airtunesPort();
            resolutionCombo.setSelectedItem(ResolutionPreset.forSize(settings.width(), settings.height()));
            widthCombo.setSelectedItem(settings.width());
            heightCombo.setSelectedItem(settings.height());
            fpsCombo.setSelectedItem(settings.fps());
            playerCombo.setSelectedItem(PlayerOption.fromImplementation(settings.playerImplementation()));
            startFullscreenCheck.setSelected(settings.startFullscreen());
            autoStartCheck.setSelected(settings.autoStartEnabled());
            autoRunServiceCheck.setSelected(settings.autoRunService());
            startMinimizedCheck.setSelected(settings.startMinimized());
            closeToTrayCheck.setSelected(settings.closeToTray());
            advancedToggle.setSelected(playerCombo.getSelectedItem() != PlayerOption.GSTREAMER);
            advancedPanel.setVisible(advancedToggle.isSelected());
            language = settings.language();
            languageCombo.setSelectedItem(language);
        } finally {
            changingLanguage = false;
            updatingSettings = false;
        }
        updateConfigAvailability();
        checkSelectedPlayer();
    }

    private void selectResolution() {
        if (updatingSettings) {
            return;
        }
        ResolutionPreset preset = (ResolutionPreset) resolutionCombo.getSelectedItem();
        if (preset != null && preset != ResolutionPreset.CUSTOM) {
            updatingSettings = true;
            try {
                widthCombo.setSelectedItem(preset.width());
                heightCombo.setSelectedItem(preset.height());
            } finally {
                updatingSettings = false;
            }
        }
        updateConfigAvailability();
        scheduleAutoSave();
    }

    private void restoreDefaults() {
        if (autoStartUpdating || serviceActionPending || quitting.get()) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                message(LauncherMessages.Key.DIALOG_RESET_MESSAGE),
                message(LauncherMessages.Key.DIALOG_RESET_TITLE),
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        autoSaveTimer.stop();
        boolean previousAutoStart = autoStartCheck.isSelected();
        applySettings(LauncherSettings.defaults(airtunesPort));
        AirPlayLauncher.setCurrentLanguage(language);
        applyLanguage();
        applyAutoStartRegistry(previousAutoStart);
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
            savedSettings = savedSettings.withLanguage(language);
            saveFailure = null;
        } catch (IOException exception) {
            saveFailure = exception;
            showError(LauncherMessages.Key.DIALOG_SAVE_ERROR_TITLE, exception);
        }
        updateConfigurationStatus();
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
        if (autoStartUpdating) {
            showError(LauncherMessages.Key.DIALOG_SAVE_ERROR_TITLE,
                    new LauncherIOException(LauncherMessages.Key.ERROR_AUTOSTART_PENDING));
            return false;
        }
        try {
            LauncherSettings settings = readSettings();
            configStore.saveGuiSettings(settings);
            savedSettings = settings;
            saveFailure = null;
            updateConfigurationStatus();
            receiveLocalizedLog(LauncherMessages.Key.LOG_CONFIGURATION_SAVED);
            return true;
        } catch (IllegalArgumentException | IOException exception) {
            if (exception instanceof IOException ioFailure) {
                saveFailure = ioFailure;
            }
            updateConfigurationStatus();
            showError(LauncherMessages.Key.DIALOG_SAVE_ERROR_TITLE, exception);
            return false;
        }
    }

    @Override
    public void start() {
        if (quitting.get() || installingUpdate || serviceActionPending) {
            return;
        }
        if (!saveConfiguration()) {
            return;
        }
        serviceActionPending = true;
        applySnapshot(processManager.snapshot());
        observeServiceAction(processManager.startAsync(), LauncherMessages.Key.DIALOG_START_ERROR_TITLE);
    }

    @Override
    public void stop() {
        if (quitting.get() || installingUpdate || serviceActionPending) {
            return;
        }
        serviceActionPending = true;
        applySnapshot(processManager.snapshot());
        observeServiceAction(processManager.stopAsync(), LauncherMessages.Key.DIALOG_STOP_ERROR_TITLE);
    }

    @Override
    public void toggleFullscreen(boolean fullscreen) {
        if (quitting.get() || installingUpdate) {
            return;
        }
        processManager.setFullscreenAsync(fullscreen).whenComplete((actual, failure) ->
                SwingUtilities.invokeLater(() -> {
                    applySnapshot(processManager.snapshot());
                    if (failure != null) {
                        showError(fullscreen
                                ? LauncherMessages.Key.DIALOG_FULLSCREEN_ERROR_TITLE
                                : LauncherMessages.Key.DIALOG_WINDOWED_ERROR_TITLE, unwrap(failure));
                    }
                }));
    }

    private void restart() {
        if (!quitting.get() && !installingUpdate && !serviceActionPending && saveConfiguration()) {
            serviceActionPending = true;
            applySnapshot(processManager.snapshot());
            observeServiceAction(processManager.restartAsync(), LauncherMessages.Key.DIALOG_RESTART_ERROR_TITLE);
        }
    }

    @Override
    public void settings() {
        showWindow();
        SwingUtilities.invokeLater(serverNameField::requestFocusInWindow);
    }

    @Override
    public void about() {
        if (quitting.get()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            String title = LauncherMessages.text(language, LauncherMessages.Key.APPLICATION_TITLE);
            String message = message(LauncherMessages.Key.ABOUT_MESSAGE, currentVersion);
            JOptionPane.showMessageDialog(this, message, title, JOptionPane.INFORMATION_MESSAGE);
        });
    }

    @Override
    public void checkUpdates() {
        SwingUtilities.invokeLater(() -> {
            if (quitting.get() || checkingUpdates || downloadingUpdate || installingUpdate) {
                return;
            }
            checkingUpdates = true;
            refreshUpdateControls();
            CompletableFuture.supplyAsync(() -> {
                try {
                    return updateChecker.check(currentVersion);
                } catch (IOException exception) {
                    throw new CompletionException(exception);
                }
            }).whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
                if (quitting.get()) {
                    checkingUpdates = false;
                    return;
                }
                try {
                    showUpdateResult(result, failure == null ? null : unwrap(failure));
                } finally {
                    checkingUpdates = false;
                    refreshUpdateControls();
                }
            }));
        });
    }

    private void showUpdateResult(GitHubUpdateChecker.Result result, Throwable failure) {
        LauncherMessages.Key title;
        String text;
        URI releasePage;
        int messageType;
        if (failure != null) {
            title = LauncherMessages.Key.UPDATE_FAILED_TITLE;
            String explanation = LauncherMessages.failureText(language, failure);
            text = message(LauncherMessages.Key.UPDATE_FAILED_MESSAGE, explanation);
            releasePage = GitHubUpdateChecker.RELEASES_PAGE;
            messageType = JOptionPane.WARNING_MESSAGE;
            receiveLog(message(title) + ": " + explanation);
        } else {
            boolean available = result.updateAvailable();
            title = available ? LauncherMessages.Key.UPDATE_AVAILABLE_TITLE : LauncherMessages.Key.UPDATE_CURRENT_TITLE;
            text = message(available ? LauncherMessages.Key.UPDATE_AVAILABLE_MESSAGE
                    : LauncherMessages.Key.UPDATE_CURRENT_MESSAGE, result.currentVersion(), result.latestVersion());
            releasePage = result.releasePage();
            messageType = JOptionPane.INFORMATION_MESSAGE;
        }
        boolean automatic = failure == null && result.automaticUpdateAvailable() && UpdateInstaller.supported();
        if (failure == null && result.updateAvailable() && !automatic) {
            text += message(LauncherMessages.Key.UPDATE_MANUAL_ONLY);
        }
        String[] options = automatic
                ? new String[]{message(LauncherMessages.Key.UPDATE_AUTOMATIC),
                message(LauncherMessages.Key.UPDATE_OPEN_RELEASE), message(LauncherMessages.Key.UPDATE_CLOSE)}
                : new String[]{message(LauncherMessages.Key.UPDATE_OPEN_RELEASE), message(LauncherMessages.Key.UPDATE_CLOSE)};
        int selection = JOptionPane.showOptionDialog(this, text, message(title), JOptionPane.DEFAULT_OPTION,
                messageType, null, options, options[options.length - 1]);
        if (automatic && selection == 0) {
            downloadUpdate(result);
        } else if (selection == (automatic ? 1 : 0)) {
            openReleasePage(releasePage);
        }
    }

    private void downloadUpdate(GitHubUpdateChecker.Result release) {
        if (quitting.get() || downloadingUpdate || installingUpdate) {
            return;
        }
        downloadingUpdate = true;
        UpdateDownloader.Cancellation cancellation = new UpdateDownloader.Cancellation();
        updateCancellation = cancellation;
        refreshUpdateControls();
        JDialog dialog = new JDialog(this, message(LauncherMessages.Key.UPDATE_DOWNLOAD_TITLE), true);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        JTextArea description = new JTextArea(4, 44);
        description.setEditable(false);
        description.setOpaque(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setText(message(LauncherMessages.Key.UPDATE_DOWNLOADING, "0.0",
                String.format(Locale.ROOT, "%.1f", release.asset().size() / 1_048_576.0)));
        JButton cancel = new JButton(message(LauncherMessages.Key.UPDATE_CANCEL));
        Runnable cancelDownload = () -> {
            cancellation.cancel();
            cancel.setEnabled(false);
            cancel.setText(message(LauncherMessages.Key.UPDATE_CANCELLING));
        };
        cancel.addActionListener(event -> cancelDownload.run());
        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                cancelDownload.run();
            }
        });
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setBorder(new EmptyBorder(16, 16, 16, 16));
        panel.add(description, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(cancel);
        panel.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);
        CompletableFuture.supplyAsync(() -> {
            try {
                return new UpdateInstaller(baseDirectory).prepare(release, cancellation, progress ->
                        SwingUtilities.invokeLater(() -> {
                            if (cancellation.isCancelled()) {
                                return;
                            }
                            progressBar.setIndeterminate(progress.preparing());
                            if (progress.preparing()) {
                                progressBar.setString("");
                                description.setText(message(LauncherMessages.Key.UPDATE_PREPARING));
                            } else {
                                progressBar.setValue((int) (progress.completed() * 100 / progress.total()));
                                description.setText(message(LauncherMessages.Key.UPDATE_DOWNLOADING,
                                        String.format(Locale.ROOT, "%.1f", progress.completed() / 1_048_576.0),
                                        String.format(Locale.ROOT, "%.1f", progress.total() / 1_048_576.0)));
                            }
                        }));
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).whenComplete((prepared, failure) -> SwingUtilities.invokeLater(() -> {
            dialog.dispose();
            downloadingUpdate = false;
            updateCancellation = null;
            refreshUpdateControls();
            if (quitting.get() || cancellation.isCancelled()) {
                discardUpdate(prepared);
                return;
            }
            if (failure != null) {
                showError(LauncherMessages.Key.UPDATE_INSTALL_ERROR_TITLE, unwrap(failure));
                return;
            }
            String[] options = {message(LauncherMessages.Key.UPDATE_RESTART), message(LauncherMessages.Key.UPDATE_NOT_NOW)};
            int selection = JOptionPane.showOptionDialog(this,
                    message(LauncherMessages.Key.UPDATE_READY_MESSAGE, prepared.version()),
                    message(LauncherMessages.Key.UPDATE_READY_TITLE), JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, options, options[1]);
            if (selection == 0) {
                installUpdate(prepared);
            } else {
                discardUpdate(prepared);
            }
        }));
        dialog.setVisible(true);
    }

    private void installUpdate(UpdateInstaller.Prepared prepared) {
        if (quitting.get() || installingUpdate || serviceActionPending || autoStartUpdating) {
            discardUpdate(prepared);
            showError(LauncherMessages.Key.UPDATE_INSTALL_ERROR_TITLE,
                    new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_BUSY));
            return;
        }
        if (!saveConfiguration()) {
            discardUpdate(prepared);
            return;
        }
        boolean resumeService = processManager.isProcessRunning();
        installingUpdate = true;
        setEnabled(false);
        refreshUpdateControls();
        CompletableFuture.supplyAsync(() -> {
            try {
                return UpdateInstaller.launchHelper(prepared, language.resolved(), resumeService);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }).whenComplete((pending, failure) -> SwingUtilities.invokeLater(() -> {
            if (failure != null) {
                discardUpdate(prepared);
                updateInstallationFailed(null, unwrap(failure));
                return;
            }
            serviceActionPending = true;
            processManager.stopAsync().whenComplete((ignored, stopFailure) -> SwingUtilities.invokeLater(() -> {
                serviceActionPending = false;
                if (stopFailure != null) {
                    updateInstallationFailed(pending, unwrap(stopFailure));
                    return;
                }
                if (processManager.isProcessRunning()) {
                    updateInstallationFailed(pending,
                            new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_SERVICE_STOP));
                    return;
                }
                try {
                    pending.applyAfterExit();
                } catch (IOException exception) {
                    updateInstallationFailed(pending, exception);
                    return;
                }
                quitting.set(true);
                autoSaveTimer.stop();
                processManager.close();
                if (tray != null) {
                    tray.close();
                }
                dispose();
                System.exit(0);
            }));
        }));
    }

    private void updateInstallationFailed(UpdateInstaller.Pending pending, Throwable failure) {
        if (pending != null) {
            try {
                pending.cancel();
            } catch (IOException cancelFailure) {
                failure.addSuppressed(cancelFailure);
            }
        }
        installingUpdate = false;
        setEnabled(true);
        refreshUpdateControls();
        applySnapshot(processManager.snapshot());
        showError(LauncherMessages.Key.UPDATE_INSTALL_ERROR_TITLE, failure);
    }

    private void discardUpdate(UpdateInstaller.Prepared prepared) {
        if (prepared == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                prepared.close();
            } catch (IOException exception) {
                receiveLog(LauncherMessages.failureText(language, exception));
            }
        });
    }

    private void openReleasePage(URI releasePage) {
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new IOException("Opening a browser is not supported");
            }
            Desktop.getDesktop().browse(releasePage);
        } catch (IOException | RuntimeException exception) {
            String title = message(LauncherMessages.Key.UPDATE_BROWSER_ERROR_TITLE);
            receiveLog(title + ": " + LauncherMessages.failureText(language, exception));
            JTextField link = new JTextField(releasePage.toString());
            link.setEditable(false);
            JOptionPane.showMessageDialog(this,
                    new Object[]{message(LauncherMessages.Key.ERROR_OPEN_RELEASE), link}, title,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshUpdateControls() {
        versionLabel.setText(message(LauncherMessages.Key.CURRENT_VERSION, currentVersion));
        boolean updating = downloadingUpdate || installingUpdate;
        checkUpdatesButton.setText(message(updating ? LauncherMessages.Key.UPDATE_WORKING
                : checkingUpdates ? LauncherMessages.Key.CHECKING_UPDATES : LauncherMessages.Key.CHECK_UPDATES));
        checkUpdatesButton.setToolTipText(hintHtml(message(LauncherMessages.Key.CHECK_UPDATES_HINT)));
        checkUpdatesButton.setEnabled(!checkingUpdates && !updating && !quitting.get());
        if (tray != null) {
            tray.setUpdateActivity(checkingUpdates, updating);
        }
    }

    @Override
    public void showWindow() {
        if (quitting.get() || installingUpdate) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            setVisible(true);
            setExtendedState(JFrame.NORMAL);
            toFront();
            requestFocus();
        });
    }

    void showAtStartup(boolean minimized, boolean automaticLaunch) {
        setVisible(!startsInTray(startMinimizedCheck.isSelected(), minimized, automaticLaunch, tray != null));
    }

    void completeUpdateStartup() {
        installingUpdate = false;
        setEnabled(true);
        refreshUpdateControls();
    }

    static boolean startsInTray(
            boolean preference, boolean minimizedArgument, boolean automaticLaunch, boolean trayAvailable) {
        return trayAvailable && (preference || (minimizedArgument && !automaticLaunch));
    }

    static boolean closesToTray(boolean preference, boolean trayAvailable) {
        return preference && trayAvailable;
    }

    void startServiceAtStartup(boolean updateRelaunch, boolean resumeService) {
        SwingUtilities.invokeLater(() -> {
            try {
                LauncherSettings settings = configStore.load();
                if (updateRelaunch ? resumeService : settings.autoRunService()) {
                    start();
                }
            } catch (Exception exception) {
                receiveLocalizedLog(LauncherMessages.Key.LOG_AUTO_RUN_FAILED, exception);
            }
        });
    }

    @Override
    public void quit() {
        if (installingUpdate) {
            return;
        }
        if (autoStartUpdating) {
            showError(LauncherMessages.Key.DIALOG_SAVE_ERROR_TITLE,
                    new LauncherIOException(LauncherMessages.Key.ERROR_AUTOSTART_PENDING));
            return;
        }
        if (!quitting.compareAndSet(false, true)) {
            return;
        }
        if (updateCancellation != null) {
            updateCancellation.cancel();
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
        languageLabel.setToolTipText(hintHtml(message(LauncherMessages.Key.LANGUAGE_HINT)));
        languageCombo.setToolTipText(hintHtml(message(LauncherMessages.Key.LANGUAGE_HINT)));
        configurationBorder.setTitle(message(LauncherMessages.Key.CONFIGURATION_SECTION));
        serverNameLabel.setText(message(LauncherMessages.Key.SERVER_NAME_LABEL));
        serverNameLabel.setToolTipText(hintHtml(message(LauncherMessages.Key.SERVER_NAME_HINT)));
        serverNameField.setToolTipText(hintHtml(message(LauncherMessages.Key.SERVER_NAME_HINT)));
        resolutionLabel.setText(message(LauncherMessages.Key.RESOLUTION_LABEL));
        widthLabel.setText(message(LauncherMessages.Key.WIDTH_LABEL));
        heightLabel.setText(message(LauncherMessages.Key.HEIGHT_LABEL));
        fpsLabel.setText(message(LauncherMessages.Key.FPS_LABEL));
        playerLabel.setText(message(LauncherMessages.Key.PLAYER_LABEL));
        advancedToggle.setText(message(LauncherMessages.Key.ADVANCED_SETTINGS));
        restoreDefaultsButton.setText(message(LauncherMessages.Key.RESTORE_DEFAULTS));
        videoWarningLabel.setText(hintHtml(message(LauncherMessages.Key.VIDEO_EXPERIMENTAL_HINT)));
        resolutionCombo.repaint();
        playerCombo.repaint();
        refreshPlayerText();
        startFullscreenCheck.setText(message(LauncherMessages.Key.START_FULLSCREEN));
        startFullscreenCheck.setToolTipText(hintHtml(message(LauncherMessages.Key.START_FULLSCREEN_HINT)));
        autoStartCheck.setText(message(LauncherMessages.Key.AUTO_START_LABEL));
        autoStartCheck.setToolTipText(hintHtml(message(LauncherMessages.Key.AUTO_START_HINT)));
        autoRunServiceCheck.setText(message(LauncherMessages.Key.AUTO_START_AND_RUN));
        autoRunServiceCheck.setToolTipText(hintHtml(message(LauncherMessages.Key.AUTO_START_AND_RUN_HINT)));
        startMinimizedCheck.setText(message(LauncherMessages.Key.START_MINIMIZED));
        startMinimizedCheck.setToolTipText(hintHtml(message(LauncherMessages.Key.START_MINIMIZED_HINT)));
        closeToTrayCheck.setText(message(LauncherMessages.Key.CLOSE_TO_TRAY));
        closeToTrayCheck.setToolTipText(hintHtml(message(LauncherMessages.Key.CLOSE_TO_TRAY_HINT)));
        startButton.setText(message(LauncherMessages.Key.START));
        startButton.setToolTipText(hintHtml(message(LauncherMessages.Key.START_HINT)));
        stopButton.setText(message(LauncherMessages.Key.STOP));
        stopButton.setToolTipText(hintHtml(message(LauncherMessages.Key.STOP_HINT)));
        restartButton.setText(message(LauncherMessages.Key.RESTART));
        restartButton.setToolTipText(hintHtml(message(LauncherMessages.Key.RESTART_TOOLTIP)));
        displayBorder.setTitle(message(LauncherMessages.Key.DISPLAY_SECTION));
        fullscreenButton.setText(message(LauncherMessages.Key.FULLSCREEN));
        windowedButton.setText(message(LauncherMessages.Key.WINDOWED));
        logTitleLabel.setText(message(LauncherMessages.Key.RUNTIME_LOG));
        refreshLogToggle();
        clearButton.setText(message(LauncherMessages.Key.CLEAR));
        if (tray != null) {
            tray.setLanguage(language);
        }
        refreshUpdateControls();
        applySnapshot(processManager.snapshot());
        revalidate();
        repaint();
    }

    private void refreshLogToggle() {
        logsToggle.setText(message(logsToggle.isSelected()
                ? LauncherMessages.Key.COLLAPSE_RUNTIME_LOG : LauncherMessages.Key.EXPAND_RUNTIME_LOG));
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
        boolean changing = serviceActionPending || snapshot.state() == ServerProcessManager.State.STARTING
                || snapshot.state() == ServerProcessManager.State.STOPPING;
        startButton.setEnabled(!active && !changing && !autoStartUpdating);
        stopButton.setEnabled(active && !changing);
        restartButton.setEnabled(active && !changing && !autoStartUpdating);
        boolean fullscreenControl = snapshot.controlConnected() && snapshot.fullscreenAvailable();
        fullscreenButton.setEnabled(fullscreenControl && !snapshot.fullscreen());
        windowedButton.setEnabled(fullscreenControl && snapshot.fullscreen());
        if (tray != null) {
            tray.update(snapshot);
        }
        updateConfigAvailability();
        updateConfigurationStatus();
    }

    private void updateConfigAvailability() {
        ServerProcessManager.State state = processManager.snapshot().state();
        boolean changing = serviceActionPending || autoStartUpdating || state == ServerProcessManager.State.STARTING
                || state == ServerProcessManager.State.STOPPING;
        boolean custom = resolutionCombo.getSelectedItem() == ResolutionPreset.CUSTOM;
        widthLabel.setVisible(custom);
        widthCombo.setVisible(custom);
        heightLabel.setVisible(custom);
        heightCombo.setVisible(custom);
        serverNameField.setEnabled(!changing);
        resolutionCombo.setEnabled(!changing);
        widthCombo.setEnabled(!changing);
        heightCombo.setEnabled(!changing);
        fpsCombo.setEnabled(!changing);
        playerCombo.setEnabled(!changing);
        startFullscreenCheck.setEnabled(!changing && playerCombo.getSelectedItem() == PlayerOption.GSTREAMER);
        autoStartCheck.setEnabled(!autoStartUpdating);
        restoreDefaultsButton.setEnabled(!changing && !autoStartUpdating);
        revalidate();
    }

    private void updateConfigurationStatus() {
        restartButton.setText(message(LauncherMessages.Key.RESTART));
        try {
            LauncherSettings settings = readSettings();
            boolean restartRequired = processManager.snapshot().state() == ServerProcessManager.State.RUNNING
                    && !settings.sameServiceConfiguration(processManager.activeSettings());
            videoWarningLabel.setVisible(settings.unverifiedVideoMode());
            if (restartRequired) {
                restartButton.setText(message(LauncherMessages.Key.RESTART_AND_APPLY));
            }
            if (autoStartUpdating) {
                setConfigurationStatus(LauncherMessages.Key.CONFIG_STATUS_AUTOSTART, new Color(90, 99, 110));
            } else if (saveFailure != null) {
                setConfigurationStatus(LauncherMessages.Key.CONFIG_STATUS_SAVE_FAILED, new Color(180, 40, 40),
                        LauncherMessages.failureText(language, saveFailure));
            } else if (!settings.equals(savedSettings)) {
                setConfigurationStatus(LauncherMessages.Key.CONFIG_STATUS_SAVING, new Color(90, 99, 110));
            } else if (restartRequired) {
                setConfigurationStatus(LauncherMessages.Key.CONFIG_STATUS_RESTART_REQUIRED, new Color(153, 92, 0));
            } else {
                setConfigurationStatus(LauncherMessages.Key.CONFIG_STATUS_SAVED, new Color(46, 125, 50));
            }
        } catch (IllegalArgumentException exception) {
            videoWarningLabel.setVisible(false);
            setConfigurationStatus(LauncherMessages.Key.CONFIG_STATUS_INVALID, new Color(180, 40, 40),
                    LauncherMessages.failureText(language, exception));
        }
    }

    private void setConfigurationStatus(LauncherMessages.Key key, Color color, Object... arguments) {
        configurationStatusLabel.setForeground(color);
        configurationStatusLabel.setText(hintHtml(message(key, arguments)));
    }

    private static String hintHtml(String text) {
        return "<html><body style='width: 230px'>"
                + text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                + "</body></html>";
    }

    private void checkSelectedPlayer() {
        PlayerOption player = (PlayerOption) playerCombo.getSelectedItem();
        int generation = ++playerCheckGeneration;
        playerDependencyLabel.setToolTipText(null);
        playerCheckKey = player.executable() == null ? null : LauncherMessages.Key.PLAYER_CHECKING;
        playerCheckArgument = player.executable();
        refreshPlayerText();
        if (player.executable() == null) {
            return;
        }
        CompletableFuture.supplyAsync(() -> player.findExecutable(baseDirectory, System.getenv("PATH")))
                .whenComplete((executable, failure) -> SwingUtilities.invokeLater(() -> {
                    if (generation != playerCheckGeneration || quitting.get()) {
                        return;
                    }
                    if (failure != null) {
                        playerCheckKey = LauncherMessages.Key.PLAYER_CHECK_FAILED;
                        playerCheckArgument = unwrap(failure);
                    } else {
                        playerCheckKey = executable == null
                                ? LauncherMessages.Key.PLAYER_MISSING : LauncherMessages.Key.PLAYER_AVAILABLE;
                        playerCheckArgument = player.executable();
                        playerDependencyLabel.setToolTipText(executable == null ? null : executable.toString());
                    }
                    refreshPlayerText();
                }));
    }

    private void refreshPlayerText() {
        PlayerOption player = (PlayerOption) playerCombo.getSelectedItem();
        playerHintLabel.setText(hintHtml(player.hint(language)));
        playerDependencyLabel.setVisible(playerCheckKey != null);
        if (playerCheckKey != null) {
            Object argument = localizeArgument(language, playerCheckArgument);
            playerDependencyLabel.setText(hintHtml(message(playerCheckKey, argument)));
            playerDependencyLabel.setForeground(playerCheckKey == LauncherMessages.Key.PLAYER_AVAILABLE
                    ? new Color(46, 125, 50) : new Color(153, 92, 0));
        }
        revalidate();
    }

    private void applyAutoStartRegistry(boolean previousValue) {
        try {
            readSettings();
        } catch (IllegalArgumentException exception) {
            autoStartCheck.setSelected(previousValue);
            updateConfigurationStatus();
            return;
        }
        boolean enabled = autoStartCheck.isSelected();
        autoStartUpdating = true;
        autoSaveTimer.stop();
        applySnapshot(processManager.snapshot());
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Path launcherJar = baseDirectory.resolve("java-airplay-launcher.jar");
                Path executable = baseDirectory.resolve("AirPlayReceiver.exe");
                Path targetPath = java.nio.file.Files.isRegularFile(executable) ? executable : launcherJar;
                if (enabled) {
                    AutoStartManager.enable(targetPath);
                } else {
                    AutoStartManager.disable();
                }
            } catch (IOException exception) {
                throw new CompletionException(exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new CompletionException(exception);
            }
        }).whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            autoStartUpdating = false;
            if (failure != null) {
                autoStartCheck.setSelected(previousValue);
                showError(LauncherMessages.Key.DIALOG_SAVE_ERROR_TITLE, unwrap(failure));
            }
            autoSaveConfiguration();
            applySnapshot(processManager.snapshot());
        }));
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

    private void observeServiceAction(
            java.util.concurrent.CompletableFuture<Void> future,
            LauncherMessages.Key titleKey) {
        future.whenComplete((ignored, failure) -> SwingUtilities.invokeLater(() -> {
            serviceActionPending = false;
            applySnapshot(processManager.snapshot());
            if (failure != null) {
                showError(titleKey, unwrap(failure));
            }
        }));
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
