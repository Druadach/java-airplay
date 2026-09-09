package com.github.serezhka.airplay.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

enum PlayerOption {
    GSTREAMER("gstreamer", null, LauncherMessages.Key.PLAYER_GSTREAMER, LauncherMessages.Key.PLAYER_GSTREAMER_HINT),
    FFMPEG("ffmpeg", "ffplay.exe", LauncherMessages.Key.PLAYER_FFMPEG, LauncherMessages.Key.PLAYER_FFMPEG_HINT),
    VLC("vlc", "vlc.exe", LauncherMessages.Key.PLAYER_VLC, LauncherMessages.Key.PLAYER_VLC_HINT),
    H264_DUMP("h264-dump", null, LauncherMessages.Key.PLAYER_H264_DUMP, LauncherMessages.Key.PLAYER_H264_DUMP_HINT);

    private final String implementation;
    private final String executable;
    private final LauncherMessages.Key labelKey;
    private final LauncherMessages.Key hintKey;

    PlayerOption(String implementation, String executable,
                 LauncherMessages.Key labelKey, LauncherMessages.Key hintKey) {
        this.implementation = implementation;
        this.executable = executable;
        this.labelKey = labelKey;
        this.hintKey = hintKey;
    }

    String implementation() {
        return implementation;
    }

    String executable() {
        return executable;
    }

    String label(UiLanguage language) {
        return LauncherMessages.text(language, labelKey);
    }

    String hint(UiLanguage language) {
        return LauncherMessages.text(language, hintKey);
    }

    Path findExecutable(Path baseDirectory, String searchPath) {
        if (executable == null) {
            return null;
        }
        List<Path> directories = new ArrayList<>(List.of(baseDirectory,
                baseDirectory.resolve("jre/bin"), baseDirectory.resolve("gstreamer/bin")));
        if (searchPath != null) {
            for (String entry : searchPath.split(Pattern.quote(File.pathSeparator))) {
                String directory = entry.trim();
                if (directory.startsWith("\"") && directory.endsWith("\"") && directory.length() > 1) {
                    directory = directory.substring(1, directory.length() - 1);
                }
                if (!directory.isBlank()) {
                    try {
                        directories.add(baseDirectory.resolve(directory));
                    } catch (InvalidPathException ignored) {
                    }
                }
            }
        }
        for (Path directory : directories) {
            Path candidate = directory.resolve(executable).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    void requireAvailable(Path baseDirectory, String searchPath) throws IOException {
        if (executable != null && findExecutable(baseDirectory, searchPath) == null) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_EXTERNAL_PLAYER_MISSING, executable);
        }
    }

    static PlayerOption fromImplementation(String implementation) {
        for (PlayerOption option : values()) {
            if (option.implementation.equals(implementation)) {
                return option;
            }
        }
        throw new LauncherInputException(LauncherMessages.Key.VALIDATION_PLAYER, implementation);
    }
}
