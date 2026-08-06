package com.github.serezhka.airplay.server.internal.handler.control;

import com.github.serezhka.airplay.lib.AudioStreamInfo;
import com.github.serezhka.airplay.lib.MediaStreamInfo;
import com.github.serezhka.airplay.lib.VideoStreamInfo;
import com.github.serezhka.airplay.server.AirPlayConsumer;
import com.github.serezhka.airplay.server.internal.AudioControlServer;
import com.github.serezhka.airplay.server.internal.AudioReceiver;
import com.github.serezhka.airplay.server.internal.VideoReceiver;
import com.github.serezhka.airplay.server.internal.handler.audio.AudioHandler;
import com.github.serezhka.airplay.server.internal.handler.session.Session;
import com.github.serezhka.airplay.server.internal.handler.session.SessionManager;
import com.github.serezhka.airplay.server.internal.handler.session.SessionMediaCoordinator;
import com.github.serezhka.airplay.server.internal.handler.video.VideoHandler;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.rtsp.RtspMethods;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.function.IntSupplier;

@ChannelHandler.Sharable
public class RTSPHandler extends ControlHandler {

    private static final Logger log = LoggerFactory.getLogger(RTSPHandler.class);
    private static final long RECEIVER_START_TIMEOUT_MILLIS = 10_000;
    private static final AttributeKey<SessionManager.ControlSession> CONTROL_SESSION_KEY =
            AttributeKey.valueOf("airplay.rtsp.control-session");

    private final int airTunesPort;
    private final SessionManager sessionManager;
    private final SessionMediaCoordinator mediaCoordinator;

    public RTSPHandler(int airTunesPort, SessionManager sessionManager, AirPlayConsumer airPlayConsumer) {
        super(sessionManager);
        this.airTunesPort = airTunesPort;
        this.sessionManager = sessionManager;
        this.mediaCoordinator = new SessionMediaCoordinator(sessionManager, airPlayConsumer);
    }

    @Override
    protected boolean handleRequest(
            ChannelHandlerContext ctx, Session session, FullHttpRequest request) throws Exception {
        var response = createResponseForRequest(request);
        if (RtspMethods.SETUP.equals(request.method())) {
            MediaStreamInfo mediaStreamInfo = session.getAirPlay().rtspGetMediaStreamInfo(
                    new ByteBufInputStream(request.content()));
            if (mediaStreamInfo == null) {
                request.content().resetReaderIndex();
                session.getAirPlay().rtspSetupEncryption(new ByteBufInputStream(request.content()));
            } else if (mediaStreamInfo.getStreamType() == MediaStreamInfo.StreamType.AUDIO) {
                SessionManager.ControlSession controlSession = controlSession(ctx, session);
                if (!setupAudio(controlSession, (AudioStreamInfo) mediaStreamInfo, response.content())) {
                    response.setStatus(HttpResponseStatus.SERVICE_UNAVAILABLE);
                }
            } else if (mediaStreamInfo.getStreamType() == MediaStreamInfo.StreamType.VIDEO) {
                SessionManager.ControlSession controlSession = controlSession(ctx, session);
                if (!setupVideo(controlSession, (VideoStreamInfo) mediaStreamInfo, response.content())) {
                    response.setStatus(HttpResponseStatus.SERVICE_UNAVAILABLE);
                }
            }
            return sendResponse(ctx, request, response);
        } else if (RtspMethods.GET_PARAMETER.equals(request.method())) {
            byte[] content = "volume: 1.000000\r\n".getBytes(StandardCharsets.US_ASCII);
            response.content().writeBytes(content);
            return sendResponse(ctx, request, response);
        } else if (RtspMethods.RECORD.equals(request.method())) {
            response.headers().add("Audio-Latency", "11025");
            response.headers().add("Audio-Jack-Status", "connected; type=analog");
            return sendResponse(ctx, request, response);
        } else if (RtspMethods.SET_PARAMETER.equals(request.method())) {
            return sendResponse(ctx, request, response);
        } else if ("FLUSH".equals(request.method().toString())) {
            return sendResponse(ctx, request, response);
        } else if (RtspMethods.TEARDOWN.equals(request.method())) {
            MediaStreamInfo mediaStreamInfo = session.getAirPlay().rtspGetMediaStreamInfo(
                    new ByteBufInputStream(request.content()));
            SessionManager.ControlSession controlSession =
                    ctx.channel().attr(CONTROL_SESSION_KEY).get();
            if (controlSession != null && controlSession.getSession() == session) {
                if (mediaStreamInfo == null) {
                    mediaCoordinator.disconnectAll(controlSession);
                } else if (mediaStreamInfo.getStreamType() == MediaStreamInfo.StreamType.AUDIO) {
                    mediaCoordinator.disconnectAudio(controlSession);
                } else if (mediaStreamInfo.getStreamType() == MediaStreamInfo.StreamType.VIDEO) {
                    mediaCoordinator.disconnectVideo(controlSession);
                }
            }
            return sendResponse(ctx, request, response);
        } else if ("POST".equals(request.method().toString()) && request.uri().equals("/audioMode")) {
            return sendResponse(ctx, request, response);
        }
        return false;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        try {
            SessionManager.ControlSession controlSession =
                    ctx.channel().attr(CONTROL_SESSION_KEY).getAndSet(null);
            if (controlSession != null) {
                mediaCoordinator.controlDisconnected(controlSession);
            }
        } finally {
            ctx.fireChannelInactive();
        }
    }

