package com.github.serezhka.airplay.server.internal.decoder;

import com.github.serezhka.airplay.server.internal.packet.AudioPacket;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import java.util.Arrays;

public final class AudioDecoderRegressionTest {
    public static void main(String[] arguments) {
        timestampsUseUnsigned32BitValues();
        ssrcUsesItsOwnNetworkOrderBytes();
        packetMetadataAndAudioPayloadArePreserved();
        System.out.println("AudioDecoder regression tests passed");
    }

    private static void timestampsUseUnsigned32BitValues() {
        for (long timestamp : new long[]{0x80000000L, 0L, 0x7FFFFFFFL, 0xFFFFFFFFL, 2357908754L}) {
            AudioPacket packet = decode(0x60, 0, timestamp, 0L, new byte[0]);
            assertEquals(timestamp, packet.getTimestamp(), "Unsigned RTP timestamp");
        }
    }

    private static void ssrcUsesItsOwnNetworkOrderBytes() {
        for (long ssrc : new long[]{0x11223344L, 0L, 0x01020304L, 0x7FFFFFFFL, 0x80000000L, 0xFFFFFFFFL}) {
            AudioPacket packet = decode(0x60, 1, 0x12345678L, ssrc, new byte[0]);
            assertEquals(ssrc, packet.getSsrc(), "Unsigned RTP SSRC must use bytes 8 through 11");
        }
    }

    private static void packetMetadataAndAudioPayloadArePreserved() {
        byte[] payload = new byte[1920];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) index;
        }
        for (int payloadType : new int[]{0x60, 0xE0}) {
            AudioPacket packet = decode(payloadType, 65535, 0x89ABCDEFL, 0xFEDCBA98L, payload);
            assertEquals(128, packet.getFlag(), "RTP flags");
            assertEquals(96, packet.getType(), "RTP payload type must ignore the marker bit");
            assertEquals(65535, packet.getSequenceNumber(), "Unsigned sequence number");
            assertEquals(0x89ABCDEFL, packet.getTimestamp(), "Packet timestamp");
            assertEquals(0xFEDCBA98L, packet.getSsrc(), "Packet SSRC");
            assertEquals(payload.length, packet.getEncodedAudioSize(), "Audio payload length");
            if (!packet.isAvailable()) {
                throw new AssertionError("Decoded audio must be available");
            }
            if (!Arrays.equals(payload, Arrays.copyOf(packet.getEncodedAudio(), packet.getEncodedAudioSize()))) {
                throw new AssertionError("Audio payload must not change during header decoding");
            }
        }
    }

    private static AudioPacket decode(int payloadType, int sequenceNumber, long timestamp, long ssrc,
                                      byte[] payload) {
        EmbeddedChannel channel = new EmbeddedChannel(new AudioDecoder());
        try {
            ByteBuf message = Unpooled.buffer(12 + payload.length)
                    .writeByte(0x80)
                    .writeByte(payloadType)
                    .writeShort(sequenceNumber)
                    .writeInt((int) timestamp)
                    .writeInt((int) ssrc)
                    .writeBytes(payload);
            if (!channel.writeInbound(message)) {
                throw new AssertionError("RTP packet must produce decoded audio");
            }
            AudioPacket packet = channel.readInbound();
            if (packet == null || channel.readInbound() != null) {
                throw new AssertionError("Each RTP packet must produce exactly one audio packet");
            }
            return packet;
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static void assertEquals(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + " but got " + actual);
        }
    }
}
