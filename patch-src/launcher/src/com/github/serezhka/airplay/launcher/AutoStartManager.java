package com.github.serezhka.airplay.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Manages Windows startup registry entries for the AirPlay launcher.
 */
final class AutoStartManager {
    private static final String REGISTRY_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String APP_NAME = "AirPlayReceiver";

    private AutoStartManager() {
    }

    /**
     * Checks if auto-start is currently enabled.
     */
    static boolean isEnabled() {
        try {
            return runRegistry("query", REGISTRY_KEY, "/v", APP_NAME) == 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException exception) {
            return false;
        }
    }

    /**
     * Enables auto-start by adding a registry entry pointing to the launcher executable.
     * @param executablePath Path to AirPlayReceiver.exe or java-airplay-launcher.jar
     */
    static void enable(Path executablePath) throws IOException, InterruptedException {
        String command = startupCommand(executablePath);
        int exitCode = runRegistry("add", REGISTRY_KEY,
                "/v", APP_NAME, "/t", "REG_SZ", "/d", command, "/f");
        if (exitCode != 0) {
            throw new IOException("Failed to add registry entry, exit code: " + exitCode);
        }
    }

    static String startupCommand(Path executablePath) throws IOException {
        executablePath = executablePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(executablePath)) {
            throw new IOException("Executable not found: " + executablePath);
        }

        String command = "\"" + executablePath.toAbsolutePath().normalize() + "\"";
        if (executablePath.getFileName().toString().endsWith(".jar")) {
            Path directory = executablePath.getParent();
            Path java = directory.resolve("jre/bin/javaw.exe");
            if (!Files.isRegularFile(java)) {
                throw new IOException("Bundled Java runtime not found: " + java);
            }
            command = "\"" + java + "\" -Dfile.encoding=UTF-8 -jar " + command
                    + " \"--base-dir=" + directory + "\"";
        }
        return command + " --auto-start";
    }

    /**
     * Disables auto-start by removing the registry entry.
     */
    static void disable() throws IOException, InterruptedException {
        if (!isEnabled()) {
            return;
        }
        int exitCode = runRegistry("delete", REGISTRY_KEY, "/v", APP_NAME, "/f");
        if (exitCode != 0) {
            throw new IOException("Failed to delete registry entry, exit code: " + exitCode);
        }
    }

    private static int runRegistry(String... arguments) throws IOException, InterruptedException {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("reg.exe");
        java.util.Collections.addAll(command, arguments);
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                throw new IOException("Registry command timed out");
            }
            return process.exitValue();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
