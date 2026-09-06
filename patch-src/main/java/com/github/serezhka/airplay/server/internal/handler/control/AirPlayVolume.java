package com.github.serezhka.airplay.server.internal.handler.control;

import java.util.Locale;
import java.util.OptionalDouble;

import com.github.serezhka.airplay.server.VolumeController;

public final class AirPlayVolume {
    public static final double MIN_DB = -144.0;
    public static final double MAX_DB = 0.0;
    public static final double DEFAULT_DB = 0.0;

    private AirPlayVolume() {
    }

    static OptionalDouble parse(String content) {
        if (content == null || content.isEmpty()) {
            return OptionalDouble.empty();
        }

        for (String line : content.split("\\r?\\n")) {
            int separator = line.indexOf(':');
            if (separator < 0 || !"volume".equalsIgnoreCase(line.substring(0, separator).trim())) {
                continue;
            }
            try {
                double value = Double.parseDouble(line.substring(separator + 1).trim());
                if (Double.isFinite(value)) {
                    return OptionalDouble.of(clampDb(value));
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed parameters and acknowledge the RTSP request.
            }
        }
        return OptionalDouble.empty();
    }

    public static double clampDb(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("AirPlay volume must be finite");
        }
        return Math.max(MIN_DB, Math.min(MAX_DB, value));
    }

    static double toLinear(double volumeDb) {
        double clamped = clampDb(volumeDb);
        return VolumeController.dbToLinear(clamped);
    }

    static String formatParameter(double volumeDb) {
        return String.format(Locale.ROOT, "volume: %.6f\r\n", clampDb(volumeDb));
    }
}
