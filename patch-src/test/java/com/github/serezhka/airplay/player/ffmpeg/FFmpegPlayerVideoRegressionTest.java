package com.github.serezhka.airplay.player.ffmpeg;

import com.github.serezhka.airplay.lib.AudioStreamInfo;
import com.github.serezhka.airplay.lib.VideoStreamInfo;
import com.github.serezhka.airplay.server.AirPlayConsumer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class FFmpegPlayerVideoRegressionTest {
    public static void main(String[] args) throws Exception {
        callbacksWithoutAProcessAreHarmless();
        repeatedVideoFormatReplacesTheProcess();
        failedStartsLeaveNoStaleProcess();
        exitedProcessesAreNotWrittenTo();
        brokenInputStopsTheProcess(false);
        brokenInputStopsTheProcess(true);
        closeFailuresDoNotPreventCleanup();
        concurrentVideoCallbacksAreSerialized();
        System.out.println("FFmpegPlayer video regression tests passed");
    }

    private static void callbacksWithoutAProcessAreHarmless() {
        RecordingPlayer player = new RecordingPlayer();

        player.onVideo(new byte[]{1, 2, 3});
        player.onVideoSrcDisconnect();
        player.onVideoSrcDisconnect();

        assertEquals(0, player.startAttempts, "unexpected process starts");
    }

    private static void repeatedVideoFormatReplacesTheProcess() {
        FakeProcess first = new FakeProcess();
        FakeProcess second = new FakeProcess();
        RecordingPlayer player = new RecordingPlayer(first, second);
        byte[] firstFrame = {0, 0, 0, 1, 0x65};
        byte[] secondFrame = {0, 0, 0, 1, 0x41};

        player.onVideoFormat(null);
        player.onVideo(firstFrame);
        player.onVideoFormat(null);
        assertStopped(first, "replaced process");
        player.onVideo(secondFrame);

        assertArrayEquals(firstFrame, first.input.bytes.toByteArray(), "first process video");
        assertArrayEquals(secondFrame, second.input.bytes.toByteArray(), "second process video");
        assertEquals(1, first.input.flushCalls, "first process flush count");
        assertEquals(1, second.input.flushCalls, "second process flush count");
        assertEquals(2, player.startAttempts, "process replacement count");

        player.onVideoSrcDisconnect();
        player.onVideoSrcDisconnect();
        player.onVideo(firstFrame);
        assertStopped(first, "previous process after disconnect");
        assertStopped(second, "disconnected process");
        assertArrayEquals(secondFrame, second.input.bytes.toByteArray(), "video after disconnect");
    }

    private static void failedStartsLeaveNoStaleProcess() {
        FakeProcess first = new FakeProcess();
        FakeProcess second = new FakeProcess();
        RecordingPlayer player = new RecordingPlayer(first, second);
        IOException startFailure = new IOException("simulated ffplay start failure");

        player.startFailure = startFailure;
        assertStartFails(player, startFailure);
        player.onVideo(new byte[]{1});
        player.onVideoSrcDisconnect();

        player.startFailure = null;
        player.onVideoFormat(null);
        player.startFailure = startFailure;
        assertStartFails(player, startFailure);
        player.onVideo(new byte[]{2});
        player.onVideoSrcDisconnect();
        assertStopped(first, "process before failed replacement");
        assertEquals(0, first.input.bytes.size(), "video after failed replacement");

        player.startFailure = null;
        player.onVideoFormat(null);
        player.onVideo(new byte[]{3});
        assertArrayEquals(new byte[]{3}, second.input.bytes.toByteArray(), "video after recovery");
        player.onVideoSrcDisconnect();
        assertStopped(second, "recovered process");
    }

    private static void exitedProcessesAreNotWrittenTo() {
        FakeProcess process = new FakeProcess();
        RecordingPlayer player = new RecordingPlayer(process);
        player.onVideoFormat(null);
        process.alive = false;

        player.onVideo(new byte[]{1, 2, 3});
        player.onVideo(new byte[]{4, 5, 6});
        player.onVideoSrcDisconnect();

        assertEquals(0, process.input.bytes.size(), "writes to an exited process");
        assertEquals(0, process.input.flushCalls, "flushes to an exited process");
        assertStopped(process, "exited process");
    }

    private static void brokenInputStopsTheProcess(boolean failOnFlush) {
        FakeProcess process = new FakeProcess();
        FakeProcess recovered = new FakeProcess();
        RecordingPlayer player = new RecordingPlayer(process, recovered);
        process.input.failWrite = !failOnFlush;
        process.input.failFlush = failOnFlush;
        process.input.failClose = true;
        player.onVideoFormat(null);

        player.onVideo(new byte[]{1, 2, 3});
        assertStopped(process, failOnFlush ? "failed flush" : "failed write");
        player.onVideo(new byte[]{4, 5, 6});
        player.onVideoSrcDisconnect();
        assertStopped(process, "failed input after repeated callbacks");

        player.onVideoFormat(null);
        player.onVideo(new byte[]{7});
        assertArrayEquals(new byte[]{7}, recovered.input.bytes.toByteArray(), "recovered input");
        player.onVideoSrcDisconnect();
        assertStopped(recovered, "process after input recovery");
    }

    private static void closeFailuresDoNotPreventCleanup() {
        FakeProcess first = new FakeProcess();
        FakeProcess second = new FakeProcess();
        first.input.failClose = true;
        second.input.failClose = true;
        RecordingPlayer player = new RecordingPlayer(first, second);

        player.onVideoFormat(null);
        player.onVideoFormat(null);
        player.onVideoSrcDisconnect();
        player.onVideoSrcDisconnect();

        assertStopped(first, "replacement despite close failure");
        assertStopped(second, "disconnect despite close failure");
    }

    private static void concurrentVideoCallbacksAreSerialized() throws Exception {
        FakeProcess first = new FakeProcess();
        FakeProcess second = new FakeProcess();
        RecordingPlayer player = new RecordingPlayer(first, second);
        first.input.writeEntered = new CountDownLatch(1);
        first.input.releaseWrite = new CountDownLatch(1);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        List<Thread> callbacks = new ArrayList<>();
        player.onVideoFormat(null);
        callbacks.add(startCallback("video-writer", () -> player.onVideo(new byte[]{1}), callbackFailure));

        try {
            if (!first.input.writeEntered.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("video writer did not reach the input stream");
            }
            callbacks.add(startCallback("second-video-writer", () -> player.onVideo(new byte[]{2}),
                    callbackFailure));
            callbacks.add(startCallback("video-replacement", () -> player.onVideoFormat(null),
                    callbackFailure));
            callbacks.add(startCallback("video-disconnect", player::onVideoSrcDisconnect, callbackFailure));
            for (int index = 1; index < callbacks.size(); index++) {
                assertBlocked(callbacks.get(index));
            }
            assertEquals(0, first.input.closeCalls, "input closed during a video write");
            assertEquals(0, first.destroyCalls, "process destroyed during a video write");
            assertEquals(1, player.startAttempts, "replacement started during a video write");
        } finally {
            first.input.releaseWrite.countDown();
            for (Thread callback : callbacks) {
                callback.join(5000);
                if (callback.isAlive()) {
                    throw new AssertionError(callback.getName() + " did not finish");
                }
            }
        }

        if (callbackFailure.get() != null) {
            throw new AssertionError("concurrent video callback failed", callbackFailure.get());
        }
        player.onVideoSrcDisconnect();
        assertStopped(first, "original concurrent process");
        assertStopped(second, "replacement concurrent process");
    }

    private static Thread startCallback(String name, Runnable callback, AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> {
            try {
                callback.run();
            } catch (Throwable exception) {
                failure.compareAndSet(null, exception);
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void assertBlocked(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (thread.isAlive() && thread.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
        if (thread.getState() != Thread.State.BLOCKED) {
            throw new AssertionError(thread.getName() + " was not serialized with the video writer");
        }
    }

    private static void assertStartFails(RecordingPlayer player, IOException expected) {
        try {
            player.onVideoFormat(null);
            throw new AssertionError("process start failure was not propagated");
        } catch (RuntimeException exception) {
            if (exception.getCause() != expected) {
                throw new AssertionError("process start failure cause was not preserved", exception);
            }
        }
    }

    private static void assertStopped(FakeProcess process, String description) {
        assertEquals(1, process.input.closeCalls, description + " input close count");
        assertEquals(1, process.destroyCalls, description + " destroy count");
        assertEquals(false, process.isAlive(), description + " liveness");
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual, String description) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(description + " expected " + Arrays.toString(expected)
                    + " but was " + Arrays.toString(actual));
        }
    }

    private static final class RecordingPlayer extends FFmpegPlayer {
        private final Deque<FakeProcess> processes;
        private FakeProcess lastStarted;
        private IOException startFailure;
        private int startAttempts;

        private RecordingPlayer(FakeProcess... processes) {
            super(new SilentAudioConsumer());
            this.processes = new ArrayDeque<>(Arrays.asList(processes));
        }

        @Override
        Process startVideoProcess(ProcessBuilder processBuilder) throws IOException {
            startAttempts++;
            assertEquals(List.of("ffplay", "-fs", "-f", "h264", "-codec:v", "h264", "-probesize", "32",
                    "-analyzeduration", "0", "-vf", "setpts=0", "-flags", "low_delay", "-"),
                    processBuilder.command(), "fullscreen and low-latency command");
            assertEquals(ProcessBuilder.Redirect.PIPE, processBuilder.redirectInput(), "video input redirect");
            assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectOutput(), "video output redirect");
            assertEquals(ProcessBuilder.Redirect.INHERIT, processBuilder.redirectError(), "video error redirect");
            if (lastStarted != null) {
                assertStopped(lastStarted, "previous process before starting a new one");
            }
            if (startFailure != null) {
                throw startFailure;
            }
            lastStarted = processes.removeFirst();
            return lastStarted;
        }
    }

    private static final class FakeProcess extends Process {
        private final RecordingOutputStream input = new RecordingOutputStream();
        private volatile boolean alive = true;
        private int destroyCalls;

        @Override
        public OutputStream getOutputStream() {
            return input;
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            throw new AssertionError("video cleanup must not wait for the process to exit");
        }

        @Override
        public int exitValue() {
            if (alive) {
                throw new IllegalThreadStateException("process is still alive");
            }
            return 0;
        }

        @Override
        public void destroy() {
            assertEquals(true, input.closed, "input must close before process destruction");
            destroyCalls++;
            alive = false;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }
    }

    private static final class RecordingOutputStream extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private boolean closed;
        private boolean failWrite;
        private boolean failFlush;
        private boolean failClose;
        private int flushCalls;
        private int closeCalls;
        private CountDownLatch writeEntered;
        private CountDownLatch releaseWrite;

        @Override
        public void write(int value) throws IOException {
            write(new byte[]{(byte) value});
        }

        @Override
        public void write(byte[] frame, int offset, int length) throws IOException {
            if (writeEntered != null) {
                writeEntered.countDown();
                try {
                    if (!releaseWrite.await(10, TimeUnit.SECONDS)) {
                        throw new AssertionError("video write was not released");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IOException(exception);
                }
            }
            if (closed) {
                throw new AssertionError("video input was closed before a write finished");
            }
            if (failWrite) {
                throw new IOException("simulated video write failure");
            }
            bytes.write(frame, offset, length);
        }

        @Override
        public void flush() throws IOException {
            if (closed) {
                throw new AssertionError("video input was closed before flushing");
            }
            flushCalls++;
            if (failFlush) {
                throw new IOException("simulated video flush failure");
            }
        }

        @Override
        public void close() throws IOException {
            closeCalls++;
            closed = true;
            if (failClose) {
                throw new IOException("simulated video input close failure");
            }
        }
    }

    private static final class SilentAudioConsumer implements AirPlayConsumer {
        @Override
        public void onVideoFormat(VideoStreamInfo videoStreamInfo) {
        }

        @Override
        public void onVideo(byte[] bytes) {
        }

        @Override
        public void onVideoSrcDisconnect() {
        }

        @Override
        public void onAudioFormat(AudioStreamInfo audioStreamInfo) {
        }

        @Override
        public void onAudio(byte[] bytes) {
        }

        @Override
        public void onAudioSrcDisconnect() {
        }
    }
}
