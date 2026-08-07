package com.github.serezhka.airplay.player.gstreamer;

import java.util.function.Consumer;

public interface FullscreenController {
    boolean isFullscreen();

    void setFullscreen(boolean fullscreen);

    void addFullscreenListener(Consumer<Boolean> listener);
}
