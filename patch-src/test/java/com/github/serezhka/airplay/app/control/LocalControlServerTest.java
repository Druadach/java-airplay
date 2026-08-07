package com.github.serezhka.airplay.app.control;

import com.github.serezhka.airplay.player.gstreamer.FullscreenController;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class LocalControlServerTest {
    private static final String TOKEN = "test-control-token";

    public static void main(String[] args) throws Exception {
        FakeFullscreenController fullscreen = new FakeFullscreenController();
        CountDownLatch quit = new CountDownLatch(1);
        try (LocalControlServer server = new LocalControlServer(0, TOKEN, fullscreen, quit::countDown)) {
            server.start();
            int port = server.getPort();

            assertEquals("ERR\tAUTH", send(port, "wrong\tSTATUS"), "invalid token");
            assertEquals(
                    "OK\tSTATUS\tRUNNING\ttrue\tFULLSCREEN_AVAILABLE\ttrue\tFULLSCREEN\tfalse",
                    send(port, TOKEN + "\tSTATUS"),
                    "initial status");
            assertEquals("ERR\tVALUE", send(port, TOKEN + "\tFULLSCREEN\t1"), "invalid value");
            assertEquals("ERR\tCOMMAND", send(port, TOKEN + "\t" + "X".repeat(600)),
                    "oversized request");
            assertEquals(
                    "OK\tSTATUS\tRUNNING\ttrue\tFULLSCREEN_AVAILABLE\ttrue\tFULLSCREEN\tfalse",
                    send(port, TOKEN + "\tSTATUS"),
                    "status after oversized request");
            assertEquals("OK\tFULLSCREEN\ttrue",
                    send(port, TOKEN + "\tFULLSCREEN\ttrue"), "enable fullscreen");
            assertTrue(fullscreen.isFullscreen(), "fullscreen state was not updated");
            assertEquals("OK\tFULLSCREEN\tfalse",
                    send(port, TOKEN + "\tFULLSCREEN\tfalse"), "disable fullscreen");
            assertEquals("OK\tQUIT", send(port, TOKEN + "\tQUIT"), "quit response");
            assertTrue(quit.await(2, TimeUnit.SECONDS), "quit action was not invoked");
        }

        try (LocalControlServer server = new LocalControlServer(0, TOKEN, null, () -> { })) {
            server.start();
            assertEquals("ERR\tUNAVAILABLE",
                    send(server.getPort(), TOKEN + "\tFULLSCREEN\ttrue"),
                    "non-GStreamer fullscreen command");
        }

        closeDisconnectsIdleClientAndAllowsRestart();

        System.out.println("LocalControlServerTest passed");
    }

    private static void closeDisconnectsIdleClientAndAllowsRestart() throws Exception {
        LocalControlServer server = new LocalControlServer(0, TOKEN, null, () -> { });
        try (Socket idleClient = new Socket(InetAddress.getByName("127.0.0.1"), start(server))) {
            idleClient.getOutputStream().write(TOKEN.getBytes(StandardCharsets.UTF_8));
            idleClient.getOutputStream().flush();
            Thread.sleep(100);

            long started = System.nanoTime();
            java.util.concurrent.atomic.AtomicBoolean stopped = new java.util.concurrent.atomic.AtomicBoolean();
            server.stop(() -> stopped.set(true));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(elapsedMillis < 500, "closing an idle client took " + elapsedMillis + " ms");
            assertTrue(stopped.get(), "SmartLifecycle stop callback was not invoked");
            idleClient.setSoTimeout(1_000);
            try {
                assertTrue(idleClient.getInputStream().read() == -1, "idle client was not closed");
            } catch (java.net.SocketException expected) {
                // A reset also proves the active client was closed.
            }

            server.start();
            assertEquals(
                    "OK\tSTATUS\tRUNNING\ttrue\tFULLSCREEN_AVAILABLE\tfalse\tFULLSCREEN\tfalse",
                    send(server.getPort(), TOKEN + "\tSTATUS"),
                    "status after restart");
        } finally {
            server.close();
        }
    }

    private static int start(LocalControlServer server) {
        server.start();
        return server.getPort();
    }

    private static String send(int port, String request) throws Exception {
        try (Socket socket = new Socket(InetAddress.getByName("127.0.0.1"), port);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                     socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     socket.getInputStream(), StandardCharsets.UTF_8))) {
            socket.setSoTimeout(2_000);
            writer.write(request);
            writer.newLine();
            writer.flush();
            return reader.readLine();
        }
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class FakeFullscreenController implements FullscreenController {
        private final List<Consumer<Boolean>> listeners = new ArrayList<>();
        private boolean fullscreen;

        @Override
        public boolean isFullscreen() {
            return fullscreen;
        }

        @Override
        public void setFullscreen(boolean fullscreen) {
            this.fullscreen = fullscreen;
            listeners.forEach(listener -> listener.accept(fullscreen));
        }

        @Override
        public void addFullscreenListener(Consumer<Boolean> listener) {
            listeners.add(listener);
            listener.accept(fullscreen);
        }
    }
}
