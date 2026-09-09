package com.github.serezhka.airplay.launcher;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record GitHubRelease(String tagName, boolean draft, boolean prerelease, List<Asset> assets) {
    static GitHubRelease parse(String json) throws IOException {
        Parser parser = new Parser(json);
        Map<String, Object> fields = new HashMap<>();
        parser.expect('{');
        if (!parser.consume('}')) {
            do {
                String name = parser.string();
                parser.expect(':');
                Object value = switch (name) {
                    case "tag_name" -> parser.string();
                    case "draft", "prerelease" -> parser.bool();
                    case "assets" -> parser.assets();
                    default -> {
                        parser.skipValue(1);
                        yield null;
                    }
                };
                if (value != null && fields.putIfAbsent(name, value) != null) {
                    throw invalidResponse();
                }
            } while (parser.consume(','));
            parser.expect('}');
        }
        parser.whitespace();
        if (parser.offset != json.length() || !fields.keySet().containsAll(
                List.of("tag_name", "draft", "prerelease"))) {
            throw invalidResponse();
        }
        return new GitHubRelease((String) fields.get("tag_name"),
                (Boolean) fields.get("draft"), (Boolean) fields.get("prerelease"),
                fields.containsKey("assets") ? castAssets(fields.get("assets")) : List.of());
    }

    @SuppressWarnings("unchecked")
    private static List<Asset> castAssets(Object value) {
        return (List<Asset>) value;
    }

    record Asset(String name, long size, String digest, String state, String downloadUrl) {
    }

    private static IOException invalidResponse() {
        return new LauncherIOException(LauncherMessages.Key.ERROR_UPDATE_RESPONSE);
    }

    private static final class Parser {
        private static final Pattern NUMBER = Pattern.compile(
                "-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
        private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{4}");
        private static final int MAX_DEPTH = 32;
        private final String json;
        private int offset;

        private Parser(String json) throws IOException {
            if (json == null) {
                throw invalidResponse();
            }
            this.json = json;
        }

        private List<Asset> assets() throws IOException {
            whitespace();
            if (json.startsWith("null", offset)) {
                offset += 4;
                return List.of();
            }
            expect('[');
            List<Asset> assets = new ArrayList<>();
            if (!consume(']')) {
                do {
                    if (assets.size() >= 256) {
                        throw invalidResponse();
                    }
                    expect('{');
                    Map<String, String> values = new HashMap<>();
                    Set<String> names = new HashSet<>();
                    if (!consume('}')) {
                        do {
                            String name = string();
                            if (!names.add(name)) {
                                throw invalidResponse();
                            }
                            expect(':');
                            whitespace();
                            String value = null;
                            if (List.of("name", "digest", "state", "browser_download_url").contains(name)
                                    && offset < json.length() && json.charAt(offset) == '"') {
                                value = string();
                            } else if (name.equals("size")) {
                                int start = offset;
                                skipValue(3);
                                value = json.substring(start, offset);
                            } else {
                                skipValue(3);
                            }
                            if (value != null && values.putIfAbsent(name, value) != null) {
                                throw invalidResponse();
                            }
                        } while (consume(','));
                        expect('}');
                    }
                    long size = -1;
                    try {
                        size = new BigDecimal(values.getOrDefault("size", "-1")).longValueExact();
                    } catch (NumberFormatException | ArithmeticException ignored) {
                    }
                    assets.add(new Asset(values.get("name"), size, values.get("digest"),
                            values.get("state"), values.get("browser_download_url")));
                } while (consume(','));
                expect(']');
            }
            return List.copyOf(assets);
        }

        private String string() throws IOException {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (offset < json.length()) {
                char character = json.charAt(offset++);
                if (character == '"') {
                    return value.toString();
                }
                if (character < 0x20) {
                    throw invalidResponse();
                }
                if (character != '\\') {
                    value.append(character);
                    continue;
                }
                if (offset == json.length()) {
                    throw invalidResponse();
                }
                char escaped = json.charAt(offset++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> {
                        if (offset + 4 > json.length()) {
                            throw invalidResponse();
                        }
                        String digits = json.substring(offset, offset + 4);
                        if (!HEX.matcher(digits).matches()) {
                            throw invalidResponse();
                        }
                        value.append((char) Integer.parseInt(digits, 16));
                        offset += 4;
                    }
                    default -> throw invalidResponse();
                }
            }
            throw invalidResponse();
        }

        private boolean bool() throws IOException {
            whitespace();
            if (json.startsWith("true", offset)) {
                offset += 4;
                return true;
            }
            if (json.startsWith("false", offset)) {
                offset += 5;
                return false;
            }
            throw invalidResponse();
        }

        private void skipValue(int depth) throws IOException {
            whitespace();
            if (depth > MAX_DEPTH || offset == json.length()) {
                throw invalidResponse();
            }
            switch (json.charAt(offset)) {
                case '{' -> {
                    expect('{');
                    if (!consume('}')) {
                        do {
                            string();
                            expect(':');
                            skipValue(depth + 1);
                        } while (consume(','));
                        expect('}');
                    }
                }
                case '[' -> {
                    expect('[');
                    if (!consume(']')) {
                        do {
                            skipValue(depth + 1);
                        } while (consume(','));
                        expect(']');
                    }
                }
                case '"' -> string();
                case 't', 'f' -> bool();
                case 'n' -> {
                    if (!json.startsWith("null", offset)) {
                        throw invalidResponse();
                    }
                    offset += 4;
                }
                default -> {
                    Matcher number = NUMBER.matcher(json).region(offset, json.length());
                    if (!number.lookingAt()) {
                        throw invalidResponse();
                    }
                    offset = number.end();
                }
            }
        }

        private void expect(char character) throws IOException {
            if (!consume(character)) {
                throw invalidResponse();
            }
        }

        private boolean consume(char character) {
            whitespace();
            if (offset < json.length() && json.charAt(offset) == character) {
                offset++;
                return true;
            }
            return false;
        }

        private void whitespace() {
            while (offset < json.length() && " \t\r\n".indexOf(json.charAt(offset)) >= 0) {
                offset++;
            }
        }
    }
}
