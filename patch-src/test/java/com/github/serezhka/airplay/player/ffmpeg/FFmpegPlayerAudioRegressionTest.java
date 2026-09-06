package com.github.serezhka.airplay.player.ffmpeg;

import com.github.serezhka.airplay.lib.AudioStreamInfo;
import com.github.serezhka.airplay.lib.VideoStreamInfo;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import com.github.serezhka.airplay.server.VolumeController;

import java.lang.reflect.Constructor;
import java.util.Arrays;

public final class FFmpegPlayerAudioRegressionTest {
    public static void main(String[] args) throws Exception {
        audioCallbacksAreForwardedToTheAudioBackend();
        volumeChangesAreForwardedToTheAudioBackend();
        System.out.println("FFmpegPlayer audio regression tests passed");
    }

    private static void audioCallbacksAreForwardedToTheAudioBackend() throws Exception {
        RecordingConsumer audioBackend = new RecordingConsumer();
        FFmpegPlayer player = new FFmpegPlayer(audioBackend);
        AudioStreamInfo streamInfo = audioStreamInfo();
        byte[] audioFrame = {(byte) 0xf8, (byte) 0xe8, 0x50, 0x00};

        player.onAudioFormat(streamInfo);
        player.onAudio(audioFrame);
        player.onAudioSrcDisconnect();

        assertSame(streamInfo, audioBackend.streamInfo, "audio format");
        assertArrayEquals(audioFrame, audioBackend.audioFrame, "audio frame");
        assertEquals(1, audioBackend.disconnects, "audio disconnect count");
    }

    private static void volumeChangesAreForwardedToTheAudioBackend() {
        RecordingConsumer audioBackend = new RecordingConsumer();
        FFmpegPlayer player = new FFmpegPlayer(audioBackend);

        player.setVolumeDb(-15.0);

        assertEquals(1, audioBackend.volumeChanges, "volume change count");
        if (Math.abs(-15.0 - audioBackend.volumeDb) > 0.000001) {
            throw new AssertionError("volume was not forwarded unchanged");
        }
    }

    private static AudioStreamInfo audioStreamInfo() throws Exception {
        Constructor<AudioStreamInfo> constructor = AudioStreamInfo.class.getDeclaredConstructor(
                AudioStreamInfo.CompressionType.class,
                AudioStreamInfo.AudioFormat.class,
                int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(
                AudioStreamInfo.CompressionType.AAC_ELD,
                AudioStreamInfo.AudioFormat.AAC_ELD_44100_2,
                480);
    }

    private static void assertSame(Object expected, Object actual, String description) {
        if (expected != actual) {
            throw new AssertionError(description + " was not forwarded unchanged");
        }
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual, String description) {
        if (!Arrays.equals(expected, actual)) {
            throw new AssertionError(description + " expected " + Arrays.toString(expected)
                    + " but was " + Arrays.toString(actual));
        }
    }

    private static void assertEquals(int expected, int actual, String description) {
        if (expected != actual) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }

    private static final class RecordingConsumer implements AirPlayConsumer, VolumeController {
        private AudioStreamInfo streamInfo;
        private byte[] audioFrame;
        private int disconnects;
        private int volumeChanges;
        private double volumeDb;

        @Override
        public void setVolumeDb(double volumeDb) {
            volumeChanges++;
            this.volumeDb = volumeDb;
        }

        @Override
        public void onAudioFormat(AudioStreamInfo audioStreamInfo) {
            streamInfo = audioStreamInfo;
        }

        @Override
        public void onAudio(byte[] bytes) {
            audioFrame = bytes;
        }

        @Override
        public void onAudioSrcDisconnect() {
            disconnects++;
        }

        @Override
        public void onVideoFormat(VideoStreamInfo videoStreamInfo) {
        }

        @Override
        public void onVideo(byte[] bytes) {
        }

        @Override
        public void onVideoSrcDisconnect() {
        }
    }
}
