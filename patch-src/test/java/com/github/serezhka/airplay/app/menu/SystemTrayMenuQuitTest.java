package com.github.serezhka.airplay.app.menu;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class SystemTrayMenuQuitTest {
    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            runProcessProbe(args[0]);
            return;
        }

        gracefulQuitRunsOffTheMenuEventThread();
        cleanupFailuresCannotPreventProcessExit();
        blockingTrayCleanupIsGuardedAndQuitIsIdempotent();
        watchdogForcesExitWhenCleanupBlocks();
        productionQuitTimeoutRemainsResponsive();
        System.out.println("System tray quit tests passed");
    }

    private static void runProcessProbe(String mode) {
        boolean blockCleanup;
        long timeoutMillis;
        if ("normal-process-probe".equals(mode)) {
            blockCleanup = false;
            timeoutMillis = 200;
        } else if ("blocked-process-probe".equals(mode)) {
            blockCleanup = true;
            timeoutMillis = 200;
        } else if ("production-timeout-process-probe".equals(mode)) {
            blockCleanup = true;
            timeoutMillis = SystemTrayMenu.QUIT_TIMEOUT_MILLIS;
        } else {
            throw new IllegalArgumentException("Unknown process probe: " + mode);
        }

        Thread failureGuard = new Thread(() -> {
            try {
            Thread.sleep(10_000);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            Runtime.getRuntime().halt(99);
        }, "tray-quit-test-failure-guard");
        failureGuard.setDaemon(false);
        failureGuard.start();

        CountDownLatch neverReleased = new CountDownLatch(1);
        SystemTrayMenu.quitAsync(
                new AtomicBoolean(),
                () -> {
                },
                () -> {
                    if (blockCleanup) {
                        awaitUninterruptibly(neverReleased);
                    }
                    return 0;
                },
                System::exit,
                code -> Runtime.getRuntime().halt(code),
                timeoutMillis);
        awaitUninterruptibly(neverReleased);
    }

    private static void gracefulQuitRunsOffTheMenuEventThread() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        CountDownLatch processExit = new CountDownLatch(1);
        AtomicInteger forcedExits = new AtomicInteger();

        long startedAt = System.nanoTime();
        Thread shutdownThread = SystemTrayMenu.quitAsync(
                new AtomicBoolean(),
                () -> events.add("tray"),
                () -> {
                    events.add("application");
                    return 0;
                },
                code -> {
                    events.add("process:" + code);
                    processExit.countDown();
                },
                code -> forcedExits.incrementAndGet(),
                1_000);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertTrue(elapsedMillis < 500, "quit callback should return promptly");
        assertTrue(processExit.await(2, TimeUnit.SECONDS), "normal process exit was not requested");
        shutdownThread.join(2_000);
        Thread.sleep(50);
        assertEquals(List.of("tray", "application", "process:0"), events, "shutdown order");
        assertEquals(0, forcedExits.get(), "forced exit count");
        assertTrue(!shutdownThread.isDaemon(), "shutdown worker must keep running until exit is requested");
    }

    private static void cleanupFailuresCannotPreventProcessExit() throws Exception {
        CountDownLatch processExit = new CountDownLatch(1);
        AtomicInteger forcedExits = new AtomicInteger();

        Thread shutdownThread = SystemTrayMenu.quitAsync(
                new AtomicBoolean(),
                () -> {
                    throw new IllegalStateException("tray failure");
                },
                () -> {
                    throw new IllegalStateException("application failure");
                },
                code -> processExit.countDown(),
                code -> forcedExits.incrementAndGet(),
                1_000);

        assertTrue(processExit.await(2, TimeUnit.SECONDS), "cleanup failure prevented process exit");
        shutdownThread.join(2_000);
        Thread.sleep(50);
        assertEquals(0, forcedExits.get(), "forced exit count after cleanup failure");
    }

    private static void blockingTrayCleanupIsGuardedAndQuitIsIdempotent() throws Exception {
        AtomicBoolean quitStarted = new AtomicBoolean();
        CountDownLatch trayCleanupStarted = new CountDownLatch(1);
        CountDownLatch releaseTrayCleanup = new CountDownLatch(1);
        CountDownLatch forcedExit = new CountDownLatch(1);
        AtomicInteger duplicateCallbacks = new AtomicInteger();

        Thread shutdownThread = SystemTrayMenu.quitAsync(
                quitStarted,
                () -> {
                    trayCleanupStarted.countDown();
                    awaitUninterruptibly(releaseTrayCleanup);
                },
                () -> 0,
                code -> {
                },
                code -> forcedExit.countDown(),
                100);
        assertTrue(trayCleanupStarted.await(1, TimeUnit.SECONDS), "tray cleanup did not start");

        Thread duplicateThread = SystemTrayMenu.quitAsync(
                quitStarted,
                duplicateCallbacks::incrementAndGet,
                () -> {
                    duplicateCallbacks.incrementAndGet();
                    return 0;
                },
                code -> duplicateCallbacks.incrementAndGet(),
                code -> duplicateCallbacks.incrementAndGet(),
                100);

        assertTrue(duplicateThread == null, "duplicate quit worker should not be created");
        assertEquals(0, duplicateCallbacks.get(), "duplicate quit callbacks");
        assertTrue(forcedExit.await(2, TimeUnit.SECONDS), "blocking tray cleanup was not forced");
        releaseTrayCleanup.countDown();
        shutdownThread.join(2_000);
    }

    private static void watchdogForcesExitWhenCleanupBlocks() throws Exception {
        CountDownLatch cleanupStarted = new CountDownLatch(1);
        CountDownLatch releaseCleanup = new CountDownLatch(1);
        CountDownLatch forcedExit = new CountDownLatch(1);

        Thread shutdownThread = SystemTrayMenu.quitAsync(
                new AtomicBoolean(),
                () -> {
                },
                () -> {
                    cleanupStarted.countDown();
                    awaitUninterruptibly(releaseCleanup);
                    return 0;
                },
                code -> {
                },
                code -> forcedExit.countDown(),
                100);

        assertTrue(cleanupStarted.await(1, TimeUnit.SECONDS), "application cleanup did not start");
        assertTrue(forcedExit.await(2, TimeUnit.SECONDS), "watchdog did not force process exit");
        releaseCleanup.countDown();
        shutdownThread.join(2_000);
    }

    private static void productionQuitTimeoutRemainsResponsive() {
        assertTrue(SystemTrayMenu.QUIT_TIMEOUT_MILLIS <= 500,
                "production quit timeout must remain at most 500 ms");
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void assertTrue(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }
}
