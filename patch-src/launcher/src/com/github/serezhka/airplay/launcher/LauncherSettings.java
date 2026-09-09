package com.github.serezhka.airplay.launcher;

record LauncherSettings(
        String serverName,
        int airtunesPort,
        int width,
        int height,
        int fps,
        String playerImplementation,
        boolean startFullscreen,
        boolean autoStartEnabled,
        boolean autoRunService,
        boolean startMinimized,
        boolean closeToTray,
        UiLanguage language) {

    LauncherSettings {
        serverName = serverName == null ? "" : serverName.trim();
        playerImplementation = playerImplementation == null ? "" : playerImplementation.trim();
        language = language == null ? UiLanguage.SYSTEM : language;
        if (serverName.isEmpty() || serverName.length() > 64) {
            throw new LauncherInputException(LauncherMessages.Key.VALIDATION_SERVER_NAME);
        }
        if (airtunesPort < 1 || airtunesPort > 65535) {
            throw new LauncherInputException(LauncherMessages.Key.VALIDATION_SERVER_PORT);
        }
        if (width < 320 || width > 7680) {
            throw new LauncherInputException(LauncherMessages.Key.VALIDATION_WIDTH);
        }
        if (height < 240 || height > 4320) {
            throw new LauncherInputException(LauncherMessages.Key.VALIDATION_HEIGHT);
        }
        if (fps < 1 || fps > 240) {
            throw new LauncherInputException(LauncherMessages.Key.VALIDATION_FPS);
        }
        PlayerOption.fromImplementation(playerImplementation);
    }

    static LauncherSettings defaults() {
        return defaults(5001);
    }

    static LauncherSettings defaults(int airtunesPort) {
        return new LauncherSettings(
                "AirPlay - PC", airtunesPort, 1920, 1080, 60, "gstreamer", false, false, false,
                false, true, UiLanguage.SYSTEM);
    }

    LauncherSettings withLanguage(UiLanguage preference) {
        return new LauncherSettings(serverName, airtunesPort, width, height, fps, playerImplementation,
                startFullscreen, autoStartEnabled, autoRunService, startMinimized, closeToTray, preference);
    }

    boolean sameServiceConfiguration(LauncherSettings other) {
        return other != null && serverName.equals(other.serverName) && airtunesPort == other.airtunesPort
                && width == other.width && height == other.height && fps == other.fps
                && playerImplementation.equals(other.playerImplementation)
                && startFullscreen == other.startFullscreen;
    }

    boolean unverifiedVideoMode() {
        return width > 3840 || height > 2160 || fps > 60;
    }
}
