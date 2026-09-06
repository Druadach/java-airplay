package com.github.serezhka.airplay.server.internal.handler.session;

import com.github.serezhka.airplay.lib.AudioStreamInfo;
import com.github.serezhka.airplay.lib.VideoStreamInfo;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import com.github.serezhka.airplay.server.VolumeController;

import java.util.ArrayList;
import java.util.List;

public final class SessionMediaCoordinatorVolumeTest {
    public static void main(String[] args) {
        pendingVolumeIsAppliedWhenAudioStarts();
        activeVolumeChangesAreAppliedImmediately();
        inactiveSessionCannotChangeOutputVolume();
        System.out.println("Session volume coordination tests passed");
    }

    private static void pendingVolumeIsAppliedWhenAudioStarts() {
        SessionManager sessions = new SessionManager();
        RecordingConsumer consumer = new RecordingConsumer();
        SessionMediaCoordinator coordinator = new SessionMediaCoordinator(sessions, consumer);
        Session session = sessions.getSession("pending-volume");
        SessionManager.ControlSession control = sessions.openControlSession(session, () -> { });

        coordinator.setVolume(control, -15.0);
        coordinator.prepareAudio(control, null);

        assertListEquals(List.of(-15.0), consumer.volumeChanges, "pending volume");
    }

    private static void activeVolumeChangesAreAppliedImmediately() {
        SessionManager sessions = new SessionManager();
        RecordingConsumer consumer = new RecordingConsumer();
        SessionMediaCoordinator coordinator = new SessionMediaCoordinator(sessions, consumer);
        Session session = sessions.getSession("active-volume");
        SessionManager.ControlSession control = sessions.openControlSession(session, () -> { });

        coordinator.prepareAudio(control, null);
        coordinator.setVolume(control, -6.0);

        assertListEquals(List.of(0.0, -6.0), consumer.volumeChanges, "active volume");
    }

    private static void inactiveSessionCannotChangeOutputVolume() {
        SessionManager sessions = new SessionManager();
        RecordingConsumer consumer = new RecordingConsumer();
        SessionMediaCoordinator coordinator = new SessionMediaCoordinator(sessions, consumer);
        Session first = sessions.getSession("first-volume");
        Session second = sessions.getSession("second-volume");
        SessionManager.ControlSession firstControl = sessions.openControlSession(first, () -> { });
        SessionManager.ControlSession secondControl = sessions.openControlSession(second, () -> { });

        coordinator.prepareAudio(firstControl, null);
        coordinator.setVolume(secondControl, -24.0);
        assertListEquals(List.of(0.0), consumer.volumeChanges, "inactive volume");

        coordinator.prepareAudio(secondControl, null);
        assertListEquals(List.of(0.0, -24.0), consumer.volumeChanges, "second session volume");
    }

    private static void assertListEquals(List<Double> expected, List<Double> actual, String description) {
        if (!expected.equals(actual)) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }

    private static final class RecordingConsumer implements AirPlayConsumer, VolumeController {
        private final List<Double> volumeChanges = new ArrayList<>();

        @Override
        public void setVolumeDb(double volumeDb) {
            volumeChanges.add(volumeDb);
        }

        @Override public void onVideoFormat(VideoStreamInfo info) { }
        @Override public void onVideo(byte[] bytes) { }
        @Override public void onVideoSrcDisconnect() { }
        @Override public void onAudioFormat(AudioStreamInfo info) { }
        @Override public void onAudio(byte[] bytes) { }
        @Override public void onAudioSrcDisconnect() { }
    }
}
