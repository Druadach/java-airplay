package com.github.serezhka.airplay.server.internal.handler.session;

import com.github.serezhka.airplay.lib.AudioStreamInfo;
import com.github.serezhka.airplay.lib.VideoStreamInfo;
import com.github.serezhka.airplay.server.AirPlayConsumer;

final class SessionAirPlayConsumer implements AirPlayConsumer {

    enum StreamKind {
        VIDEO,
        AUDIO
    }

    private final SessionManager sessionManager;
    private final SessionManager.ControlSession controlSession;
    private final SessionManager.MediaLease lease;
    private final StreamKind streamKind;
    private final AirPlayConsumer delegate;

    SessionAirPlayConsumer(
            SessionManager sessionManager,
            SessionManager.ControlSession controlSession,
            SessionManager.MediaLease lease,
            StreamKind streamKind,
            AirPlayConsumer delegate) {
        this.sessionManager = sessionManager;
        this.controlSession = controlSession;
        this.lease = lease;
        this.streamKind = streamKind;
        this.delegate = delegate;
    }

    @Override
    public void onVideoFormat(VideoStreamInfo videoStreamInfo) {
        if (streamKind != StreamKind.VIDEO) {
            return;
        }
        synchronized (sessionManager) {
            if (sessionManager.ownsVideoLease(controlSession, lease)) {
                delegate.onVideoFormat(videoStreamInfo);
            }
        }
    }

    @Override
    public void onVideo(byte[] bytes) {
        if (streamKind != StreamKind.VIDEO) {
            return;
        }
        synchronized (sessionManager) {
            if (sessionManager.ownsVideoLease(controlSession, lease)) {
                delegate.onVideo(bytes);
            }
        }
    }

    @Override
    public void onVideoSrcDisconnect() {
        if (streamKind != StreamKind.VIDEO) {
            return;
        }
        synchronized (sessionManager) {
            if (sessionManager.ownsVideoLease(controlSession, lease)) {
                delegate.onVideoSrcDisconnect();
            }
        }
    }

    @Override
    public void onAudioFormat(AudioStreamInfo audioStreamInfo) {
        if (streamKind != StreamKind.AUDIO) {
            return;
        }
        synchronized (sessionManager) {
            if (sessionManager.ownsAudioLease(controlSession, lease)) {
                delegate.onAudioFormat(audioStreamInfo);
            }
        }
    }

    @Override
    public void onAudio(byte[] bytes) {
        if (streamKind != StreamKind.AUDIO) {
            return;
        }
        synchronized (sessionManager) {
            if (sessionManager.ownsAudioLease(controlSession, lease)) {
                delegate.onAudio(bytes);
            }
        }
    }

    @Override
    public void onAudioSrcDisconnect() {
        if (streamKind != StreamKind.AUDIO) {
            return;
        }
        synchronized (sessionManager) {
            if (sessionManager.ownsAudioLease(controlSession, lease)) {
                delegate.onAudioSrcDisconnect();
            }
        }
    }
}
