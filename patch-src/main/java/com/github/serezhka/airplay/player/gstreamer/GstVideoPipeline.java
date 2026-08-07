package com.github.serezhka.airplay.player.gstreamer;

final class GstVideoPipeline {
    private static final String H264_DECODER =
            "appsrc name=h264-src ! h264parse ! avdec_h264 ! ";

    private GstVideoPipeline() {
    }

    static String fullscreen() {
        return H264_DECODER
                + "d3d11videosink name=video-sink fullscreen-toggle-mode=property "
                + "fullscreen=true sync=false";
    }
}
