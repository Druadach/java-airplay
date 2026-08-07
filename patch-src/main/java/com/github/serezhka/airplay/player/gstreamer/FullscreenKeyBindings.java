package com.github.serezhka.airplay.player.gstreamer;

final class FullscreenKeyBindings {
    private FullscreenKeyBindings() {
    }

    static boolean isToggle(String eventType, String key) {
        return "key-release".equals(eventType) && key != null && "F11".equalsIgnoreCase(key);
    }

    static boolean isWindowed(String eventType, String key) {
        return "key-release".equals(eventType)
                && key != null
                && ("Esc".equalsIgnoreCase(key) || "Escape".equalsIgnoreCase(key));
    }
}
