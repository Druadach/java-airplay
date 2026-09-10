package com.github.serezhka.airplay.server.internal.decoder;

import com.github.serezhka.airplay.server.internal.packet.AudioPacket;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

public class AudioDecoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf message, List<Object> output) {
        byte[] headerBytes = new byte[12];
        message.readBytes(headerBytes);

        int flag = headerBytes[0] & 0xFF;
        int type = headerBytes[1] & 0x7F;
        int sequenceNumber = ((headerBytes[2] & 0xFF) << 8) | (headerBytes[3] & 0xFF);
        long timestamp = readUnsignedIntBigEndian(headerBytes, 4);
        long ssrc = readUnsignedIntBigEndian(headerBytes, 8);

        AudioPacket audioPacket = AudioPacket.builder()
                .flag(flag)
                .type(type)
                .sequenceNumber(sequenceNumber)
                .timestamp(timestamp)
                .ssrc(ssrc)
                .available(true)
                .encodedAudioSize(message.readableBytes())
                .build();
        audioPacket.encodedAudio(packet -> message.readBytes(packet, 0, message.readableBytes()));
        output.add(audioPacket);
    }

    private static long readUnsignedIntBigEndian(byte[] bytes, int offset) {
        return ((long) bytes[offset] & 0xFF) << 24 |
                ((long) bytes[offset + 1] & 0xFF) << 16 |
                ((long) bytes[offset + 2] & 0xFF) << 8 |
                ((long) bytes[offset + 3] & 0xFF);
    }
}
