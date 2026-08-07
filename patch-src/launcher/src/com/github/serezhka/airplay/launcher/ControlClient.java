package com.github.serezhka.airplay.launcher;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class ControlClient {
    private static final int CONNECT_TIMEOUT_MILLIS = 350;
    private static final int READ_TIMEOUT_MILLIS = 800;
    private static final int MAX_RESPONSE_LENGTH = 512;

    record Status(boolean running, boolean fullscreenAvailable, boolean fullscreen) {
    }

    Status status(int port, String token) throws IOException {
        String[] fields = fields(request(port, token + "\tSTATUS"));
        if (fields.length != 8
                || !"OK".equals(fields[0])
                || !"STATUS".equals(fields[1])
                || !"RUNNING".equals(fields[2])
                || !"FULLSCREEN_AVAILABLE".equals(fields[4])
                || !"FULLSCREEN".equals(fields[6])) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_INVALID_STATUS_RESPONSE);
        }
        return new Status(bool(fields[3]), bool(fields[5]), bool(fields[7]));
    }

    boolean setFullscreen(int port, String token, boolean fullscreen) throws IOException {
        String[] fields = fields(request(port, token + "\tFULLSCREEN\t" + fullscreen));
        if (fields.length != 3 || !"OK".equals(fields[0]) || !"FULLSCREEN".equals(fields[1])) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_INVALID_FULLSCREEN_RESPONSE);
        }
        return bool(fields[2]);
    }

    void quit(int port, String token) throws IOException {
        String[] fields = fields(request(port, token + "\tQUIT"));
        if (fields.length != 2 || !"OK".equals(fields[0]) || !"QUIT".equals(fields[1])) {
            throw new LauncherIOException(LauncherMessages.Key.ERROR_INVALID_QUIT_RESPONSE);
        }
    }

    private String request(int port, String request) throws IOException {
        if (port < 1 || port > 65535 || tokenContainsControlCharacter(request.substring(0, request.indexOf('\t')))) {
            throw new LauncherInputException(LauncherMessages.Key.ERROR_INVALID_CONTROL_ENDPOINT);
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            try (BufferedWriter writer = new BufferedWriter(
                         new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
                writer.write(request);
                writer.newLine();
                writer.flush();
                String response = reader.readLine();
                if (response == null || response.length() > MAX_RESPONSE_LENGTH) {
                    throw new LauncherIOException(LauncherMessages.Key.ERROR_MISSING_CONTROL_RESPONSE);
                }
                if (response.startsWith("ERR\t")) {
                    throw new LauncherIOException(
                            LauncherMessages.Key.ERROR_CONTROL_REJECTED, response.substring(4));
                }
                return response;
            }
        }
    }

    private static String[] fields(String response) {
        return response.split("\t", -1);
    }

    private static boolean bool(String value) throws IOException {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new LauncherIOException(LauncherMessages.Key.ERROR_INVALID_CONTROL_BOOLEAN, value);
    }

    private static boolean tokenContainsControlCharacter(String token) {
        return token.isEmpty() || token.chars().anyMatch(character -> character < 0x21 || character > 0x7e);
    }
}
