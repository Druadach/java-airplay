package com.github.serezhka.airplay.app.lifecycle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public final class ApplicationShutdown {
    public static final long DEFAULT_TIMEOUT_MILLIS = 500;
    private static final Logger log = LoggerFactory.getLogger(ApplicationShutdown.class);

    private final ApplicationContext applicationContext;
    private final AtomicBoolean quitStarted = new AtomicBoolean();

    public ApplicationShutdown(ApplicationContext applicationContext) {
        this.applicationContext = Objects.requireNonNull(applicationContext, "applicationContext");
    }

    public Thread request(Runnable cleanup) {
        return quitAsync(
                quitStarted,
                cleanup,
                () -> SpringApplication.exit(applicationContext, () -> 0),
                System::exit,
                code -> Runtime.getRuntime().halt(code),
                DEFAULT_TIMEOUT_MILLIS);
    }

    public static Thread quitAsync(
            AtomicBoolean quitStarted,
            Runnable cleanup,
            IntSupplier applicationExit,
            IntConsumer processExit,
            IntConsumer forcedExit,
            long timeoutMillis) {
        Objects.requireNonNull(quitStarted, "quitStarted");
        Objects.requireNonNull(cleanup, "cleanup");
        Objects.requireNonNull(applicationExit, "applicationExit");
        Objects.requireNonNull(processExit, "processExit");
        Objects.requireNonNull(forcedExit, "forcedExit");
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be positive");
        }
        if (!quitStarted.compareAndSet(false, true)) {
            return null;
        }

        log.info("Application quit requested");
        CountDownLatch cleanupStart = new CountDownLatch(1);

        Thread shutdownThread = new Thread(() -> {
            try {
                cleanupStart.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }

            try {
                cleanup.run();
            } catch (Throwable exception) {
                log.warn("Unable to run application shutdown cleanup", exception);
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
