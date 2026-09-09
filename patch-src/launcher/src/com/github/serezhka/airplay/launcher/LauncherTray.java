package com.github.serezhka.airplay.launcher;

import java.awt.BasicStroke;
import java.awt.CheckboxMenuItem;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;

final class LauncherTray implements AutoCloseable {
    interface Actions {
        void showWindow();

        void start();

        void stop();

        void toggleFullscreen(boolean fullscreen);

        void settings();

        void about();

        void checkUpdates();

        void quit();
    }

    private final TrayIcon trayIcon;
    private final MenuItem showItem = new MenuItem();
    private final MenuItem serviceItem = new MenuItem();
    private final CheckboxMenuItem fullscreenItem = new CheckboxMenuItem();
    private final MenuItem settingsItem = new MenuItem();
    private final MenuItem checkUpdatesItem = new MenuItem();
    private final MenuItem aboutItem = new MenuItem();
    private final MenuItem quitItem = new MenuItem();
    private UiLanguage language;
    private ServerProcessManager.Snapshot snapshot = ServerProcessManager.Snapshot.stopped();
    private boolean updatingFullscreenState = false;
    private boolean checkingUpdates;
    private boolean updating;

    private LauncherTray(Actions actions, UiLanguage language) throws Exception {
        PopupMenu menu = new PopupMenu();
        showItem.addActionListener(event -> actions.showWindow());
        serviceItem.addActionListener(event -> {
            if (canStartService()) {
                actions.start();
            } else {
                actions.stop();
            }
        });
        fullscreenItem.addItemListener(event -> {
            if (!updatingFullscreenState) {
                boolean fullscreen = event.getStateChange() == ItemEvent.SELECTED;
                actions.toggleFullscreen(fullscreen);
            }
        });
        settingsItem.addActionListener(event -> actions.settings());
        checkUpdatesItem.addActionListener(event -> actions.checkUpdates());
        aboutItem.addActionListener(event -> actions.about());
        quitItem.addActionListener(event -> actions.quit());

        menu.add(showItem);
        menu.addSeparator();
        menu.add(serviceItem);
        menu.addSeparator();
        menu.add(fullscreenItem);
        menu.add(settingsItem);
        menu.addSeparator();
        menu.add(checkUpdatesItem);
        menu.add(aboutItem);
        menu.add(quitItem);
        applyMenuFont(menu);

        trayIcon = new TrayIcon(applicationIcon(), "", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() >= 2) {
                    actions.showWindow();
                }
            }
        });
        setLanguage(language);
        SystemTray.getSystemTray().add(trayIcon);
        update(snapshot);
    }

    static LauncherTray install(Actions actions) {
        return install(actions, UiLanguage.systemDefault());
    }

    static LauncherTray install(Actions actions, UiLanguage language) {
        if (!SystemTray.isSupported()) {
            return null;
        }
        try {
            return new LauncherTray(actions, language);
        } catch (Exception exception) {
            return null;
        }
    }

    void update(ServerProcessManager.Snapshot snapshot) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        boolean changing = snapshot.state() == ServerProcessManager.State.STARTING
                || snapshot.state() == ServerProcessManager.State.STOPPING;
        Labels labels = labels(language, snapshot);
        serviceItem.setLabel(canStartService() ? labels.start() : labels.stop());
        serviceItem.setEnabled(!changing);

        // Fullscreen checkbox
        boolean fullscreenAvailable = snapshot.controlConnected() && snapshot.fullscreenAvailable();
        fullscreenItem.setEnabled(fullscreenAvailable);
        updatingFullscreenState = true;
        try {
            fullscreenItem.setState(snapshot.fullscreen());
        } finally {
            updatingFullscreenState = false;
        }

        trayIcon.setToolTip(labels(language, snapshot).tooltip());
    }

    void setLanguage(UiLanguage language) {
        this.language = Objects.requireNonNull(language, "language");
        Labels labels = labels(language, snapshot);
        showItem.setLabel(labels.open());
        serviceItem.setLabel(canStartService() ? labels.start() : labels.stop());
        fullscreenItem.setLabel(labels.fullscreen());
        settingsItem.setLabel(labels.settings());
        setUpdateActivity(checkingUpdates, updating);
        aboutItem.setLabel(labels.about());
        quitItem.setLabel(labels.exit());
        trayIcon.setToolTip(labels.tooltip());
    }

    void setUpdateActivity(boolean checkingUpdates, boolean updating) {
        this.checkingUpdates = checkingUpdates;
        this.updating = updating;
        checkUpdatesItem.setEnabled(!checkingUpdates && !updating);
        checkUpdatesItem.setLabel(LauncherMessages.text(language, updating ? LauncherMessages.Key.UPDATE_WORKING
                : checkingUpdates ? LauncherMessages.Key.CHECKING_UPDATES : LauncherMessages.Key.CHECK_UPDATES));
    }

    static Labels labels(UiLanguage language, ServerProcessManager.Snapshot snapshot) {
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(snapshot, "snapshot");
        LauncherMessages.Key stateKey = switch (snapshot.state()) {
            case STOPPED -> LauncherMessages.Key.STATE_STOPPED;
            case STARTING -> LauncherMessages.Key.STATE_STARTING;
            case RUNNING -> LauncherMessages.Key.STATE_RUNNING;
            case STOPPING -> LauncherMessages.Key.STATE_STOPPING;
            case FAILED -> LauncherMessages.Key.STATE_FAILED;
        };
        return new Labels(
                LauncherMessages.text(language, LauncherMessages.Key.TRAY_OPEN),
                LauncherMessages.text(language, LauncherMessages.Key.TRAY_START),
                LauncherMessages.text(language, LauncherMessages.Key.TRAY_STOP),
                LauncherMessages.text(language, LauncherMessages.Key.FULLSCREEN),
                LauncherMessages.text(language, LauncherMessages.Key.TRAY_SETTINGS),
                LauncherMessages.text(language, LauncherMessages.Key.CHECK_UPDATES),
                LauncherMessages.text(language, LauncherMessages.Key.TRAY_ABOUT),
                LauncherMessages.text(language, LauncherMessages.Key.TRAY_EXIT),
                LauncherMessages.text(language, LauncherMessages.Key.APPLICATION_TITLE)
                        + " - " + LauncherMessages.text(language, stateKey));
    }

    record Labels(
            String open,
            String start,
            String stop,
            String fullscreen,
            String settings,
            String checkUpdates,
            String about,
            String exit,
            String tooltip) {
    }

    private boolean canStartService() {
        return snapshot.state() == ServerProcessManager.State.STOPPED
                || snapshot.state() == ServerProcessManager.State.FAILED;
    }

    private void applyMenuFont(PopupMenu menu) {
        Font font = unicodeMenuFont();
        menu.setFont(font);
        for (MenuItem item : List.of(
                showItem,
                serviceItem,
                settingsItem,
                checkUpdatesItem,
                aboutItem,
                quitItem)) {
            item.setFont(font);
        }
        fullscreenItem.setFont(font);
    }

    private static Font unicodeMenuFont() {
        String sample = "显示主窗口启动停止服务全屏设置检查更新关于退出";
        // Try system fonts that support CJK
        for (String family : List.of(
                "Microsoft YaHei UI",
                "Microsoft YaHei",
                "SimSun",
                "NSimSun",
                "SimHei",
                Font.DIALOG)) {
            Font font = new Font(family, Font.PLAIN, 12);
            if (font.canDisplayUpTo(sample) < 0) {
                return font;
            }
        }
        // Fallback: use default with explicit encoding support
        return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    static Image applicationIcon() {
        var resource = LauncherTray.class.getResource("/menu/tray_icon.png");
        if (resource != null) {
            try {
                BufferedImage image = ImageIO.read(resource);
                if (image != null) {
                    return image;
                }
            } catch (IOException ignored) {
                // Fall back to the generated icon below.
            }
        }
        return createFallbackIcon();
    }

    private static Image createFallbackIcon() {
        int size = 32;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(32, 40, 43));
            graphics.fillRoundRect(1, 1, 30, 30, 7, 7);
            graphics.setColor(new Color(74, 222, 128));
            graphics.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.drawArc(7, 7, 18, 18, 45, 90);
            graphics.drawArc(10, 10, 12, 12, 45, 90);
            graphics.fillOval(14, 14, 4, 4);
            graphics.drawLine(9, 24, 23, 24);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    @Override
    public void close() {
        SystemTray.getSystemTray().remove(trayIcon);
    }
}
