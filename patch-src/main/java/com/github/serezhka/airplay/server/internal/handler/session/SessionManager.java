package com.github.serezhka.airplay.server.internal.handler.session;

import java.util.HashMap;
import java.util.Map;

public class SessionManager {

    private final Map<String, Session> sessions = new HashMap<>();

    private ControlSession activeControlSession;
    private MediaLease videoLease;
    private MediaLease audioLease;

    public Session getSession(String activeRemote) {
        synchronized (sessions) {
            Session session = sessions.get(activeRemote);
            if (session == null) {
                session = new Session();
                sessions.put(activeRemote, session);
            }
            return session;
        }
    }

    public synchronized ControlSession openControlSession(Session session, Runnable closeAction) {
        return new ControlSession(session, closeAction);
    }

    synchronized Activation claimActiveControl(ControlSession controlSession) {
        if (controlSession.closed || controlSession.revoked) {
            return Activation.rejected();
        }

        ControlSession previousControlSession = activeControlSession;
        if (previousControlSession == controlSession) {
            return new Activation(true, previousControlSession, false, false);
        }

        boolean hadVideoLease = videoLease != null;
        boolean hadAudioLease = audioLease != null;
        if (previousControlSession != null) {
            previousControlSession.revoked = true;
        }
        activeControlSession = controlSession;
        videoLease = null;
        audioLease = null;
        return new Activation(true, previousControlSession, hadVideoLease, hadAudioLease);
    }

    synchronized boolean isActiveSession(Session session) {
        return activeControlSession != null && activeControlSession.session == session;
    }

    synchronized boolean isActiveControl(ControlSession controlSession) {
        return activeControlSession == controlSession;
    }

    synchronized void releaseActiveControl(ControlSession controlSession) {
        if (activeControlSession == controlSession) {
            activeControlSession = null;
            videoLease = null;
            audioLease = null;
        }
    }

    synchronized void closeControlSession(ControlSession controlSession) {
        controlSession.closed = true;
        controlSession.revoked = true;
        releaseActiveControl(controlSession);
    }

    synchronized MediaLease openVideoLease(ControlSession controlSession) {
        requireActiveControl(controlSession);
        videoLease = new MediaLease();
        return videoLease;
    }

    synchronized MediaLease openAudioLease(ControlSession controlSession) {
        requireActiveControl(controlSession);
        audioLease = new MediaLease();
        return audioLease;
    }

    synchronized boolean ownsVideoLease(ControlSession controlSession, MediaLease lease) {
        return activeControlSession == controlSession && videoLease == lease;
    }

    synchronized boolean ownsAudioLease(ControlSession controlSession, MediaLease lease) {
        return activeControlSession == controlSession && audioLease == lease;
    }

    synchronized boolean hasVideoLease(ControlSession controlSession) {
        return activeControlSession == controlSession && videoLease != null;
    }

    synchronized boolean hasAudioLease(ControlSession controlSession) {
        return activeControlSession == controlSession && audioLease != null;
    }

    synchronized void closeVideoLease(ControlSession controlSession) {
        if (activeControlSession == controlSession) {
            videoLease = null;
        }
    }

    synchronized void closeVideoLease(ControlSession controlSession, MediaLease lease) {
        if (ownsVideoLease(controlSession, lease)) {
            videoLease = null;
        }
    }

    synchronized void closeAudioLease(ControlSession controlSession) {
        if (activeControlSession == controlSession) {
            audioLease = null;
        }
    }

    synchronized void closeAudioLease(ControlSession controlSession, MediaLease lease) {
        if (ownsAudioLease(controlSession, lease)) {
            audioLease = null;
        }
    }

    private void requireActiveControl(ControlSession controlSession) {
        if (activeControlSession != controlSession) {
            throw new IllegalStateException("Cannot open media for an inactive AirPlay control session");
        }
    }

    static final class Activation {
        final boolean accepted;
        final ControlSession previousControlSession;
        final boolean hadVideoLease;
        final boolean hadAudioLease;

        private Activation(
                boolean accepted,
                ControlSession previousControlSession,
                boolean hadVideoLease,
                boolean hadAudioLease) {
            this.accepted = accepted;
            this.previousControlSession = previousControlSession;
            this.hadVideoLease = hadVideoLease;
            this.hadAudioLease = hadAudioLease;
        }

        private static Activation rejected() {
            return new Activation(false, null, false, false);
        }
    }

    static final class MediaLease {
    }

    public static final class ControlSession {
        private final Session session;
        private final Runnable closeAction;
        private boolean revoked;
        private boolean closed;
        private boolean closeRequested;

        private ControlSession(Session session, Runnable closeAction) {
            this.session = session;
            this.closeAction = closeAction;
        }

        public Session getSession() {
            return session;
        }

        void closeChannel() {
            if (!closeRequested) {
                closeRequested = true;
                closeAction.run();
            }
        }
    }
}
