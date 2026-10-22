package com.mycompany.attendancemanagementsystem;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.regex.Pattern;

public final class Database {
    private static final Pattern SAFE_DATABASE_NAME = Pattern.compile("[A-Za-z0-9_$]+");
    private static final Pattern SAFE_HOST = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.-]*");

    private static String url;
    private static String username;
    private static String password;

    private Database() {
    }

    public static synchronized void initialize(String webRoot) throws SQLException {
        String host = setting("MYSQL_HOST", webRoot, "127.0.0.1");
        String port = setting("MYSQL_PORT", webRoot, "3306");
        String database = setting("MYSQL_DATABASE", webRoot, null);
        username = setting("MYSQL_USER", webRoot, null);
        password = setting("MYSQL_PASSWORD", webRoot, "");

        if (database == null || database.isBlank()) {
            throw new SQLException("MYSQL_DATABASE is missing. Configure it in .env or the server environment.");
        }
        if (!SAFE_DATABASE_NAME.matcher(database).matches()) {
            throw new SQLException("MYSQL_DATABASE must contain only letters, digits, underscores, or dollar signs.");
        }
        if (username == null || username.isBlank()) {
            throw new SQLException("MYSQL_USER is missing. Configure it in .env or the server environment.");
        }
        if (!SAFE_HOST.matcher(host).matches() || !port.matches("\\d{1,5}")
                || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535) {
            throw new SQLException("MYSQL_HOST or MYSQL_PORT is invalid.");
        }

        url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?connectionTimeZone=LOCAL";
        try {
            Class.forName("com.mysql.cj.jdbc.Driver", true, Database.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new SQLException("MySQL JDBC driver is missing from the application. Rebuild and redeploy the WAR.",
                    exception);
        }
        try (Connection connection = getConnection();
             var statement = connection.createStatement()) {
            statement.executeQuery("SELECT 1 FROM employees LIMIT 0").close();
            statement.executeQuery("SELECT 1 FROM attendance LIMIT 0").close();
        } catch (SQLException exception) {
            throw new SQLException("Could not connect to MySQL or find the attendance tables. "
                    + "Check MYSQL_HOST, MYSQL_PORT, MYSQL_DATABASE, MYSQL_USER, and MYSQL_PASSWORD, "
                    + "then run database/schema.sql.", exception);
        }
    }

    public static Connection getConnection() throws SQLException {
        if (url == null) {
            throw new SQLException("The MySQL database has not been initialized.");
        }
        return DriverManager.getConnection(url, username, password);
    }

    private static String setting(String key, String webRoot, String defaultValue) {
        String value = EnvironmentConfig.get(key, webRoot);
        return value == null ? defaultValue : value;
    }
}
