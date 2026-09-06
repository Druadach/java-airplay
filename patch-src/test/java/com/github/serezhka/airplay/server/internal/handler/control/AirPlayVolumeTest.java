package com.github.serezhka.airplay.server.internal.handler.control;

import java.util.OptionalDouble;

public final class AirPlayVolumeTest {
    public static void main(String[] args) {
        parsesVolumeParameter();
        ignoresMalformedParameters();
        convertsAndFormatsVolume();
        System.out.println("AirPlay volume tests passed");
    }

    private static void parsesVolumeParameter() {
        OptionalDouble value = AirPlayVolume.parse(
                "progress: 0/0\r\nVolume: -15.0\r\n");
        assertPresent(value, -15.0, "volume parameter");
        assertPresent(AirPlayVolume.parse("volume: -200\r\n"), -144.0, "lower clamp");
        assertPresent(AirPlayVolume.parse("volume: 2\r\n"), 0.0, "upper clamp");
    }

    private static void ignoresMalformedParameters() {
        assertEmpty(AirPlayVolume.parse("volume: NaN\r\n"), "NaN");
        assertEmpty(AirPlayVolume.parse("volume: not-a-number\r\n"), "invalid number");
        assertEmpty(AirPlayVolume.parse("progress: 1/2\r\n"), "unrelated parameter");
    }

    private static void convertsAndFormatsVolume() {
        assertClose(0.0, AirPlayVolume.toLinear(-144.0), "mute conversion");
        assertClose(Math.pow(10.0, -15.0 / 20.0), AirPlayVolume.toLinear(-15.0), "dB conversion");
        if (!"volume: -15.000000\r\n".equals(AirPlayVolume.formatParameter(-15.0))) {
            throw new AssertionError("unexpected GET_PARAMETER value");
        }
    }

    private static void assertPresent(OptionalDouble actual, double expected, String description) {
        if (actual.isEmpty() || Math.abs(actual.getAsDouble() - expected) > 0.000001) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertEmpty(OptionalDouble actual, String description) {
        if (actual.isPresent()) {
            throw new AssertionError(description + " should be ignored");
        }
    }

    private static void assertClose(double expected, double actual, String description) {
        if (Math.abs(expected - actual) > 0.000001) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }
}
