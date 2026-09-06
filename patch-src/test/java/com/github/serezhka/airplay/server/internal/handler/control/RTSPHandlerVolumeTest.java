package com.github.serezhka.airplay.server.internal.handler.control;

import com.github.serezhka.airplay.server.AirPlayConsumer;
import com.github.serezhka.airplay.server.VolumeController;
import com.github.serezhka.airplay.server.internal.handler.session.SessionManager;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.handler.codec.rtsp.RtspVersions;

import java.nio.charset.StandardCharsets;

public final class RTSPHandlerVolumeTest {
    public static void main(String[] args) {
        setAndGetParameterUseSessionVolume();
        System.out.println("RTSP volume tests passed");
    }

    private static void setAndGetParameterUseSessionVolume() {
        SessionManager sessionManager = new SessionManager();
        RecordingConsumer consumer = new RecordingConsumer();
        EmbeddedChannel channel = new EmbeddedChannel(new RTSPHandler(7011, sessionManager, consumer));
        try {
            FullHttpResponse setResponse = request(
                    channel,
                    RtspMethods.SET_PARAMETER,
                    "volume: -15.0\r\n");
            assertResponse(setResponse, "SET_PARAMETER");
            setResponse.release();

            FullHttpResponse getResponse = request(channel, RtspMethods.GET_PARAMETER, "");
            assertResponse(getResponse, "GET_PARAMETER");
            String body = getResponse.content().toString(StandardCharsets.US_ASCII);
            getResponse.release();
            if (!"volume: -15.000000\r\n".equals(body)) {
                throw new AssertionError("unexpected GET_PARAMETER body: " + body);
            }
            if (consumer.volumeChanges != 0) {
                throw new AssertionError("volume must not reach an inactive audio lease");
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static FullHttpResponse request(
            EmbeddedChannel channel, io.netty.handler.codec.http.HttpMethod method, String body) {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(
                RtspVersions.RTSP_1_0,
                method,
                "/rtsp",
                Unpooled.copiedBuffer(body, StandardCharsets.US_ASCII));
        request.headers().set("CSeq", "1");
        request.headers().set("Active-Remote", "volume-test");
        channel.writeInbound(request);
        FullHttpResponse response = channel.readOutbound();
        if (response == null) {
            throw new AssertionError("RTSP handler did not write a response");
        }
        return response;
    }

    private static void assertResponse(FullHttpResponse response, String method) {
        if (response.status().code() != 200) {
            throw new AssertionError(method + " returned " + response.status());
        }
    }

    private static final class RecordingConsumer implements AirPlayConsumer, VolumeController {
        private int volumeChanges;

        @Override
        public void setVolumeDb(double volumeDb) {
            volumeChanges++;
        }

        @Override public void onVideoFormat(com.github.serezhka.airplay.lib.VideoStreamInfo info) { }
        @Override public void onVideo(byte[] bytes) { }
        @Override public void onVideoSrcDisconnect() { }
        @Override public void onAudioFormat(com.github.serezhka.airplay.lib.AudioStreamInfo info) { }
        @Override public void onAudio(byte[] bytes) { }
        @Override public void onAudioSrcDisconnect() { }
    }
}
