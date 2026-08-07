package com.github.serezhka.airplay.player.gstreamer;

import com.github.serezhka.airplay.app.config.PlayerConfig;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Method;

public final class GstFullscreenConfigurationTest {
    private static final String EXPECTED_PIPELINE =
            "appsrc name=h264-src ! h264parse ! avdec_h264 ! "
                    + "d3d11videosink name=video-sink fullscreen-toggle-mode=property "
                    + "enable-navigation-events=true fullscreen=false sync=false";

    public static void main(String[] args) throws Exception {
        controllablePipelineUsesTheNativeBorderlessMode();
        playerConfigurationExposesTheFullscreenSwitch();
        nativePlayerExposesRuntimeFullscreenControl();
        nativeWindowKeyboardBindingsAreMapped();
        System.out.println("GStreamer fullscreen configuration tests passed");
    }

    private static void controllablePipelineUsesTheNativeBorderlessMode() {
        assertEquals(EXPECTED_PIPELINE, GstVideoPipeline.controllable(), "controllable pipeline");
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
    }

    private static void nativePlayerExposesRuntimeFullscreenControl() throws Exception {
        assertTrue(
                FullscreenController.class.isAssignableFrom(GstPlayerFullscreen.class),
                "native player must implement runtime fullscreen control");
        GstPlayerFullscreen.class.getDeclaredConstructor(boolean.class);
        GstPlayerFullscreen.class.getMethod("isFullscreen");
        GstPlayerFullscreen.class.getMethod("setFullscreen", boolean.class);
        GstPlayerFullscreen.class.getMethod("addFullscreenListener", java.util.function.Consumer.class);
    }

    private static void nativeWindowKeyboardBindingsAreMapped() {
        assertTrue(FullscreenKeyBindings.isToggle("key-release", "F11"), "F11 release");
        assertTrue(!FullscreenKeyBindings.isToggle("key-press", "F11"), "F11 press");
        assertTrue(FullscreenKeyBindings.isWindowed("key-release", "Esc"), "Esc release");
        assertTrue(FullscreenKeyBindings.isWindowed("key-release", "Escape"), "Escape release");
        assertTrue(!FullscreenKeyBindings.isWindowed("key-press", "Esc"), "Esc press");
        assertTrue(!FullscreenKeyBindings.isWindowed("key-release", "Enter"), "unrelated key");
    }

    private static void assertEquals(String expected, String actual, String description) {
        if (!expected.equals(actual)) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertTrue(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
