package com.github.serezhka.airplay.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

final class GitHubUpdateChecker {
    static final URI RELEASES_PAGE = URI.create("https://github.com/Druadach/java-airplay/releases");
    static final URI LATEST_RELEASE_API = URI.create(
            "https://api.github.com/repos/Druadach/java-airplay/releases/latest");
    static final int MAX_RESPONSE_BYTES = 1_048_576;
    private final URI endpoint;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    GitHubUpdateChecker() {
        this(LATEST_RELEASE_API, 5_000, 10_000);
    }

    GitHubUpdateChecker(URI endpoint, int connectTimeoutMillis, int readTimeoutMillis) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0) {
            throw new IllegalArgumentException("Update-check timeouts must be positive");
        }
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    Result check(AppVersion currentVersion) throws IOException {
        Objects.requireNonNull(currentVersion, "currentVersion");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) endpoint.toURL().openConnection();
            connection.setConnectTimeout(connectTimeoutMillis);
            connection.setReadTimeout(readTimeoutMillis);
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "AirPlayReceiver/" + currentVersion);
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            int status = connection.getResponseCode();
            if (status == 429 || (status == 403
                    && ("0".equals(connection.getHeaderField("X-RateLimit-Remaining"))
                    || connection.getHeaderField("Retry-After") != null))) {
                throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_RATE_LIMIT);
            }
            if (status == 404) {
                throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_NO_RELEASE);
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_HTTP, Integer.toString(status));
            }
            if (connection.getContentLengthLong() > MAX_RESPONSE_BYTES) {
                throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_RESPONSE);
            }
            try (InputStream input = connection.getInputStream()) {
                byte[] response = input.readNBytes(MAX_RESPONSE_BYTES + 1);
                if (response.length > MAX_RESPONSE_BYTES) {
                    throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_RESPONSE);
                }
                return parseResponse(new String(response, StandardCharsets.UTF_8), currentVersion);
            }
        } catch (SocketTimeoutException exception) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_TIMEOUT);
        } catch (LauncherIOException exception) {
            throw exception;
        } catch (IOException exception) {
            LauncherIOException failure = new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_CONNECTION);
            failure.initCause(exception);
            throw failure;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    static Result parseResponse(String json, AppVersion currentVersion) throws IOException {
        GitHubRelease release = GitHubRelease.parse(json);
        if (release.draft() || release.prerelease()) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_NO_RELEASE);
        }
        AppVersion latestVersion;
        try {
            latestVersion = AppVersion.parse(release.tagName());
        } catch (IllegalArgumentException exception) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_VERSION);
        }
        if (latestVersion.isPrerelease()) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_NO_RELEASE);
        }
        return new Result(currentVersion, latestVersion,
                URI.create(RELEASES_PAGE + "/tag/" + latestVersion),
                UpdateAsset.select(release.tagName(), release.assets()));
    }

    record Result(AppVersion currentVersion, AppVersion latestVersion, URI releasePage, UpdateAsset asset) {
        boolean updateAvailable() {
            return latestVersion.compareTo(currentVersion) > 0;
        }

        boolean automaticUpdateAvailable() {
            return updateAvailable() && asset != null;
        }
    }
}
