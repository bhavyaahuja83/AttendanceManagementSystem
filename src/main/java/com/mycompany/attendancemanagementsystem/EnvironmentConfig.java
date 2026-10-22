package com.mycompany.attendancemanagementsystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class EnvironmentConfig {
    private EnvironmentConfig() {
    }

    public static String get(String key, String webRoot) {
        String value = System.getenv(key);
        if (value != null) {
            return value;
        }

        Path[] roots = {
            Path.of(System.getProperty("user.dir")).toAbsolutePath(),
            webRoot == null ? null : Path.of(webRoot).toAbsolutePath()
        };
        for (Path root : roots) {
            for (Path directory = root; directory != null; directory = directory.getParent()) {
                Path envFile = directory.resolve(".env");
                if (Files.isRegularFile(envFile)) {
                    value = read(envFile, key);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static String read(Path path, String key) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String trimmed = line.trim();
                int separator = trimmed.indexOf('=');
                if (separator > 0 && key.equals(trimmed.substring(0, separator).trim())) {
                    String value = trimmed.substring(separator + 1).trim();
                    if (value.length() >= 2
                            && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read environment file: " + path, exception);
        }
        return null;
    }
}
