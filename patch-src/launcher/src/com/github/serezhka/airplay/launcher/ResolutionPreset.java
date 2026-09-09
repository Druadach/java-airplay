package com.github.serezhka.airplay.launcher;

enum ResolutionPreset {
    HD(1280, 720, "720p"),
    FULL_HD(1920, 1080, "1080p"),
    QUAD_HD(2560, 1440, "1440p"),
    ULTRA_HD(3840, 2160, "4K"),
    CUSTOM(0, 0, "");

    private final int width;
    private final int height;
    private final String label;

    ResolutionPreset(int width, int height, String label) {
        this.width = width;
        this.height = height;
        this.label = label;
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    String label(UiLanguage language) {
        return this == CUSTOM
                ? LauncherMessages.text(language, LauncherMessages.Key.RESOLUTION_CUSTOM)
                : label + " (" + width + " × " + height + ")";
    }

    static ResolutionPreset forSize(int width, int height) {
        for (ResolutionPreset preset : values()) {
            if (preset != CUSTOM && preset.width == width && preset.height == height) {
                return preset;
            }
        }
        return CUSTOM;
    }
}
