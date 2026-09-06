package com.github.serezhka.airplay.app.menu;

import com.github.serezhka.airplay.app.lifecycle.ApplicationShutdown;
import com.github.serezhka.airplay.player.gstreamer.FullscreenController;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import javax.imageio.ImageIO;

public class SystemTrayMenu {
    private static final Logger log = LoggerFactory.getLogger(SystemTrayMenu.class);
    public static final long QUIT_TIMEOUT_MILLIS = ApplicationShutdown.DEFAULT_TIMEOUT_MILLIS;

    private final java.awt.SystemTray systemTray;
    private final TrayIcon trayIcon;

    public SystemTrayMenu(ApplicationShutdown applicationShutdown, AirPlayConsumer airPlayConsumer) {
        Objects.requireNonNull(applicationShutdown, "applicationShutdown");
        if (!java.awt.SystemTray.isSupported()) {
            log.warn("System tray is not supported on this platform");
            systemTray = null;
            trayIcon = null;
            return;
        }

        systemTray = java.awt.SystemTray.getSystemTray();

        // Load tray icon image
        Image image = loadTrayIcon();
        if (image == null) {
            log.warn("Unable to load tray icon image");
            trayIcon = null;
            return;
        }

        // Create popup menu
        PopupMenu popup = new PopupMenu();

        // Add fullscreen checkbox if available
        if (airPlayConsumer instanceof FullscreenController fullscreenController) {
            CheckboxMenuItem fullscreenItem = createFullscreenCheckbox(fullscreenController);
            popup.add(fullscreenItem);
            popup.addSeparator();
        }

        // Add quit menu item
        MenuItem quitItem = new MenuItem("Quit");
        quitItem.addActionListener(e -> applicationShutdown.request(this::shutdown));
        popup.add(quitItem);

        // Create tray icon
        trayIcon = new TrayIcon(image, "AirPlay Receiver", popup);
        trayIcon.setImageAutoSize(true);

        // Add to system tray
        try {
            systemTray.add(trayIcon);
            log.info("System tray icon added successfully");
        } catch (AWTException e) {
            log.error("Unable to add system tray icon", e);
        }
    }

    private Image loadTrayIcon() {
        try (InputStream stream = Objects.requireNonNull(
                getClass().getResourceAsStream("/menu/tray_icon.png"))) {
            BufferedImage icon = ImageIO.read(stream);

            // Scale to tray icon size
            Dimension trayIconSize = java.awt.SystemTray.getSystemTray().getTrayIconSize();
            return icon.getScaledInstance(trayIconSize.width, trayIconSize.height, Image.SCALE_SMOOTH);
        } catch (Exception e) {
            log.error("Failed to load tray icon", e);
            return null;
        }
    }

    static CheckboxMenuItem createFullscreenCheckbox(FullscreenController fullscreenController) {
        Objects.requireNonNull(fullscreenController, "fullscreenController");
        CheckboxMenuItem fullscreenItem = new CheckboxMenuItem(
                "Fullscreen", fullscreenController.isFullscreen());
        fullscreenController.addFullscreenListener(fullscreen -> {
            if (EventQueue.isDispatchThread()) {
                fullscreenItem.setState(fullscreen);
            } else {
                EventQueue.invokeLater(() -> fullscreenItem.setState(fullscreen));
            }
        });

        // AWT setState does not emit ItemEvents; only user actions reach this listener.
        fullscreenItem.addItemListener(e -> {
            boolean fullscreen = fullscreenItem.getState();
            boolean previousFullscreen = fullscreenController.isFullscreen();
            try {
                fullscreenController.setFullscreen(fullscreen);
                log.info("GStreamer fullscreen mode changed to {}", fullscreen);
            } catch (Throwable exception) {
                fullscreenItem.setState(previousFullscreen);
                log.error("Unable to change GStreamer fullscreen mode", exception);
            }
        });

        return fullscreenItem;
    }

    public void shutdown() {
        if (systemTray != null && trayIcon != null) {
            systemTray.remove(trayIcon);
            log.info("System tray icon removed");
        }
    }

    public static Thread quitAsync(
            AtomicBoolean quitStarted,
            Runnable trayShutdown,
            IntSupplier applicationExit,
            IntConsumer processExit,
            IntConsumer forcedExit,
            long timeoutMillis) {
        return ApplicationShutdown.quitAsync(
                quitStarted,
                trayShutdown,
                applicationExit,
                processExit,
                forcedExit,
                timeoutMillis);
    }
}
