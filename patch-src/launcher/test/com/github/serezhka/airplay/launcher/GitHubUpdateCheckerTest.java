package com.github.serezhka.airplay.launcher;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

final class GitHubUpdateCheckerTest {
    private GitHubUpdateCheckerTest() {
    }

    static void runAll() throws Exception {
        versionsCompareNumerically();
        invalidVersionsAreRejected();
        releaseFieldsAreParsedAtTheTopLevel();
        malformedReleaseDataIsRejected();
        onlyNewerStableReleasesOfferUpdates();
        httpRequestsUseTheGitHubApiContract();
        httpErrorsAndLimitsAreReported();
        timeoutsAndConnectionFailuresAreReported();
        oversizedResponsesAreRejected();
        versionTextAndControlsStayConsistent();
        System.out.println("GitHub update checker tests passed");
    }

    private static void versionsCompareNumerically() {
        assertOrder("v1.2.2", "1.2.2", 0);
        assertOrder("V1.2", "1.2.0.0", 0);
        assertOrder("1.10.0", "1.9.9", 1);
        assertOrder("2.0.0", "1.999.999", 1);
        assertOrder("1.2.2.1", "1.2.2", 1);
        assertOrder("1.2.2+build.10", "v1.2.2+build.9", 0);
        assertOrder("1.2.3-rc.10", "1.2.3-rc.2", 1);
        assertOrder("999999999999999999999.0.0", "2.0.0", 1);
        List<String> versions = List.of("1.0.0-alpha", "1.0.0-alpha.1", "1.0.0-alpha.beta",
                "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.11", "1.0.0-rc.1", "1.0.0");
        for (int index = 1; index < versions.size(); index++) {
            assertOrder(versions.get(index - 1), versions.get(index), -1);
        }
    }

