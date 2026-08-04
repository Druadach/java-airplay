package com.github.serezhka.airplay.server.internal.handler.audio;

import com.github.serezhka.airplay.lib.AirPlay;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import com.github.serezhka.airplay.server.internal.packet.AudioPacket;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class AudioHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(AudioHandler.class);

    private static final int RTP_SEQUENCE_MASK = 0xffff;
    private static final int HALF_SEQUENCE_RANGE = 0x8000;
    private static final int BUFFER_SIZE = 512;
    static final int MAX_REORDERED_PACKETS = 8;

    private final AirPlay airPlay;
    private final AirPlayConsumer dataConsumer;
    private final AudioPacket[] buffer = new AudioPacket[BUFFER_SIZE];

    private boolean sequenceInitialized;
    private int lastDeliveredSequence;
    private int packetsInBuffer;

    public AudioHandler(AirPlay airPlay, AirPlayConsumer dataConsumer) {
        this.airPlay = airPlay;
        this.dataConsumer = dataConsumer;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        AudioPacket packet = (AudioPacket) msg;
        int sequence = normalizeSequence(packet.getSequenceNumber());

        if (!sequenceInitialized) {
            sequenceInitialized = true;
            lastDeliveredSequence = normalizeSequence(sequence - 1);
        } else if (!isNewerSequence(sequence, lastDeliveredSequence)) {
            return;
        }

        if (!enqueue(packet, sequence)) {
            return;
        }

        drainConsecutivePackets();

        while (packetsInBuffer >= MAX_REORDERED_PACKETS) {
            int nextSequence = findNearestBufferedSequence();
            if (nextSequence < 0) {
                break;
            }

            int skippedPackets = forwardDistance(lastDeliveredSequence, nextSequence) - 1;
            if (skippedPackets > 0) {
                log.debug("Skipping {} missing audio packet(s) before sequence {}",
                        skippedPackets, nextSequence);
            }

            lastDeliveredSequence = normalizeSequence(nextSequence - 1);
            if (drainConsecutivePackets() == 0) {
                break;
            }
        }
    }

    private boolean enqueue(AudioPacket packet, int sequence) {
        int index = sequence % buffer.length;
        AudioPacket existing = buffer[index];

        if (existing != null && existing.isAvailable()) {
            int existingSequence = normalizeSequence(existing.getSequenceNumber());
            if (existingSequence == sequence) {
                return false;
            }

            int existingDistance = forwardDistance(lastDeliveredSequence, existingSequence);
            int incomingDistance = forwardDistance(lastDeliveredSequence, sequence);
            if (existingDistance > 0
                    && existingDistance < HALF_SEQUENCE_RANGE
                    && existingDistance <= incomingDistance) {
                return false;
            }

            existing.available(false);
        } else {
            packetsInBuffer++;
        }

        packet.available(true);
        buffer[index] = packet;
        return true;
    }

    private int drainConsecutivePackets() throws Exception {
        int delivered = 0;

        while (true) {
            int expectedSequence = normalizeSequence(lastDeliveredSequence + 1);
            int index = expectedSequence % buffer.length;
            AudioPacket packet = buffer[index];

            if (packet == null
                    || !packet.isAvailable()
                    || normalizeSequence(packet.getSequenceNumber()) != expectedSequence) {
                return delivered;
            }

            airPlay.decryptAudio(packet.getEncodedAudio(), packet.getEncodedAudioSize());
            dataConsumer.onAudio(Arrays.copyOfRange(
                    packet.getEncodedAudio(), 0, packet.getEncodedAudioSize()));

            packet.available(false);
            buffer[index] = null;
            lastDeliveredSequence = expectedSequence;
            packetsInBuffer--;
            delivered++;
        }
    }

    private int findNearestBufferedSequence() {
        int nearestSequence = -1;
        int nearestDistance = HALF_SEQUENCE_RANGE;

        for (int index = 0; index < buffer.length; index++) {
            AudioPacket packet = buffer[index];
            if (packet == null || !packet.isAvailable()) {
                continue;
            }

            int sequence = normalizeSequence(packet.getSequenceNumber());
            int distance = forwardDistance(lastDeliveredSequence, sequence);
            if (distance == 0 || distance >= HALF_SEQUENCE_RANGE) {
                packet.available(false);
                buffer[index] = null;
                packetsInBuffer--;
                continue;
            }

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestSequence = sequence;
            }
        }

        return nearestSequence;
    }

    static boolean isNewerSequence(int sequence, int previousSequence) {
        int distance = forwardDistance(previousSequence, sequence);
        return distance > 0 && distance < HALF_SEQUENCE_RANGE;
    }

    private static int forwardDistance(int fromSequence, int toSequence) {
        return (toSequence - fromSequence) & RTP_SEQUENCE_MASK;
    }

    private static int normalizeSequence(int sequence) {
        return sequence & RTP_SEQUENCE_MASK;
    }
}
