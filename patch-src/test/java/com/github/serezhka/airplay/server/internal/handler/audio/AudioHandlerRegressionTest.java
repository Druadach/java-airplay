package com.github.serezhka.airplay.server.internal.handler.audio;

import com.github.serezhka.airplay.lib.AirPlay;
import com.github.serezhka.airplay.lib.AudioStreamInfo;
import com.github.serezhka.airplay.lib.VideoStreamInfo;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import com.github.serezhka.airplay.server.internal.packet.AudioPacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class AudioHandlerRegressionTest {
    public static void main(String[] args) throws Exception {
        firstPacketMayBeZero();
        orderedPacketsCrossSequenceWrap();
        reorderedPacketsCrossSequenceWrap();
        duplicatesAndOldPacketsAreIgnored();
        missingPacketDoesNotMuteTheStreamForever();
        sequenceComparisonUsesSixteenBitArithmetic();
        System.out.println("AudioHandler regression tests passed");
    }

    private static void firstPacketMayBeZero() throws Exception {
        assertDelivered(List.of(0), 0);
    }

    private static void orderedPacketsCrossSequenceWrap() throws Exception {
        assertDelivered(List.of(65534, 65535, 0, 1), 65534, 65535, 0, 1);
    }

    private static void reorderedPacketsCrossSequenceWrap() throws Exception {
        assertDelivered(List.of(65534, 65535, 0, 1), 65534, 0, 65535, 1);
    }

    private static void duplicatesAndOldPacketsAreIgnored() throws Exception {
        assertDelivered(List.of(10, 11), 10, 10, 9, 11);
    }

    private static void missingPacketDoesNotMuteTheStreamForever() throws Exception {
        int[] received = new int[AudioHandler.MAX_REORDERED_PACKETS + 1];
        received[0] = 100;
        for (int index = 1; index < received.length; index++) {
            received[index] = 101 + index;
        }

        List<Integer> expected = new ArrayList<>();
        expected.add(100);
        for (int sequence = 102; sequence <= 101 + AudioHandler.MAX_REORDERED_PACKETS; sequence++) {
            expected.add(sequence);
        }
        assertDelivered(expected, received);
    }

    private static void sequenceComparisonUsesSixteenBitArithmetic() {
        assertTrue(AudioHandler.isNewerSequence(0, 65535), "0 must follow 65535");
        assertTrue(AudioHandler.isNewerSequence(1, 65535), "1 must be newer than 65535");
        assertFalse(AudioHandler.isNewerSequence(65535, 0), "65535 must be old after 0");
        assertFalse(AudioHandler.isNewerSequence(42, 42), "duplicates must not be newer");
    }

    private static void assertDelivered(List<Integer> expected, int... received) throws Exception {
        RecordingConsumer consumer = new RecordingConsumer();
        AudioHandler handler = new AudioHandler(new NoOpAirPlay(), consumer);

        for (int sequence : received) {
            handler.channelRead(null, packet(sequence));
        }

        if (!expected.equals(consumer.sequences)) {
            throw new AssertionError("Expected " + expected + " but got " + consumer.sequences
                    + " for input " + Arrays.toString(received));
        }
    }

    private static AudioPacket packet(int sequence) {
        AudioPacket packet = AudioPacket.builder()
                .available(true)
                .sequenceNumber(sequence)
                .encodedAudioSize(2)
                .build();
        return packet.encodedAudio(bytes -> {
            bytes[0] = (byte) (sequence >>> 8);
            bytes[1] = (byte) sequence;
        });
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean value, String message) {
        assertTrue(!value, message);
    }

    private static final class NoOpAirPlay extends AirPlay {
        @Override
        public void decryptAudio(byte[] encryptedAudio, int audioLength) {
            // Test packets carry their sequence number as plaintext.
        }
    }

    private static final class RecordingConsumer implements AirPlayConsumer {
        private final List<Integer> sequences = new ArrayList<>();

        @Override
        public void onAudio(byte[] bytes) {
            sequences.add((Byte.toUnsignedInt(bytes[0]) << 8) | Byte.toUnsignedInt(bytes[1]));
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

        @Override
        public void onAudioFormat(AudioStreamInfo audioStreamInfo) {
        }

        @Override
        public void onAudioSrcDisconnect() {
        }
    }
}