    private static void invalidVersionsAreRejected() {
        for (String version : List.of("", "vNext", "release-1.2.3", "1..2", "01.2.3", "1.2.3-01",
                "1.2.3.4.5", "1.2.3/../other", "1.2.3?download=true", " 1.2.3", "1".repeat(129))) {
            try {
                AppVersion.parse(version);
                throw new AssertionError("Accepted invalid version: " + version);
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    private static void releaseFieldsAreParsedAtTheTopLevel() throws Exception {
        String json = """
                {
                  "assets": [{"tag_name": "v999.0.0", "draft": true, "size": 1e3}],
                  "body": "Line one\\nLine two: \\"tag_name\\": \\"v999.0.0\\"",
                  "html_url": "https://untrusted.example/download",
                  "unknown": {"array": [true, false, null, -12.5e-2, {}, []]},
                  "tag\\u005fname": "v\\u0031.10.0",
                  "prerelease": false,
                  "draft": false
                }
                """;
        GitHubRelease release = GitHubRelease.parse(json);
        assertEquals("v1.10.0", release.tagName(), "escaped top-level tag");
        assertEquals(false, release.draft(), "nested draft is ignored");
        GitHubUpdateChecker.Result result = GitHubUpdateChecker.parseResponse(json, AppVersion.parse("1.2.2"));
        assertEquals(true, result.updateAvailable(), "numeric update comparison");
        assertEquals(URI.create("https://github.com/Druadach/java-airplay/releases/tag/v1.10.0"),
                result.releasePage(), "release URL never comes from untrusted metadata");
    }

    private static void malformedReleaseDataIsRejected() throws Exception {
        String valid = releaseJson("v1.2.3");
        for (String json : List.of("not JSON", "[]", "{}", "{\"tag_name\":\"v1.2.3\"}",
                valid + " trailing", valid.replace("false", "null"), valid.replace("false", "\"false\""),
                valid.replace("\"v1.2.3\"", "123"), valid.replace("\"v1.2.3\"", "\"v1.2.3\\x\""),
                valid.replace("{", "{\"tag_name\":\"v9.0.0\","), valid.replace("}", ",}"),
                valid.replace("{", "{\"number\":01,"), valid.substring(0, valid.length() - 1),
                "{\"extra\":" + "[".repeat(40) + "0" + "]".repeat(40) + "," + valid.substring(1))) {
            expectFailure(LauncherMessages.Key.ERROR_UPDATE_RESPONSE, () -> GitHubRelease.parse(json));
        }
    }

    private static void onlyNewerStableReleasesOfferUpdates() throws Exception {
        AppVersion current = AppVersion.parse("1.2.2");
        assertEquals(true, GitHubUpdateChecker.parseResponse(releaseJson("v1.2.3"), current).updateAvailable(),
                "newer release offers an update");
        assertEquals(false, GitHubUpdateChecker.parseResponse(releaseJson("v1.2.2"), current).updateAvailable(),
                "equal versions do not offer updates");
        assertEquals(false, GitHubUpdateChecker.parseResponse(releaseJson("v1.2.1"), current).updateAvailable(),
                "local development version does not offer a downgrade");
        assertEquals(false, GitHubUpdateChecker.parseResponse(releaseJson("v1.2.2+build.9"), current).updateAvailable(),
                "build metadata does not change precedence");
        assertEquals(true, GitHubUpdateChecker.parseResponse(releaseJson("v1.2.2"),
                AppVersion.parse("1.2.2-rc.1")).updateAvailable(), "stable replaces local prerelease");
        for (String json : List.of(releaseJson("v1.3.0").replace("\"draft\":false", "\"draft\":true"),
                releaseJson("v1.3.0").replace("\"prerelease\":false", "\"prerelease\":true"),
                releaseJson("v1.3.0-beta.1"))) {
            expectFailure(LauncherMessages.Key.ERROR_UPDATE_NO_RELEASE,
                    () -> GitHubUpdateChecker.parseResponse(json, current));
        }
        expectFailure(LauncherMessages.Key.ERROR_UPDATE_VERSION,
                () -> GitHubUpdateChecker.parseResponse(releaseJson("nightly"), current));
        expectFailure(LauncherMessages.Key.ERROR_UPDATE_VERSION,
                () -> GitHubUpdateChecker.parseResponse(releaseJson("https://untrusted.example"), current));
    }

    private static void httpRequestsUseTheGitHubApiContract() throws Exception {
        assertEquals(URI.create("https://api.github.com/repos/Druadach/java-airplay/releases/latest"),
                GitHubUpdateChecker.LATEST_RELEASE_API, "official repository endpoint");
        try (ReleaseServer server = new ReleaseServer(200, releaseJson("v1.10.0"))) {
            GitHubUpdateChecker.Result result = server.checker().check(AppVersion.parse("1.2.2"));
            assertEquals(true, result.updateAvailable(), "HTTP release comparison");
            assertEquals("GET", server.method.get(), "read-only API request");
            assertEquals("application/vnd.github+json", server.accept.get(), "GitHub JSON media type");
            assertEquals("2022-11-28", server.apiVersion.get(), "GitHub API version");
            assertEquals("AirPlayReceiver/1.2.2", server.userAgent.get(), "application User-Agent");
            assertEquals(1, server.requests.get(), "one request per check");
        }
    }

    private static void httpErrorsAndLimitsAreReported() throws Exception {
        expectHttpFailure(404, Map.of(), LauncherMessages.Key.ERROR_UPDATE_NO_RELEASE);
        expectHttpFailure(429, Map.of(), LauncherMessages.Key.ERROR_UPDATE_RATE_LIMIT);
        expectHttpFailure(403, Map.of("X-RateLimit-Remaining", "0"), LauncherMessages.Key.ERROR_UPDATE_RATE_LIMIT);
        expectHttpFailure(403, Map.of("Retry-After", "60"), LauncherMessages.Key.ERROR_UPDATE_RATE_LIMIT);
        expectHttpFailure(403, Map.of(), LauncherMessages.Key.ERROR_UPDATE_HTTP);
        expectHttpFailure(500, Map.of(), LauncherMessages.Key.ERROR_UPDATE_HTTP);
        expectHttpFailure(302, Map.of("Location", "http://127.0.0.1:1/not-followed"),
                LauncherMessages.Key.ERROR_UPDATE_HTTP);
        try (ReleaseServer server = new ReleaseServer(200, "<html>not release metadata</html>")) {
            expectFailure(LauncherMessages.Key.ERROR_UPDATE_RESPONSE,
                    () -> server.checker().check(AppVersion.parse("1.2.2")));
        }
    }

    private static void timeoutsAndConnectionFailuresAreReported() throws Exception {
        try (ReleaseServer server = new ReleaseServer(200, releaseJson("v1.2.3"), Map.of(), 400, false)) {
            GitHubUpdateChecker checker = new GitHubUpdateChecker(server.endpoint(), 1_000, 100);
            expectFailure(LauncherMessages.Key.ERROR_UPDATE_TIMEOUT, () -> checker.check(AppVersion.parse("1.2.2")));
        }
        try (ReleaseServer server = new ReleaseServer(exchange -> exchange.close())) {
            expectFailure(LauncherMessages.Key.ERROR_UPDATE_CONNECTION,
                    () -> server.checker().check(AppVersion.parse("1.2.2")));
        }
    }

    private static void oversizedResponsesAreRejected() throws Exception {
        String oversized = "x".repeat(GitHubUpdateChecker.MAX_RESPONSE_BYTES + 1);
        for (boolean chunked : List.of(false, true)) {
            try (ReleaseServer server = new ReleaseServer(200, oversized, Map.of(), 0, chunked)) {
                expectFailure(LauncherMessages.Key.ERROR_UPDATE_RESPONSE,
                        () -> server.checker().check(AppVersion.parse("1.2.2")));
            }
        }
    }

    private static void versionTextAndControlsStayConsistent() throws Exception {
        AppVersion current = AppVersion.current();
        for (UiLanguage language : List.of(UiLanguage.ZH_CN, UiLanguage.EN_US)) {
            String about = LauncherMessages.text(language, LauncherMessages.Key.ABOUT_MESSAGE, current);
            if (!about.contains("v" + current) || about.contains("{0}")) {
                throw new AssertionError("About dialog does not use the packaged version");
            }
        }
        SwingUtilities.invokeAndWait(() -> {
            for (UiLanguage language : List.of(UiLanguage.ZH_CN, UiLanguage.EN_US)) {
                for (LauncherMessages.Key buttonKey : List.of(LauncherMessages.Key.CHECK_UPDATES,
                        LauncherMessages.Key.CHECKING_UPDATES)) {
                    JLabel version = new JLabel(LauncherMessages.text(language, LauncherMessages.Key.CURRENT_VERSION, current));
                    JButton check = new JButton(LauncherMessages.text(language, buttonKey));
                    JPanel panel = LauncherFrame.createVersionPanel(version, check);
                    for (int width : List.of(320, 420)) {
                        panel.setSize(width, panel.getPreferredSize().height);
                        panel.doLayout();
                        assertEquals(true, version.getWidth() >= version.getPreferredSize().width,
                                "version text remains readable: " + language.code());
                        assertEquals(true, check.getWidth() >= check.getPreferredSize().width,
                                "update button remains readable: " + language.code());
                        assertEquals(true, version.getX() + version.getWidth() <= check.getX(),
                                "version and update button do not overlap");
                    }
                }
            }
        });
    }

    private static String releaseJson(String tag) {
        return "{\"tag_name\":\"" + tag + "\",\"draft\":false,\"prerelease\":false}";
    }

    private static void assertOrder(String first, String second, int expected) {
        assertEquals(expected, Integer.signum(AppVersion.parse(first).compareTo(AppVersion.parse(second))),
                first + " compared with " + second);
        assertEquals(-expected, Integer.signum(AppVersion.parse(second).compareTo(AppVersion.parse(first))),
                "reverse version comparison");
    }

    private static void expectHttpFailure(int status, Map<String, String> headers, LauncherMessages.Key key)
            throws Exception {
        try (ReleaseServer server = new ReleaseServer(status, "{}", headers, 0, false)) {
            expectFailure(key, () -> server.checker().check(AppVersion.parse("1.2.2")));
            assertEquals(1, server.requests.get(), "HTTP errors are not retried automatically");
        }
    }

    private static void expectFailure(LauncherMessages.Key key, CheckedAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected " + key);
        } catch (LauncherIOException expected) {
            assertEquals(key, expected.messageKey(), "localized update-check failure");
            for (UiLanguage language : List.of(UiLanguage.ZH_CN, UiLanguage.EN_US)) {
                String message = LauncherMessages.failureText(language, expected);
                if (message.isBlank() || message.contains("{0}")) {
                    throw new AssertionError("Incomplete update error translation: " + key);
                }
            }
        }
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(description + ": expected " + expected + " but was " + actual);
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    private static final class ReleaseServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicReference<String> method = new AtomicReference<>();
        private final AtomicReference<String> accept = new AtomicReference<>();
        private final AtomicReference<String> apiVersion = new AtomicReference<>();
        private final AtomicReference<String> userAgent = new AtomicReference<>();
        private final AtomicInteger requests = new AtomicInteger();

        private ReleaseServer(int status, String body) throws IOException {
            this(status, body, Map.of(), 0, false);
        }

        private ReleaseServer(int status, String body, Map<String, String> headers, long delayMillis, boolean chunked)
                throws IOException {
            this(exchange -> {
                try {
                    if (delayMillis > 0) {
                        try {
                            Thread.sleep(delayMillis);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    headers.forEach((name, value) -> exchange.getResponseHeaders().set(name, value));
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(status, chunked ? 0 : bytes.length);
                    try (OutputStream output = exchange.getResponseBody()) {
                        output.write(bytes);
                    }
                } finally {
                    exchange.close();
                }
            });
        }

        private ReleaseServer(HttpHandler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/latest", exchange -> {
                requests.incrementAndGet();
                method.set(exchange.getRequestMethod());
                accept.set(exchange.getRequestHeaders().getFirst("Accept"));
                apiVersion.set(exchange.getRequestHeaders().getFirst("X-GitHub-Api-Version"));
                userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
                handler.handle(exchange);
            });
            server.start();
        }

        private URI endpoint() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/latest");
        }

        private GitHubUpdateChecker checker() {
            return new GitHubUpdateChecker(endpoint(), 1_000, 2_000);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
