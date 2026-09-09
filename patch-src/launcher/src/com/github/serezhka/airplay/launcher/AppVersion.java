package com.github.serezhka.airplay.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AppVersion implements Comparable<AppVersion> {
    private static final int MAX_LENGTH = 128;
    private static final Pattern FORMAT = Pattern.compile(
            "[vV]?((?:0|[1-9][0-9]*)(?:\\.(?:0|[1-9][0-9]*)){0,3})"
                    + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");

    private final String text;
    private final List<BigInteger> components;
    private final List<String> prerelease;

    private AppVersion(String text, List<BigInteger> components, List<String> prerelease) {
        this.text = text;
        this.components = List.copyOf(components);
        this.prerelease = List.copyOf(prerelease);
    }

    static AppVersion current() throws IOException {
        try (InputStream input = AppVersion.class.getResourceAsStream("/airplay-version.txt")) {
            if (input == null) {
                throw new LauncherIOException(LauncherMessages.Key.ERROR_CURRENT_VERSION);
            }
            byte[] content = input.readNBytes(MAX_LENGTH + 1);
            if (content.length > MAX_LENGTH) {
                throw new LauncherIOException(LauncherMessages.Key.ERROR_CURRENT_VERSION);
            }
            return parse(new String(content, StandardCharsets.UTF_8).strip());
        } catch (IllegalArgumentException exception) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_CURRENT_VERSION);
        }
    }

    static AppVersion parse(String text) {
        if (text == null || text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Invalid version");
        }
        Matcher matcher = FORMAT.matcher(text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid version: " + text);
        }
        List<BigInteger> components = Arrays.stream(matcher.group(1).split("\\."))
                .map(BigInteger::new).toList();
        List<String> prerelease = matcher.group(2) == null
                ? List.of() : List.of(matcher.group(2).split("\\."));
        for (String identifier : prerelease) {
            if (numeric(identifier) && identifier.length() > 1 && identifier.startsWith("0")) {
                throw new IllegalArgumentException("Invalid prerelease version: " + text);
            }
        }
        return new AppVersion(text, components, prerelease);
    }

    @Override
    public int compareTo(AppVersion other) {
        for (int index = 0; index < Math.max(components.size(), other.components.size()); index++) {
            BigInteger first = index < components.size() ? components.get(index) : BigInteger.ZERO;
            BigInteger second = index < other.components.size() ? other.components.get(index) : BigInteger.ZERO;
            int comparison = first.compareTo(second);
            if (comparison != 0) {
                return comparison;
            }
        }
        if (prerelease.isEmpty() || other.prerelease.isEmpty()) {
            return Boolean.compare(prerelease.isEmpty(), other.prerelease.isEmpty());
        }
        for (int index = 0; index < Math.min(prerelease.size(), other.prerelease.size()); index++) {
            String first = prerelease.get(index);
            String second = other.prerelease.get(index);
            boolean firstNumeric = numeric(first);
            boolean secondNumeric = numeric(second);
            int comparison;
            if (firstNumeric && secondNumeric) {
                comparison = new BigInteger(first).compareTo(new BigInteger(second));
            } else if (firstNumeric != secondNumeric) {
                comparison = firstNumeric ? -1 : 1;
            } else {
                comparison = first.compareTo(second);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }

    @Override
    public String toString() {
        return text;
    }

    boolean isPrerelease() {
        return !prerelease.isEmpty();
    }

    private static boolean numeric(String value) {
        return value.chars().allMatch(character -> character >= '0' && character <= '9');
    }
}
