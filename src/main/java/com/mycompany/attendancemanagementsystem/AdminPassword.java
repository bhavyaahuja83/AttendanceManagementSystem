package com.mycompany.attendancemanagementsystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AdminPassword {
    private AdminPassword() {
    }

    public static String get(String webRoot) {
        String fromEnvironment = System.getenv("ADMIN_PASSWORD");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }

        String fromEnvironmentFile = System.getenv("ADMIN_PASSWORD_FILE");
        if (fromEnvironmentFile != null && !fromEnvironmentFile.isBlank()) {
            return readPassword(Path.of(fromEnvironmentFile));
        }

        Path[] roots = {
            Path.of(System.getProperty("user.dir")).toAbsolutePath(),
            webRoot == null ? null : Path.of(webRoot).toAbsolutePath()
        };
        for (Path root : roots) {
            Path directory = root;
            for (int level = 0; directory != null && level < 8; level++, directory = directory.getParent()) {
                Path envFile = directory.resolve(".env");
                if (Files.isRegularFile(envFile)) {
                    return readPassword(envFile);
                }
            }
        }
        return null;
    }

    private static String readPassword(Path path) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("admin_password=")) {
                    String value = trimmed.substring("admin_password=".length()).trim();
                    if (value.length() >= 2
                            && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }
                    return value;
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read the admin password file: " + path, exception);
        }
        return null;
    }
}
