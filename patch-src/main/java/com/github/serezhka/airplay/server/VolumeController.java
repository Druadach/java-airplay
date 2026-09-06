package com.github.serezhka.airplay.server;

/** Receives AirPlay volume changes in decibels for the active audio output. */
public interface VolumeController {
    double MIN_VOLUME_DB = -144.0;
    double MAX_VOLUME_DB = 0.0;

    void setVolumeDb(double volumeDb);

    static double dbToLinear(double volumeDb) {
        if (!Double.isFinite(volumeDb)) {
            throw new IllegalArgumentException("AirPlay volume must be finite");
        }
        if (volumeDb <= MIN_VOLUME_DB) {
            return 0.0;
        }
        return Math.pow(10.0, Math.min(MAX_VOLUME_DB, volumeDb) / 20.0);
    }
}
