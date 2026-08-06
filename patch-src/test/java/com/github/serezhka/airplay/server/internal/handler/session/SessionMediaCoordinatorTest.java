package com.github.serezhka.airplay.server.internal.handler.session;

import com.github.serezhka.airplay.lib.AudioStreamInfo;
import com.github.serezhka.airplay.lib.VideoStreamInfo;
import com.github.serezhka.airplay.server.AirPlayConsumer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class SessionMediaCoordinatorTest {

    public static void main(String[] args) throws Exception {
        newerSessionRejectsOldFramesAndTeardown();
        repeatedSetupInvalidatesThePreviousReceiver();
        takeoverRevokesBothMediaStreams();
        inFlightFrameCompletesBeforeTakeoverReset();
        System.out.println("Session media takeover tests passed");
    }

    private static void newerSessionRejectsOldFramesAndTeardown() {
        SessionManager sessionManager = new SessionManager();
        RecordingConsumer delegate = new RecordingConsumer();
        SessionMediaCoordinator coordinator = new SessionMediaCoordinator(sessionManager, delegate);
        Session firstSession = sessionManager.getSession("first");
        Session secondSession = sessionManager.getSession("second");
        int[] firstChannelCloses = new int[1];
        SessionManager.ControlSession firstControl = sessionManager.openControlSession(
                firstSession, () -> firstChannelCloses[0]++);
        SessionManager.ControlSession secondControl = sessionManager.openControlSession(
                secondSession, () -> { });

        AirPlayConsumer firstConsumer = coordinator.prepareVideo(
                firstControl, new VideoStreamInfo("first-video"));
        firstSession.setVideoReceiverThread(new Thread());
        firstConsumer.onVideo(new byte[] {1});

        AirPlayConsumer secondConsumer = coordinator.prepareVideo(
                secondControl, new VideoStreamInfo("second-video"));
        secondSession.setVideoReceiverThread(new Thread());

        assertFalse(firstSession.isVideoActive(), "takeover must stop the first receiver");
        assertEquals(1, delegate.videoDisconnects, "takeover disconnect count");
        assertEquals(1, firstChannelCloses[0], "takeover must close the old control channel");
        assertNull(
                coordinator.prepareVideo(firstControl, new VideoStreamInfo("stale-retry")),
                "revoked control connection must not reclaim playback");

        firstConsumer.onVideo(new byte[] {2});
        firstConsumer.onVideoSrcDisconnect();
        coordinator.disconnectVideo(firstControl);
        coordinator.controlDisconnected(firstControl);
        secondConsumer.onVideo(new byte[] {3});

        assertListEquals(List.of(1, 3), delegate.videoFrames, "accepted video frames");
        assertEquals(1, delegate.videoDisconnects, "stale teardown must not stop the new stream");

        coordinator.controlDisconnected(secondControl);
        secondConsumer.onVideo(new byte[] {4});

        assertEquals(2, delegate.videoDisconnects, "current teardown disconnect count");
        assertListEquals(List.of(1, 3), delegate.videoFrames, "frames after current teardown");
        assertFalse(sessionManager.isActiveSession(secondSession), "idle session must release ownership");
    }

    private static void repeatedSetupInvalidatesThePreviousReceiver() {
        SessionManager sessionManager = new SessionManager();
        RecordingConsumer delegate = new RecordingConsumer();
        SessionMediaCoordinator coordinator = new SessionMediaCoordinator(sessionManager, delegate);
        Session session = sessionManager.getSession("same-session");
        SessionManager.ControlSession controlSession = sessionManager.openControlSession(
                session, () -> { });

        AirPlayConsumer firstConsumer = coordinator.prepareVideo(
                controlSession, new VideoStreamInfo("first-generation"));
        session.setVideoReceiverThread(new Thread());
        AirPlayConsumer secondConsumer = coordinator.prepareVideo(
                controlSession, new VideoStreamInfo("second-generation"));
        session.setVideoReceiverThread(new Thread());

        firstConsumer.onVideo(new byte[] {5});
        secondConsumer.onVideo(new byte[] {6});

        assertEquals(2, delegate.videoFormats, "format count for repeated setup");
        assertEquals(1, delegate.videoDisconnects, "repeated setup reset count");
        assertListEquals(List.of(6), delegate.videoFrames, "only the newest generation may write");
    }

    private static void takeoverRevokesBothMediaStreams() {
        SessionManager sessionManager = new SessionManager();
        RecordingConsumer delegate = new RecordingConsumer();
        SessionMediaCoordinator coordinator = new SessionMediaCoordinator(sessionManager, delegate);
        Session firstSession = sessionManager.getSession("av-first");
        Session secondSession = sessionManager.getSession("av-second");
        SessionManager.ControlSession firstControl = sessionManager.openControlSession(
                firstSession, () -> { });
        SessionManager.ControlSession secondControl = sessionManager.openControlSession(
                secondSession, () -> { });

        AirPlayConsumer firstVideo = coordinator.prepareVideo(
                firstControl, new VideoStreamInfo("av-first-video"));
        firstSession.setVideoReceiverThread(new Thread());
        AirPlayConsumer firstAudio = coordinator.prepareAudio(firstControl, null);
        firstSession.setAudioReceiverThread(new Thread());
        firstSession.setAudioControlServerThread(new Thread());

        AirPlayConsumer secondVideo = coordinator.prepareVideo(
                secondControl, new VideoStreamInfo("av-second-video"));
        secondSession.setVideoReceiverThread(new Thread());

        firstVideo.onVideo(new byte[] {7});
        firstAudio.onAudio(new byte[] {8});
        secondVideo.onVideo(new byte[] {9});

        assertFalse(firstSession.isVideoActive(), "video receiver must stop on session takeover");
        assertFalse(firstSession.isAudioActive(), "audio receivers must stop on session takeover");
        assertEquals(1, delegate.audioDisconnects, "audio disconnect on session takeover");
        assertEquals(1, delegate.videoDisconnects, "video disconnect on session takeover");
        assertListEquals(List.of(9), delegate.videoFrames, "old video generation after AV takeover");
        assertListEquals(List.of(), delegate.audioFrames, "old audio generation after AV takeover");
    }

    private static void inFlightFrameCompletesBeforeTakeoverReset() throws Exception {
        SessionManager sessionManager = new SessionManager();
        BlockingConsumer delegate = new BlockingConsumer();
        SessionMediaCoordinator coordinator = new SessionMediaCoordinator(sessionManager, delegate);
        Session firstSession = sessionManager.getSession("concurrent-first");
        Session secondSession = sessionManager.getSession("concurrent-second");
        SessionManager.ControlSession firstControl = sessionManager.openControlSession(
                firstSession, () -> { });
        SessionManager.ControlSession secondControl = sessionManager.openControlSession(
                secondSession, () -> { });

        AirPlayConsumer firstConsumer = coordinator.prepareVideo(
                firstControl, new VideoStreamInfo("concurrent-first"));
        firstSession.setVideoReceiverThread(new Thread());

        Thread frameThread = new Thread(
                () -> firstConsumer.onVideo(new byte[] {10}), "test-old-video-frame");
        frameThread.start();
        if (!delegate.frameEntered.await(2, TimeUnit.SECONDS)) {
            throw new AssertionError("old frame did not enter the delegate");
        }

        AirPlayConsumer[] secondConsumer = new AirPlayConsumer[1];
        CountDownLatch takeoverStarted = new CountDownLatch(1);
        Thread takeoverThread = new Thread(() -> {
            takeoverStarted.countDown();
            secondConsumer[0] = coordinator.prepareVideo(
                    secondControl, new VideoStreamInfo("concurrent-second"));
            secondSession.setVideoReceiverThread(new Thread());
        }, "test-video-takeover");
        takeoverThread.start();
        if (!takeoverStarted.await(2, TimeUnit.SECONDS)) {
            throw new AssertionError("takeover thread did not start");
        }

        delegate.releaseFrame.countDown();
        joinOrFail(frameThread, "old frame thread");
        joinOrFail(takeoverThread, "takeover thread");

        firstConsumer.onVideo(new byte[] {12});
        secondConsumer[0].onVideo(new byte[] {11});

        assertStringListEquals(
                List.of(
                        "format:concurrent-first",
                        "frame-start:10",
                        "frame-end:10",
                        "disconnect",
                        "format:concurrent-second",
                        "frame:11"),
                delegate.events,
                "concurrent takeover order");
    }

    private static void joinOrFail(Thread thread, String description) throws InterruptedException {
        thread.join(2_000);
        if (thread.isAlive()) {
            throw new AssertionError(description + " did not finish");
        }
    }

    private static void assertEquals(int expected, int actual, String description) {
        if (expected != actual) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertFalse(boolean value, String description) {
        if (value) {
            throw new AssertionError(description);
        }
    }

    private static void assertNull(Object value, String description) {
        if (value != null) {
            throw new AssertionError(description);
        }
    }

    private static void assertListEquals(
            List<Integer> expected, List<Integer> actual, String description) {
        if (!expected.equals(actual)) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertStringListEquals(
            List<String> expected, List<String> actual, String description) {
        if (!expected.equals(actual)) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }

    private static final class RecordingConsumer implements AirPlayConsumer {
        private final List<Integer> videoFrames = new ArrayList<>();
        private final List<Integer> audioFrames = new ArrayList<>();
        private int videoFormats;
        private int videoDisconnects;
        private int audioDisconnects;

        @Override
        public void onVideoFormat(VideoStreamInfo videoStreamInfo) {
            videoFormats++;
        }

        @Override
        public void onVideo(byte[] bytes) {
            videoFrames.add((int) bytes[0]);
        }

        @Override
        public void onVideoSrcDisconnect() {
            videoDisconnects++;
        }

        @Override
        public void onAudioFormat(AudioStreamInfo audioStreamInfo) {
        }

        @Override
        public void onAudio(byte[] bytes) {
            audioFrames.add((int) bytes[0]);
        }

        @Override
        public void onAudioSrcDisconnect() {
            audioDisconnects++;
        }
    }

    private static final class BlockingConsumer implements AirPlayConsumer {
        private final List<String> events = new CopyOnWriteArrayList<>();
        private final CountDownLatch frameEntered = new CountDownLatch(1);
        private final CountDownLatch releaseFrame = new CountDownLatch(1);

        @Override
        public void onVideoFormat(VideoStreamInfo videoStreamInfo) {
            events.add("format:" + videoStreamInfo.getStreamConnectionId());
        }

        @Override
        public void onVideo(byte[] bytes) {
            int value = bytes[0];
            if (value != 10) {
                events.add("frame:" + value);
                return;
            }

            events.add("frame-start:" + value);
            frameEntered.countDown();
            try {
                if (!releaseFrame.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out while blocking the old frame");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("old frame was interrupted", exception);
            }
            events.add("frame-end:" + value);
        }

        @Override
        public void onVideoSrcDisconnect() {
            events.add("disconnect");
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
