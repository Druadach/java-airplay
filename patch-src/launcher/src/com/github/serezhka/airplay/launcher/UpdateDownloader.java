package com.github.serezhka.airplay.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class UpdateDownloader {
    private static final Set<String> DOWNLOAD_HOSTS = Set.of(
            "github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com");
    private static final int MAX_REDIRECTS = 5;
    private static final long MAX_DOWNLOAD_NANOS = 30L * 60 * 1_000_000_000;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    UpdateDownloader() {
        this(10_000, 15_000);
    }

    UpdateDownloader(int connectTimeoutMillis, int readTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    void download(UpdateAsset asset, Path destination, Cancellation cancellation,
                  Consumer<Progress> progress) throws IOException {
        download(asset, asset.downloadUri(), destination, cancellation, progress, false);
    }

    void downloadFromLoopback(UpdateAsset asset, URI endpoint, Path destination,
                              Cancellation cancellation, Consumer<Progress> progress) throws IOException {
        if (!"http".equals(endpoint.getScheme()) || !"127.0.0.1".equals(endpoint.getHost())
                || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
            throw new IllegalArgumentException("Tests require an explicit IPv4 loopback endpoint");
        }
        download(asset, endpoint, destination, cancellation, progress, true);
    }

    private void download(UpdateAsset asset, URI endpoint, Path destination, Cancellation cancellation,
                          Consumer<Progress> progress, boolean loopback) throws IOException {
        if (asset.size() <= 0 || asset.size() > UpdateAsset.MAX_DOWNLOAD_BYTES
                || !asset.sha256().matches("[0-9a-fA-F]{64}")) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_PACKAGE);
        }
        URI current = endpoint;
        long deadline = System.nanoTime() + MAX_DOWNLOAD_NANOS;
        try {
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                cancellation.check();
                if (!(loopback && current.equals(endpoint)) && !isTrustedDownloadUri(current)) {
                    throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_PACKAGE);
                }
                HttpURLConnection connection = (HttpURLConnection) current.toURL().openConnection();
                cancellation.connection = connection;
                try {
                    connection.setConnectTimeout(connectTimeoutMillis);
                    connection.setReadTimeout(readTimeoutMillis);
                    connection.setInstanceFollowRedirects(false);
                    connection.setUseCaches(false);
                    connection.setRequestProperty("Accept", "application/octet-stream");
                    connection.setRequestProperty("User-Agent", "AirPlayReceiver-Updater");
                    cancellation.check();
                    int status = connection.getResponseCode();
                    if (Set.of(301, 302, 303, 307, 308).contains(status)) {
                        String location = connection.getHeaderField("Location");
                        if (redirect == MAX_REDIRECTS || location == null) {
                            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_PACKAGE);
                        }
                        current = current.resolve(location);
                        if (!isTrustedDownloadUri(current)) {
                            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_PACKAGE);
                        }
                        continue;
                    }
                    if (status != HttpURLConnection.HTTP_OK) {
                        throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_HTTP, status);
                    }
                    long declared = connection.getContentLengthLong();
                    if (declared >= 0 && declared != asset.size()) {
                        throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_PACKAGE);
                    }
                    try (InputStream input = connection.getInputStream();
                         OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
                        copyVerified(input, output, asset, cancellation, progress, deadline);
                    }
                    return;
                } finally {
                    cancellation.connection = null;
                    connection.disconnect();
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            cancellation.check();
            if (exception instanceof LauncherIOException localized) {
                throw localized;
            }
            LauncherIOException failure = new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_DOWNLOAD);
            failure.initCause(exception);
            throw failure;
        }
    }

    static boolean isTrustedDownloadUri(URI uri) {
        return "https".equals(uri.getScheme()) && uri.getHost() != null && DOWNLOAD_HOSTS.contains(uri.getHost())
                && (uri.getPort() == -1 || uri.getPort() == 443)
                && uri.getRawUserInfo() == null && uri.getRawFragment() == null;
    }

    private static void copyVerified(InputStream input, OutputStream output, UpdateAsset asset,
                                     Cancellation cancellation, Consumer<Progress> progress,
                                     long deadline) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        byte[] buffer = new byte[65_536];
        long completed = 0;
        long lastProgress = 0;
        progress.accept(new Progress(false, 0, asset.size()));
        int count;
        while ((count = input.read(buffer)) != -1) {
            cancellation.check();
            if (System.nanoTime() > deadline) {
                throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_DOWNLOAD);
            }
            completed += count;
            if (completed > asset.size()) {
                throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_PACKAGE);
            }
            output.write(buffer, 0, count);
            digest.update(buffer, 0, count);
            long now = System.nanoTime();
            if (now - lastProgress > 150_000_000) {
                progress.accept(new Progress(false, completed, asset.size()));
                lastProgress = now;
            }
        }
        cancellation.check();
        if (completed != asset.size()) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_PACKAGE);
        }
        if (!MessageDigest.isEqual(digest.digest(), HexFormat.of().parseHex(asset.sha256()))) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_CHECKSUM);
        }
        progress.accept(new Progress(false, completed, asset.size()));
    }

    record Progress(boolean preparing, long completed, long total) {
    }

    static final class Cancellation {
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile HttpURLConnection connection;

        void cancel() {
            cancelled.set(true);
            HttpURLConnection active = connection;
            if (active != null) {
                java.util.concurrent.CompletableFuture.runAsync(active::disconnect);
            }
        }

        boolean isCancelled() {
            return cancelled.get();
        }

        void check() {
            if (cancelled.get() || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Update cancelled");
            }
        }
    }
}
