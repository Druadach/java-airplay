package com.github.serezhka.airplay.server.internal.handler.control;

import com.github.serezhka.airplay.server.internal.handler.session.Session;
import com.github.serezhka.airplay.server.internal.handler.session.SessionManager;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.rtsp.RtspVersions;
import io.netty.util.ReferenceCountUtil;

public final class ControlHandlerReleaseTest {
    public static void main(String[] args) {
        handledRequestIsReleased();
        forwardedRequestKeepsItsReference();
        failedRequestIsReleased();
        System.out.println("ControlHandler release tests passed");
    }

    private static void handledRequestIsReleased() {
        FullHttpRequest request = request();
        EmbeddedChannel channel = new EmbeddedChannel(new TestHandler(Mode.HANDLE));

        channel.writeInbound(request);

        assertRefCount(request, 0, "handled request");
        channel.finishAndReleaseAll();
    }

    private static void forwardedRequestKeepsItsReference() {
        FullHttpRequest request = request();
        EmbeddedChannel channel = new EmbeddedChannel(new TestHandler(Mode.FORWARD));

        channel.writeInbound(request);
        Object forwarded = channel.readInbound();

        if (forwarded != request) {
            throw new AssertionError("Unhandled request was not forwarded unchanged");
        }
        assertRefCount(request, 1, "forwarded request");
        ReferenceCountUtil.release(forwarded);
        channel.finishAndReleaseAll();
    }

    private static void failedRequestIsReleased() {
        FullHttpRequest request = request();
        EmbeddedChannel channel = new EmbeddedChannel(new TestHandler(Mode.THROW));

        try {
            channel.writeInbound(request);
            throw new AssertionError("Expected request handling to fail");
        } catch (IllegalStateException expected) {
            // Expected.
        }

        assertRefCount(request, 0, "failed request");
        channel.finishAndReleaseAll();
    }

    private static FullHttpRequest request() {
        return new DefaultFullHttpRequest(
                RtspVersions.RTSP_1_0,
                HttpMethod.POST,
                "/feedback",
                Unpooled.buffer(8).writeLong(1L));
    }

    private static void assertRefCount(FullHttpRequest request, int expected, String description) {
        if (request.refCnt() != expected) {
            throw new AssertionError(description + " refCnt expected " + expected
                    + " but was " + request.refCnt());
        }
    }

    private enum Mode {
        HANDLE,
        FORWARD,
        THROW
    }

    private static final class TestHandler extends ControlHandler {
        private final Mode mode;

        private TestHandler(Mode mode) {
            super(new SessionManager());
            this.mode = mode;
        }

        @Override
        protected boolean handleRequest(
                ChannelHandlerContext ctx, Session session, FullHttpRequest request) {
            if (mode == Mode.THROW) {
                throw new IllegalStateException("test failure");
            }
            return mode == Mode.HANDLE;
        }
    }
}
