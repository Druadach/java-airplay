package com.github.serezhka.airplay.player.gstreamer;

import com.github.serezhka.airplay.app.config.PlayerConfig;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Method;

public final class GstFullscreenConfigurationTest {
    private static final String EXPECTED_PIPELINE =
            "appsrc name=h264-src ! h264parse ! avdec_h264 ! "
                    + "d3d11videosink name=video-sink fullscreen-toggle-mode=property "
                    + "fullscreen=true sync=false";

    public static void main(String[] args) throws Exception {
        fullscreenPipelineUsesTheNativeBorderlessMode();
        playerConfigurationExposesTheFullscreenSwitch();
        System.out.println("GStreamer fullscreen configuration tests passed");
    }

    private static void fullscreenPipelineUsesTheNativeBorderlessMode() {
        assertEquals(EXPECTED_PIPELINE, GstVideoPipeline.fullscreen(), "fullscreen pipeline");
    }

    private static void playerConfigurationExposesTheFullscreenSwitch() throws Exception {
        Method method = PlayerConfig.class.getMethod("gstreamer", boolean.class, boolean.class);
        Value annotation = method.getParameters()[1].getAnnotation(Value.class);

        if (annotation == null) {
            throw new AssertionError("fullscreen parameter is missing @Value");
        }
        assertEquals(
                "#{new Boolean('${player.gstreamer.fullscreen:true}')}",
                annotation.value(),
                "fullscreen property binding");
        GstPlayerFullscreen.class.getDeclaredConstructor();
    }

    private static void assertEquals(String expected, String actual, String description) {
        if (!expected.equals(actual)) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }
}
