package com.github.serezhka.airplay.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class UpdateArchive {
    static final List<String> MANAGED_NAMES = List.of("jre", "gstreamer", "java-airplay-launcher.jar",
            "java-airplay-server-fixed.jar", "AirPlayReceiver.exe", "run_airplay_gui.bat", "run_airplay_server.bat");
    static final String PROTOCOL_RESOURCE = "airplay-update-protocol.txt";
    static final String PROTOCOL = "1";
    private static final long MAX_UNPACKED_BYTES = 3L * 1_073_741_824;
    private static final long MAX_FILE_BYTES = 1_073_741_824;
    private static final long FREE_SPACE_MARGIN = 64L * 1_048_576;
    private static final int MAX_ENTRIES = 40_000;

    private UpdateArchive() {
    }

    static void extract(Path archive, Path payload, AppVersion version,
                        UpdateDownloader.Cancellation cancellation) throws IOException {
        requireOrdinaryPath(payload.getParent());
        Files.createDirectory(payload);
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            List<PlannedEntry> entries = plan(zip, payload);
            long total = entries.stream().mapToLong(entry -> entry.entry().getSize()).sum();
            requireFreeSpace(payload, total);
            byte[] buffer = new byte[65_536];
            long extracted = 0;
            for (PlannedEntry planned : entries) {
                cancellation.check();
                ZipEntry entry = planned.entry();
                Path target = payload.resolve(planned.relative());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    requireOrdinaryPath(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                requireOrdinaryPath(target.getParent());
                CRC32 checksum = new CRC32();
                long written = 0;
                try (InputStream input = zip.getInputStream(entry);
                     OutputStream output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        cancellation.check();
                        written += count;
                        extracted += count;
                        if (written > entry.getSize() || extracted > MAX_UNPACKED_BYTES) {
                            throw invalidPackage();
                        }
                        checksum.update(buffer, 0, count);
                        output.write(buffer, 0, count);
                    }
                }
                if (written != entry.getSize() || checksum.getValue() != entry.getCrc()) {
                    throw invalidPackage();
                }
            }
        }
        cancellation.check();
        validatePayload(payload, version);
    }

    private static List<PlannedEntry> plan(ZipFile zip, Path payload) throws IOException {
        List<PlannedEntry> planned = new ArrayList<>();
        Set<String> explicitPaths = new HashSet<>();
        Map<String, PathKind> paths = new HashMap<>();
        String wrapper = null;
        long total = 0;
        int count = 0;
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            if (++count > MAX_ENTRIES) {
                throw invalidPackage();
            }
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (name.endsWith("/")) {
                name = name.substring(0, name.length() - 1);
            }
            String[] parts = name.split("/", -1);
            for (String part : parts) {
                validateComponent(part);
            }
            if (wrapper == null) {
                wrapper = parts[0];
            }
            if (!wrapper.equals(parts[0]) || (parts.length == 1 && !entry.isDirectory())) {
                throw invalidPackage();
            }
            String canonical = name.toLowerCase(Locale.ROOT);
            if (!explicitPaths.add(canonical)) {
                throw invalidPackage();
            }
            String prefix = "";
            for (int index = 0; index < parts.length; index++) {
                prefix = prefix.isEmpty() ? parts[index] : prefix + "/" + parts[index];
                boolean directory = index < parts.length - 1 || entry.isDirectory();
                PathKind previous = paths.putIfAbsent(prefix.toLowerCase(Locale.ROOT), new PathKind(prefix, directory));
                if (previous != null && (!previous.path().equals(prefix) || previous.directory() != directory)) {
                    throw invalidPackage();
                }
            }
            long size = entry.getSize();
            if (size < 0 || size > MAX_FILE_BYTES || (entry.isDirectory() && size != 0)) {
                throw invalidPackage();
            }
            total += size;
            if (total > MAX_UNPACKED_BYTES) {
                throw invalidPackage();
            }
            if (parts.length < 2 || !MANAGED_NAMES.contains(parts[1])) {
                continue;
            }
            String relative = String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length));
            Path target = payload.resolve(relative).normalize();
            if (!target.startsWith(payload) || target.toString().length() > 240) {
                throw invalidPackage();
            }
            planned.add(new PlannedEntry(entry, relative));
        }
        if (planned.isEmpty()) {
            throw invalidPackage();
        }
        return planned;
    }

    static void validatePayload(Path payload, AppVersion version) throws IOException {
        for (String name : MANAGED_NAMES) {
            Path target = payload.resolve(name);
            boolean directory = name.equals("jre") || name.equals("gstreamer");
            if (directory ? !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)
                    : !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.size(target) == 0) {
                throw invalidPackage();
            }
        }
        for (String name : List.of("jre/bin/java.exe", "jre/bin/javaw.exe")) {
            Path executable = payload.resolve(name);
            if (!Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS) || Files.size(executable) == 0) {
                throw invalidPackage();
            }
        }
        if (!Files.isDirectory(payload.resolve("gstreamer/bin"), LinkOption.NOFOLLOW_LINKS)) {
            throw invalidPackage();
        }
        try (JarFile launcher = new JarFile(payload.resolve("java-airplay-launcher.jar").toFile())) {
            String packagedVersion = readSmallEntry(launcher, "airplay-version.txt");
            if (!version.toString().replaceFirst("^[vV]", "").equals(packagedVersion.replaceFirst("^[vV]", ""))
                    || !PROTOCOL.equals(readSmallEntry(launcher, PROTOCOL_RESOURCE))
                    || launcher.getJarEntry("com/github/serezhka/airplay/launcher/AirPlayLauncher.class") == null
                    || launcher.getJarEntry("updater/AirPlayUpdater.exe") == null) {
                throw invalidPackage();
            }
        }
        try (JarFile server = new JarFile(payload.resolve("java-airplay-server-fixed.jar").toFile())) {
            if (server.getManifest() == null) {
                throw invalidPackage();
            }
        }
    }

    private static String readSmallEntry(JarFile jar, String name) throws IOException {
        ZipEntry entry = jar.getEntry(name);
        if (entry == null || entry.getSize() > 128) {
            throw invalidPackage();
        }
        try (InputStream input = jar.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(129);
            if (bytes.length > 128) {
                throw invalidPackage();
            }
            return new String(bytes, StandardCharsets.UTF_8).strip();
        }
    }

    private static void validateComponent(String component) throws IOException {
        String upper = component.toUpperCase(Locale.ROOT);
        String stem = upper.split("\\.", 2)[0];
        if (component.isEmpty() || component.equals(".") || component.equals("..")
                || component.length() > 200 || component.endsWith(".") || component.endsWith(" ")
                || component.chars().anyMatch(character -> character < 32 || "<>:\\|?*\"".indexOf(character) >= 0)
                || Set.of("CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$").contains(stem)
                || stem.matches("(?:COM|LPT)[0-9¹²³]")) {
            throw invalidPackage();
        }
    }

    static void requireOrdinaryPath(Path path) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path current = absolute.getRoot();
        for (Path part : absolute) {
            current = current.resolve(part);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            BasicFileAttributes attributes = Files.readAttributes(
                    current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || attributes.isOther()) {
                throw invalidPackage();
            }
        }
    }

    static void requireFreeSpace(Path directory, long required) throws IOException {
        if (Files.getFileStore(directory).getUsableSpace() < required + FREE_SPACE_MARGIN) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_SPACE);
        }
    }

    private static IOException invalidPackage() {
        return new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_PACKAGE);
    }

    private record PlannedEntry(ZipEntry entry, String relative) {
    }

    private record PathKind(String path, boolean directory) {
    }
}
