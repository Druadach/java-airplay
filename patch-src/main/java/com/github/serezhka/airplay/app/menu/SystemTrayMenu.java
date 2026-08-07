package com.github.serezhka.airplay.app.menu;

import dorkbox.systemTray.MenuItem;
import dorkbox.systemTray.SystemTray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class SystemTrayMenu {
    private static final Logger log = LoggerFactory.getLogger(SystemTrayMenu.class);
    static final long QUIT_TIMEOUT_MILLIS = 500;
    private final AtomicBoolean quitStarted = new AtomicBoolean();

    public SystemTrayMenu(ApplicationContext context) {
        SystemTray systemTray = SystemTray.get();
        if (systemTray == null) {
            log.warn("Unable to load SystemTray!");
            return;
        }

        systemTray.installShutdownHook();
        systemTray.setImage(Objects.requireNonNull(getClass().getResource("/menu/tray_icon.png")));
        systemTray.getMenu().add(new MenuItem("Quit", event -> quitAsync(
                    quitStarted,
                    systemTray::shutdown,
                    () -> SpringApplication.exit(context, () -> 0),
                    System::exit,
                    code -> Runtime.getRuntime().halt(code),
                    QUIT_TIMEOUT_MILLIS)));
    }

    static Thread quitAsync(
            AtomicBoolean quitStarted,
            Runnable trayShutdown,
            IntSupplier applicationExit,
            IntConsumer processExit,
            IntConsumer forcedExit,
            long timeoutMillis) {
        Objects.requireNonNull(quitStarted, "quitStarted");
        Objects.requireNonNull(trayShutdown, "trayShutdown");
        Objects.requireNonNull(applicationExit, "applicationExit");
        Objects.requireNonNull(processExit, "processExit");
        Objects.requireNonNull(forcedExit, "forcedExit");
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        if (!quitStarted.compareAndSet(false, true)) {
            return null;
        }

        log.info("Quit requested from the system tray");
        CountDownLatch cleanupStart = new CountDownLatch(1);

        Thread shutdownThread = new Thread(() -> {
            try {
                cleanupStart.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }

            try {
                trayShutdown.run();
            } catch (Throwable exception) {
                log.warn("Unable to remove the system tray icon during shutdown", exception);
            }

            try {
                applicationExit.getAsInt();
            } catch (Throwable exception) {
                log.error("Graceful application shutdown failed", exception);
            }

            try {
                processExit.accept(0);
            } catch (Throwable exception) {
                log.error("Normal process exit failed; forcing termination", exception);
                forcedExit.accept(0);
            }
        }, "airplay-shutdown");
        shutdownThread.setDaemon(false);

        Thread watchdogThread = new Thread(() -> {
            cleanupStart.countDown();
            try {
                shutdownThread.join(timeoutMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }

            if (shutdownThread.isAlive()) {
                log.warn("Graceful shutdown exceeded {} ms; forcing termination", timeoutMillis);
                forcedExit.accept(0);
            }
        }, "airplay-shutdown-watchdog");
        watchdogThread.setDaemon(true);

        shutdownThread.start();
        watchdogThread.start();
        return shutdownThread;
    }
}
