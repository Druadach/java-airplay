package com.github.serezhka.airplay.app.menu;

import com.github.serezhka.airplay.app.lifecycle.ApplicationShutdown;
import com.github.serezhka.airplay.player.gstreamer.FullscreenController;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import dorkbox.systemTray.Checkbox;
import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class SystemTrayMenu {
    private static final Logger log = LoggerFactory.getLogger(SystemTrayMenu.class);
    public static final long QUIT_TIMEOUT_MILLIS = ApplicationShutdown.DEFAULT_TIMEOUT_MILLIS;

    public SystemTrayMenu(ApplicationShutdown applicationShutdown, AirPlayConsumer airPlayConsumer) {
        SystemTray systemTray = SystemTray.get();
        if (systemTray == null) {
            log.warn("Unable to load SystemTray!");
            return;
        }

        systemTray.installShutdownHook();
        systemTray.setImage(Objects.requireNonNull(getClass().getResource("/menu/tray_icon.png")));
        if (airPlayConsumer instanceof FullscreenController fullscreenController) {
            systemTray.getMenu().add(createFullscreenCheckbox(fullscreenController));
        }
        systemTray.getMenu().add(new MenuItem(
                "Quit", event -> applicationShutdown.request(systemTray::shutdown)));
    }

    static Checkbox createFullscreenCheckbox(FullscreenController fullscreenController) {
        Objects.requireNonNull(fullscreenController, "fullscreenController");
        Checkbox fullscreenCheckbox = new Checkbox("Fullscreen");
        fullscreenController.addFullscreenListener(fullscreenCheckbox::setChecked);
        fullscreenCheckbox.setCallback(event -> {
            Checkbox item = (Checkbox) event.getSource();
            boolean fullscreen = item.getChecked();
            boolean previousFullscreen = fullscreenController.isFullscreen();
            try {
                fullscreenController.setFullscreen(fullscreen);
                log.info("GStreamer fullscreen mode changed to {}", fullscreen);
            } catch (Throwable exception) {
                item.setChecked(previousFullscreen);
                log.error("Unable to change GStreamer fullscreen mode", exception);
            }
        });
        return fullscreenCheckbox;
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
