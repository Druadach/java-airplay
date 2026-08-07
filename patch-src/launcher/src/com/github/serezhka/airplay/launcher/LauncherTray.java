package com.github.serezhka.airplay.launcher;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
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

        void restart();

        void fullscreen(boolean fullscreen);

        void quit();
    }

    private final TrayIcon trayIcon;
    private final MenuItem showItem = new MenuItem();
    private final MenuItem startItem = new MenuItem();
    private final MenuItem stopItem = new MenuItem();
    private final MenuItem restartItem = new MenuItem();
    private final MenuItem fullscreenItem = new MenuItem();
    private final MenuItem windowedItem = new MenuItem();
    private final MenuItem quitItem = new MenuItem();
    private UiLanguage language;
    private ServerProcessManager.Snapshot snapshot = ServerProcessManager.Snapshot.stopped();

    private LauncherTray(Actions actions, UiLanguage language) throws Exception {
        PopupMenu menu = new PopupMenu();
        showItem.addActionListener(event -> actions.showWindow());
        startItem.addActionListener(event -> actions.start());
        stopItem.addActionListener(event -> actions.stop());
        restartItem.addActionListener(event -> actions.restart());
        fullscreenItem.addActionListener(event -> actions.fullscreen(true));
        windowedItem.addActionListener(event -> actions.fullscreen(false));
        quitItem.addActionListener(event -> actions.quit());

        menu.add(showItem);
        menu.addSeparator();
        menu.add(startItem);
        menu.add(stopItem);
        menu.add(restartItem);
        menu.addSeparator();
        menu.add(fullscreenItem);
        menu.add(windowedItem);
        menu.addSeparator();
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
        boolean active = snapshot.state() == ServerProcessManager.State.RUNNING
                || snapshot.state() == ServerProcessManager.State.STARTING;
        boolean changing = snapshot.state() == ServerProcessManager.State.STARTING
                || snapshot.state() == ServerProcessManager.State.STOPPING;
        startItem.setEnabled(!active && !changing);
        stopItem.setEnabled(active && !changing);
        restartItem.setEnabled(active && !changing);
        fullscreenItem.setEnabled(snapshot.controlConnected() && snapshot.fullscreenAvailable()
                && !snapshot.fullscreen());
        windowedItem.setEnabled(snapshot.controlConnected() && snapshot.fullscreenAvailable()
                && snapshot.fullscreen());
        trayIcon.setToolTip(labels(language, snapshot).tooltip());
    }

    void setLanguage(UiLanguage language) {
        this.language = Objects.requireNonNull(language, "language");
        Labels labels = labels(language, snapshot);
        showItem.setLabel(labels.open());
        startItem.setLabel(labels.start());
        stopItem.setLabel(labels.stop());
        restartItem.setLabel(labels.restart());
        fullscreenItem.setLabel(labels.fullscreen());
        windowedItem.setLabel(labels.windowed());
        quitItem.setLabel(labels.exit());
        trayIcon.setToolTip(labels.tooltip());
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
                LauncherMessages.text(language, LauncherMessages.Key.START),
                LauncherMessages.text(language, LauncherMessages.Key.STOP),
                LauncherMessages.text(language, LauncherMessages.Key.RESTART),
                LauncherMessages.text(language, LauncherMessages.Key.FULLSCREEN),
                LauncherMessages.text(language, LauncherMessages.Key.WINDOWED),
                LauncherMessages.text(language, LauncherMessages.Key.TRAY_EXIT),
                LauncherMessages.text(language, LauncherMessages.Key.APPLICATION_TITLE)
                        + " - " + LauncherMessages.text(language, stateKey));
    }

    record Labels(
            String open,
            String start,
            String stop,
            String restart,
            String fullscreen,
            String windowed,
            String exit,
            String tooltip) {
    }

    private void applyMenuFont(PopupMenu menu) {
        Font font = unicodeMenuFont();
        menu.setFont(font);
        for (MenuItem item : List.of(
                showItem,
                startItem,
                stopItem,
                restartItem,
                fullscreenItem,
                windowedItem,
                quitItem)) {
            item.setFont(font);
        }
    }

    private static Font unicodeMenuFont() {
        String sample = "打开启动停止重启全屏窗口模式退出";
        for (String family : List.of("Microsoft YaHei UI", "Microsoft YaHei", Font.DIALOG)) {
            Font font = new Font(family, Font.PLAIN, 12);
            if (font.canDisplayUpTo(sample) < 0) {
                return font;
            }
        }
        return new Font(Font.DIALOG, Font.PLAIN, 12);
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
