package com.github.serezhka.airplay.player.gstreamer;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Element;
import org.freedesktop.gstreamer.Pad;
import org.freedesktop.gstreamer.PadProbeInfo;
import org.freedesktop.gstreamer.PadProbeReturn;
import org.freedesktop.gstreamer.PadProbeType;
import org.freedesktop.gstreamer.Pipeline;
import org.freedesktop.gstreamer.Structure;
import org.freedesktop.gstreamer.event.Event;
import org.freedesktop.gstreamer.event.NavigationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class GstPlayerFullscreen extends GstPlayer implements FullscreenController {
    private static final Logger log = LoggerFactory.getLogger(GstPlayerFullscreen.class);

    private final Element videoSink;
    private final Pad navigationPad;
    private final Pad.PROBE navigationProbe;
    private final CopyOnWriteArrayList<Consumer<Boolean>> fullscreenListeners = new CopyOnWriteArrayList<>();
    private volatile boolean fullscreen;

    public GstPlayerFullscreen() {
        this(true);
    }

    public GstPlayerFullscreen(boolean fullscreen) {
        videoSink = Objects.requireNonNull(
                h264Pipeline.getElementByName("video-sink"),
                "GStreamer video sink is missing");
        navigationPad = Objects.requireNonNull(
                h264Pipeline.getElementByName("h264-src").getStaticPad("src"),
                "GStreamer video source pad is missing");
        navigationProbe = this::handleNavigationEvent;
        navigationPad.addProbe(PadProbeType.EVENT_UPSTREAM, navigationProbe);
        setFullscreen(fullscreen);
    }

    @Override
    protected Pipeline createH264Pipeline() {
        return (Pipeline) Gst.parseLaunch(GstVideoPipeline.controllable());
    }

    @Override
    public boolean isFullscreen() {
        return fullscreen;
    }

    @Override
    public synchronized void setFullscreen(boolean fullscreen) {
        if (this.fullscreen == fullscreen) {
            return;
        }
        withVideoPipelineLock(() -> {
            videoSink.set("fullscreen", fullscreen);
            this.fullscreen = fullscreen;
        });
        notifyFullscreenListeners(fullscreen);
    }

    @Override
    public synchronized void addFullscreenListener(Consumer<Boolean> listener) {
        Consumer<Boolean> checkedListener = Objects.requireNonNull(listener, "listener");
        fullscreenListeners.add(checkedListener);
        checkedListener.accept(fullscreen);
    }

    private PadProbeReturn handleNavigationEvent(Pad pad, PadProbeInfo probeInfo) {
        try {
            Event event = probeInfo.getEvent();
            if (!(event instanceof NavigationEvent)) {
                return PadProbeReturn.OK;
            }
            Structure structure = event.getStructure();
            if (structure == null || !structure.hasName("application/x-gst-navigation")) {
                return PadProbeReturn.OK;
            }

            String eventType = structure.getString("event");
            String key = structure.getString("key");
            if (FullscreenKeyBindings.isToggle(eventType, key)) {
                Gst.getExecutor().execute(this::toggleFullscreenFromKeyboard);
            } else if (FullscreenKeyBindings.isWindowed(eventType, key)) {
                Gst.getExecutor().execute(this::exitFullscreenFromKeyboard);
            }
        } catch (Throwable exception) {
            log.warn("Unable to handle GStreamer fullscreen keyboard event", exception);
        }
        return PadProbeReturn.OK;
    }

    private synchronized void toggleFullscreenFromKeyboard() {
        try {
            setFullscreen(!fullscreen);
        } catch (Throwable exception) {
            log.warn("Unable to toggle GStreamer fullscreen mode from F11", exception);
        }
    }

    private void exitFullscreenFromKeyboard() {
        try {
            setFullscreen(false);
        } catch (Throwable exception) {
            log.warn("Unable to exit GStreamer fullscreen mode from Escape", exception);
        }
    }

    private void notifyFullscreenListeners(boolean fullscreen) {
        for (Consumer<Boolean> listener : fullscreenListeners) {
            try {
                listener.accept(fullscreen);
            } catch (Throwable exception) {
                log.warn("Unable to notify a GStreamer fullscreen listener", exception);
            }
        }
    }
}
