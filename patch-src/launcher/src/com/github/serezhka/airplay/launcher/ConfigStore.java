package com.github.serezhka.airplay.launcher;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

final class ConfigStore {
    private final Path path;

    ConfigStore(Path path) {
        this.path = path.toAbsolutePath().normalize();
    }

    Path path() {
        return path;
    }

    LauncherSettings load() throws IOException {
        LauncherSettings defaults = LauncherSettings.defaults();
        Properties properties = loadProperties();
        if (properties.isEmpty()) {
            return defaults;
        }
        return new LauncherSettings(
                properties.getProperty("airplay.serverName", defaults.serverName()),
                integer(properties, "airplay.airtunesPort", defaults.airtunesPort()),
                integer(properties, "airplay.width", defaults.width()),
                integer(properties, "airplay.height", defaults.height()),
                integer(properties, "airplay.fps", defaults.fps()),
                properties.getProperty("player.implementation", defaults.playerImplementation()),
                bool(properties, "player.gstreamer.fullscreen", defaults.startFullscreen()),
                UiLanguage.fromCode(properties.getProperty("launcher.language")));
    }

    UiLanguage loadLanguage() throws IOException {
        return UiLanguage.fromCode(loadProperties().getProperty("launcher.language"));
    }

    void save(LauncherSettings settings) throws IOException {
        update(settingsValues(settings, true));
    }

    void saveGuiSettings(LauncherSettings settings) throws IOException {
        update(settingsValues(settings, false));
    }

    private static Map<String, String> settingsValues(
            LauncherSettings settings,
            boolean includePort) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("airplay.serverName", settings.serverName());
        if (includePort) {
            values.put("airplay.airtunesPort", Integer.toString(settings.airtunesPort()));
        }
        values.put("airplay.width", Integer.toString(settings.width()));
        values.put("airplay.height", Integer.toString(settings.height()));
        values.put("airplay.fps", Integer.toString(settings.fps()));
        values.put("player.implementation", settings.playerImplementation());
        values.put("player.gstreamer.fullscreen", Boolean.toString(settings.startFullscreen()));
        values.put("launcher.language", settings.language().code());
        return values;
    }

    void saveLanguage(UiLanguage language) throws IOException {
        update(Map.of("launcher.language", language.code()));
    }

    private void update(Map<String, String> values) throws IOException {
        List<String> source = Files.exists(path)
                ? Files.readAllLines(path, StandardCharsets.UTF_8)
                : List.of("# Java AirPlay Server configuration");
        List<String> output = new ArrayList<>(source.size() + values.size() + 1);
        Map<String, Boolean> written = new LinkedHashMap<>();
        for (String key : values.keySet()) {
            written.put(key, false);
        }

        for (String line : source) {
            String key = propertyKey(line);
            if (values.containsKey(key)) {
                if (!written.get(key)) {
                    output.add(key + "=" + escapeValue(values.get(key)));
                    written.put(key, true);
                }
            } else {
                output.add(line);
            }
        }
        if (!output.isEmpty() && !output.get(output.size() - 1).isBlank()) {
            output.add("");
        }
        for (String key : values.keySet()) {
            if (!written.get(key)) {
                output.add(key + "=" + escapeValue(values.get(key)));
            }
        }

        Path parent = path.getParent();
        if (parent == null) {
            throw new LauncherIOException(
                    LauncherMessages.Key.ERROR_CONFIGURATION_NO_PARENT, path);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
        try {
            Files.write(temporary, output, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static int integer(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new LauncherInputException(LauncherMessages.Key.CONFIG_INTEGER_REQUIRED, key);
        }
    }

    private Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        if (!Files.exists(path)) {
            return properties;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static boolean bool(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value.trim());
    }

    private static String propertyKey(String line) {
        String trimmed = line.stripLeading();
        if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
            return "";
        }
        int separator = -1;
        boolean escaped = false;
        for (int index = 0; index < trimmed.length(); index++) {
            char character = trimmed.charAt(index);
            if (!escaped && (character == '=' || character == ':' || Character.isWhitespace(character))) {
                separator = index;
                break;
            }
            escaped = !escaped && character == '\\';
            if (character != '\\') {
                escaped = false;
            }
        }
        return (separator < 0 ? trimmed : trimmed.substring(0, separator)).trim();
    }

    private static String escapeValue(String value) {
        return value.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
