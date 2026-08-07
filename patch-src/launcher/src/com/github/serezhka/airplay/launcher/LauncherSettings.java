package com.github.serezhka.airplay.launcher;

import java.util.Set;

record LauncherSettings(
        String serverName,
        int airtunesPort,
        int width,
        int height,
        int fps,
        String playerImplementation,
        boolean startFullscreen,
        UiLanguage language) {

    private static final Set<String> PLAYERS = Set.of("gstreamer", "ffmpeg", "vlc", "h264-dump");

    LauncherSettings {
        serverName = serverName == null ? "" : serverName.trim();
        playerImplementation = playerImplementation == null ? "" : playerImplementation.trim();
        language = language == null ? UiLanguage.systemDefault() : language;
        if (serverName.isEmpty() || serverName.length() > 64) {
            throw new LauncherInputException(LauncherMessages.Key.VALIDATION_SERVER_NAME);
        }
        if (airtunesPort < 1 || airtunesPort > 65535) {
            throw new LauncherInputException(LauncherMessages.Key.VALIDATION_SERVER_PORT);
        }
        if (width < 320 || width > 7680 || height < 240 || height > 4320) {
            throw new LauncherInputException(LauncherMessages.Key.VALIDATION_RESOLUTION);
        }
        if (fps < 1 || fps > 240) {
            throw new LauncherInputException(LauncherMessages.Key.VALIDATION_FPS);
        }
        if (!PLAYERS.contains(playerImplementation)) {
            throw new LauncherInputException(
                    LauncherMessages.Key.VALIDATION_PLAYER, playerImplementation);
        }
    }

    static LauncherSettings defaults() {
        return new LauncherSettings(
                "Mukar", 5001, 1920, 1080, 60, "gstreamer", false, UiLanguage.systemDefault());
    }
}
