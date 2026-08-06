package com.github.serezhka.airplay.server.internal.handler.session;

import com.github.serezhka.airplay.lib.AudioStreamInfo;
import com.github.serezhka.airplay.lib.VideoStreamInfo;
import com.github.serezhka.airplay.server.AirPlayConsumer;

import java.util.Objects;

public final class SessionMediaCoordinator {

    private final SessionManager sessionManager;
    private final AirPlayConsumer delegate;

    public SessionMediaCoordinator(SessionManager sessionManager, AirPlayConsumer delegate) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public AirPlayConsumer prepareVideo(
            SessionManager.ControlSession controlSession, VideoStreamInfo videoStreamInfo) {
        synchronized (sessionManager) {
            if (!claimControl(controlSession)) {
                return null;
            }

            Session session = controlSession.getSession();
            if (sessionManager.hasVideoLease(controlSession)) {
                sessionManager.closeVideoLease(controlSession);
                session.stopVideo();
                delegate.onVideoSrcDisconnect();
            }

            SessionManager.MediaLease lease = sessionManager.openVideoLease(controlSession);
            try {
                delegate.onVideoFormat(videoStreamInfo);
            } catch (RuntimeException | Error exception) {
                sessionManager.closeVideoLease(controlSession, lease);
                releaseIfIdle(controlSession);
                throw exception;
            }
            return new SessionAirPlayConsumer(
                    sessionManager,
                    controlSession,
                    lease,
                    SessionAirPlayConsumer.StreamKind.VIDEO,
                    delegate);
        }
    }

    public AirPlayConsumer prepareAudio(
            SessionManager.ControlSession controlSession, AudioStreamInfo audioStreamInfo) {
        synchronized (sessionManager) {
            if (!claimControl(controlSession)) {
                return null;
            }

            Session session = controlSession.getSession();
            if (sessionManager.hasAudioLease(controlSession)) {
                sessionManager.closeAudioLease(controlSession);
                session.stopAudio();
                delegate.onAudioSrcDisconnect();
            }

            SessionManager.MediaLease lease = sessionManager.openAudioLease(controlSession);
            try {
                delegate.onAudioFormat(audioStreamInfo);
            } catch (RuntimeException | Error exception) {
                sessionManager.closeAudioLease(controlSession, lease);
                releaseIfIdle(controlSession);
                throw exception;
            }
            return new SessionAirPlayConsumer(
                    sessionManager,
                    controlSession,
                    lease,
                    SessionAirPlayConsumer.StreamKind.AUDIO,
                    delegate);
        }
    }

    public void disconnectVideo(SessionManager.ControlSession controlSession) {
        synchronized (sessionManager) {
            if (!sessionManager.hasVideoLease(controlSession)) {
                return;
            }

            sessionManager.closeVideoLease(controlSession);
            controlSession.getSession().stopVideo();
            delegate.onVideoSrcDisconnect();
            releaseIfIdle(controlSession);
        }
    }

    public void disconnectAudio(SessionManager.ControlSession controlSession) {
        synchronized (sessionManager) {
            if (!sessionManager.hasAudioLease(controlSession)) {
                return;
            }

            sessionManager.closeAudioLease(controlSession);
            controlSession.getSession().stopAudio();
            delegate.onAudioSrcDisconnect();
            releaseIfIdle(controlSession);
        }
    }

    public void disconnectAll(SessionManager.ControlSession controlSession) {
        synchronized (sessionManager) {
            if (!sessionManager.isActiveControl(controlSession)) {
                return;
            }

            boolean disconnectVideo = sessionManager.hasVideoLease(controlSession);
            boolean disconnectAudio = sessionManager.hasAudioLease(controlSession);
            sessionManager.closeVideoLease(controlSession);
            sessionManager.closeAudioLease(controlSession);
            Session session = controlSession.getSession();
            session.stopVideo();
            session.stopAudio();
            if (disconnectVideo) {
                delegate.onVideoSrcDisconnect();
            }
            if (disconnectAudio) {
                delegate.onAudioSrcDisconnect();
            }
            sessionManager.releaseActiveControl(controlSession);
        }
    }

    public void controlDisconnected(SessionManager.ControlSession controlSession) {
        synchronized (sessionManager) {
            boolean wasActive = sessionManager.isActiveControl(controlSession);
            boolean disconnectVideo = sessionManager.hasVideoLease(controlSession);
            boolean disconnectAudio = sessionManager.hasAudioLease(controlSession);
            sessionManager.closeControlSession(controlSession);
            if (!wasActive) {
                return;
            }

            Session session = controlSession.getSession();
            session.stopVideo();
            session.stopAudio();
            if (disconnectVideo) {
                delegate.onVideoSrcDisconnect();
            }
            if (disconnectAudio) {
                delegate.onAudioSrcDisconnect();
            }
        }
    }

    private boolean claimControl(SessionManager.ControlSession controlSession) {
        SessionManager.Activation activation = sessionManager.claimActiveControl(controlSession);
        if (!activation.accepted) {
            return false;
        }

        SessionManager.ControlSession previousControlSession = activation.previousControlSession;
        if (previousControlSession == null || previousControlSession == controlSession) {
            return true;
        }

        Session previousSession = previousControlSession.getSession();

        previousSession.stopVideo();
        previousSession.stopAudio();
        if (activation.hadVideoLease) {
            delegate.onVideoSrcDisconnect();
        }
        if (activation.hadAudioLease) {
            delegate.onAudioSrcDisconnect();
        }
        previousControlSession.closeChannel();
        return true;
    }

    private void releaseIfIdle(SessionManager.ControlSession controlSession) {
        if (!sessionManager.hasVideoLease(controlSession)
                && !sessionManager.hasAudioLease(controlSession)) {
            sessionManager.releaseActiveControl(controlSession);
        }
    }
}
