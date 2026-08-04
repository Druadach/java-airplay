package com.github.serezhka.airplay.server.internal.handler.control;

import com.github.serezhka.airplay.server.internal.handler.session.Session;
import com.github.serezhka.airplay.server.internal.handler.session.SessionManager;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.rtsp.RtspResponseStatuses;
import io.netty.handler.codec.rtsp.RtspVersions;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ControlHandler extends ChannelInboundHandlerAdapter {
    private static final Logger log = LoggerFactory.getLogger(ControlHandler.class);
    private static final String HEADER_CSEQ = "CSeq";
    private static final String HEADER_ACTIVE_REMOTE = "Active-Remote";

    private final SessionManager sessionManager;

    protected ControlHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public final void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest request)) {
            super.channelRead(ctx, msg);
            return;
        }

        boolean releaseRequest = true;
        try {
            if (!handleRequest(ctx, request)) {
                releaseRequest = false;
                super.channelRead(ctx, msg);
            }
        } finally {
            if (releaseRequest) {
                ReferenceCountUtil.release(request);
            }
        }
    }

    private boolean handleRequest(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        Session session = sessionManager.getSession(request.headers().get(HEADER_ACTIVE_REMOTE));
        return handleRequest(ctx, session, request);
    }

    protected abstract boolean handleRequest(
            ChannelHandlerContext ctx, Session session, FullHttpRequest request) throws Exception;

    protected DefaultFullHttpResponse createResponseForRequest(FullHttpRequest request) {
        DefaultFullHttpResponse response =
                new DefaultFullHttpResponse(RtspVersions.RTSP_1_0, RtspResponseStatuses.OK);
        response.headers().clear();

        String cSeq = request.headers().get(HEADER_CSEQ);
        if (cSeq != null) {
            response.headers().add(HEADER_CSEQ, cSeq);
        }
        return response;
    }

    protected boolean sendResponse(
            ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        HttpUtil.setContentLength(response, response.content().readableBytes());
        ChannelFuture future = ctx.writeAndFlush(response);
        if (!HttpUtil.isKeepAlive(request)) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
        log.info("AirPlay request {} {} is handled!", request.method(), request.uri());
        return true;
    }
}
