package com.github.serezhka.airplay.player.gstreamer;

import org.freedesktop.gstreamer.Gst;
import org.freedesktop.gstreamer.Pipeline;

public class GstPlayerFullscreen extends GstPlayer {
    @Override
    protected Pipeline createH264Pipeline() {
        return (Pipeline) Gst.parseLaunch(GstVideoPipeline.fullscreen());
    }
}