    private boolean setupAudio(
            SessionManager.ControlSession controlSession,
            AudioStreamInfo audioStreamInfo,
            io.netty.buffer.ByteBuf responseContent)
            throws Exception {
        synchronized (sessionManager) {
            log.info("Audio format is: {}", audioStreamInfo.getAudioFormat());
            log.info("Audio compression type is: {}", audioStreamInfo.getCompressionType());
            log.info("Audio samples per frame is: {}", audioStreamInfo.getSamplesPerFrame());

            AirPlayConsumer sessionConsumer = mediaCoordinator.prepareAudio(
                    controlSession, audioStreamInfo);
            if (sessionConsumer == null) {
                return false;
            }

            Session session = controlSession.getSession();
            try {
                Object audioReceiverMonitor = new Object();
                var audioHandler = new AudioHandler(session.getAirPlay(), sessionConsumer);
                var audioReceiver = new AudioReceiver(audioHandler, audioReceiverMonitor);
                var audioReceiverThread = new Thread(audioReceiver, "airplay-audio-receiver");
                session.setAudioReceiverThread(audioReceiverThread);
                startAndAwaitPort(
                        audioReceiverMonitor,
                        audioReceiverThread,
                        audioReceiver::getPort,
                        "audio receiver");

                Object audioControlMonitor = new Object();
                var audioControlServer = new AudioControlServer(audioControlMonitor);
                var audioControlServerThread = new Thread(
                        audioControlServer, "airplay-audio-control");
                session.setAudioControlServerThread(audioControlServerThread);
                startAndAwaitPort(
                        audioControlMonitor,
                        audioControlServerThread,
                        audioControlServer::getPort,
                        "audio control server");

                session.getAirPlay().rtspSetupAudio(
                        new ByteBufOutputStream(responseContent),
                        audioReceiver.getPort(),
                        audioControlServer.getPort());
            } catch (Exception exception) {
                mediaCoordinator.disconnectAudio(controlSession);
                throw exception;
            } catch (Error error) {
                mediaCoordinator.disconnectAudio(controlSession);
                throw error;
            }
            return true;
        }
    }

    private boolean setupVideo(
            SessionManager.ControlSession controlSession,
            VideoStreamInfo videoStreamInfo,
            io.netty.buffer.ByteBuf responseContent)
            throws Exception {
        synchronized (sessionManager) {
            AirPlayConsumer sessionConsumer = mediaCoordinator.prepareVideo(
                    controlSession, videoStreamInfo);
            if (sessionConsumer == null) {
                return false;
            }

            Session session = controlSession.getSession();
            try {
                Object videoReceiverMonitor = new Object();
                var videoHandler = new VideoHandler(session.getAirPlay(), sessionConsumer);
                var videoReceiver = new VideoReceiver(videoHandler, videoReceiverMonitor);
                var videoReceiverThread = new Thread(videoReceiver, "airplay-video-receiver");
                session.setVideoReceiverThread(videoReceiverThread);
                startAndAwaitPort(
                        videoReceiverMonitor,
                        videoReceiverThread,
                        videoReceiver::getPort,
                        "video receiver");

                session.getAirPlay().rtspSetupVideo(
                        new ByteBufOutputStream(responseContent),
                        videoReceiver.getPort(),
                        airTunesPort,
                        7011);
            } catch (Exception exception) {
                mediaCoordinator.disconnectVideo(controlSession);
                throw exception;
            } catch (Error error) {
                mediaCoordinator.disconnectVideo(controlSession);
                throw error;
            }
            return true;
        }
    }

    private SessionManager.ControlSession controlSession(
            ChannelHandlerContext ctx, Session session) {
        Attribute<SessionManager.ControlSession> attribute =
                ctx.channel().attr(CONTROL_SESSION_KEY);
        SessionManager.ControlSession controlSession = attribute.get();
        if (controlSession != null && controlSession.getSession() == session) {
            return controlSession;
        }
        if (controlSession != null) {
            mediaCoordinator.controlDisconnected(controlSession);
        }

        SessionManager.ControlSession newControlSession = sessionManager.openControlSession(
                session, () -> ctx.close());
        attribute.set(newControlSession);
        return newControlSession;
    }

    private static void startAndAwaitPort(
            Object monitor,
            Thread receiverThread,
            IntSupplier portSupplier,
            String componentName) throws InterruptedException {
        long deadline = System.currentTimeMillis() + RECEIVER_START_TIMEOUT_MILLIS;
        synchronized (monitor) {
            receiverThread.start();
            while (portSupplier.getAsInt() == 0 && receiverThread.isAlive()) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                monitor.wait(Math.min(remaining, 100));
            }
        }

        if (portSupplier.getAsInt() == 0) {
            receiverThread.interrupt();
            throw new IllegalStateException("AirPlay " + componentName + " did not start");
        }
    }
}
