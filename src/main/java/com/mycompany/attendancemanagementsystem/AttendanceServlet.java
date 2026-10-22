package com.mycompany.attendancemanagementsystem;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@WebServlet(name = "AttendanceServlet", urlPatterns = "/api/*")
public class AttendanceServlet extends HttpServlet {
    private static final String ADMIN_SESSION_KEY = "attendanceAdmin";
    private static final int MAX_PHOTO_BYTES = 5 * 1024 * 1024;
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @Override
    public void init() throws ServletException {
        try {
            Database.initialize();
        } catch (SQLException exception) {
            throw new ServletException("Unable to initialize the attendance database.", exception);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String path = request.getPathInfo();
            if ("/session".equals(path)) {
                json(response, "{\"authenticated\":" + isAdmin(request) + "}");
            } else if ("/employee".equals(path)) {
                findEmployee(request, response);
            } else if ("/admin/employees".equals(path)) {
                if (requireAdmin(request, response)) {
                    listEmployees(response);
                }
            } else if ("/admin/attendance".equals(path)) {
                if (requireAdmin(request, response)) {
                    listAttendance(request, response);
                }
            } else if ("/admin/summary".equals(path)) {
                if (requireAdmin(request, response)) {
                    summary(response);
                }
            } else if ("/admin/photo".equals(path)) {
                if (requireAdmin(request, response)) {
                    photo(request, response);
                }
            } else {
                jsonError(response, HttpServletResponse.SC_NOT_FOUND, "Not found.");
            }
        } catch (SQLException exception) {
            throw new IOException("The attendance database could not complete the request.", exception);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            String path = request.getPathInfo();
            if ("/admin/login".equals(path)) {
                login(request, response);
            } else if ("/admin/logout".equals(path)) {
                logout(request, response);
            } else if ("/admin/employees".equals(path)) {
                if (requireAdmin(request, response)) {
                    createEmployee(request, response);
                }
            } else if ("/attendance".equals(path)) {
                recordAttendance(request, response);
            } else {
                jsonError(response, HttpServletResponse.SC_NOT_FOUND, "Not found.");
            }
        } catch (SQLException exception) {
            throw new IOException("The attendance database could not complete the request.", exception);
        }
    }

    private void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String configuredPassword = AdminPassword.get(getServletContext().getRealPath("/"));
        if (configuredPassword == null || configuredPassword.isBlank()) {
            jsonError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Admin password is not configured. Set ADMIN_PASSWORD or add admin_password to .env.");
            return;
        }
        String supplied = request.getParameter("password");
        if (supplied == null || !MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                configuredPassword.getBytes(StandardCharsets.UTF_8))) {
            jsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "That password didn't match.");
            return;
        }
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(ADMIN_SESSION_KEY, Boolean.TRUE);
        json(response, "{\"authenticated\":true}");
    }

    private void logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        json(response, "{\"authenticated\":false}");
    }

    private void findEmployee(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        String code = request.getParameter("code");
        if (code == null || code.isBlank()) {
            jsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Enter an employee code.");
            return;
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT employee_code, full_name, department FROM employees WHERE UPPER(employee_code) = ?")) {
            statement.setString(1, code.trim().toUpperCase());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    jsonError(response, HttpServletResponse.SC_NOT_FOUND,
                            "We couldn't find that employee code. Check it and try again.");
                    return;
                }
                json(response, "{\"employeeCode\":" + quote(result.getString("employee_code")) + "}");
            }
        }
    }

    private void createEmployee(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        String name = trim(request.getParameter("name"));
        String department = trim(request.getParameter("department"));
        String email = trim(request.getParameter("email"));
        if (name.isEmpty() || department.isEmpty()) {
            jsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Name and department are required.");
            return;
        }
        if (name.length() > 120 || department.length() > 120 || email.length() > 180) {
            jsonError(response, HttpServletResponse.SC_BAD_REQUEST, "One or more employee fields are too long.");
            return;
        }
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO employees (full_name, department, email, created_at) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                insert.setString(1, name);
                insert.setString(2, department);
                insert.setString(3, email.isEmpty() ? null : email);
                insert.setTimestamp(4, Timestamp.valueOf(LocalDateTime.now()));
                insert.executeUpdate();
                try (ResultSet keys = insert.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("The database did not return the new employee ID.");
                    }
                    long id = keys.getLong(1);
                    String code = String.format("EMP-%05d", id);
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE employees SET employee_code = ? WHERE id = ?")) {
                        update.setString(1, code);
                        update.setLong(2, id);
                        update.executeUpdate();
                    }
                    connection.commit();
                    response.setStatus(HttpServletResponse.SC_CREATED);
                    json(response, employeeJson(code, name, department, email));
                }
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private void recordAttendance(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        String code = request.getHeader("X-Employee-Code");
        if (code == null || code.isBlank()) {
            jsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Enter your employee code first.");
            return;
        }
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/jpeg")) {
            jsonError(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Please capture a photo before recording attendance.");
            return;
        }
        byte[] image = readPhoto(request.getInputStream());
        if (image.length == 0 || image.length > MAX_PHOTO_BYTES) {
            jsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Photo must be between 1 byte and 5 MB.");
            return;
        }
        if (!isValidPhoto(image)) {
            jsonError(response, HttpServletResponse.SC_BAD_REQUEST, "That photo is not a valid image or is too large.");
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        try (Connection connection = Database.getConnection()) {
            long employeeId;
            String name;
            try (PreparedStatement employee = connection.prepareStatement(
                    "SELECT id, full_name FROM employees WHERE UPPER(employee_code) = ?")) {
                employee.setString(1, code.trim().toUpperCase());
                try (ResultSet result = employee.executeQuery()) {
                    if (!result.next()) {
                        jsonError(response, HttpServletResponse.SC_NOT_FOUND,
                                "We couldn't find that employee code. Check it and try again.");
                        return;
                    }
                    employeeId = result.getLong("id");
                    name = result.getString("full_name");
                }
            }
            String eventType = "IN";
            try (PreparedStatement latest = connection.prepareStatement(
                    "SELECT event_type FROM attendance WHERE employee_id = ? AND CAST(recorded_at AS DATE) = ? "
                    + "ORDER BY recorded_at DESC, id DESC FETCH FIRST 1 ROW ONLY")) {
                latest.setLong(1, employeeId);
                latest.setDate(2, Date.valueOf(now.toLocalDate()));
                try (ResultSet result = latest.executeQuery()) {
                    if (result.next() && "IN".equals(result.getString("event_type"))) {
                        eventType = "OUT";
                    }
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO attendance (employee_id, event_type, recorded_at, photo) VALUES (?, ?, ?, ?)")) {
                insert.setLong(1, employeeId);
                insert.setString(2, eventType);
                insert.setTimestamp(3, Timestamp.valueOf(now));
                insert.setBytes(4, image);
                insert.executeUpdate();
            }
            json(response, "{\"employeeName\":" + quote(name) + ",\"eventType\":" + quote(eventType)
                    + ",\"recordedAt\":" + quote(DATE_TIME_FORMAT.format(now)) + "}");
        }
    }

    private void listEmployees(HttpServletResponse response) throws SQLException, IOException {
        List<String> employees = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT employee_code, full_name, department, email FROM employees ORDER BY id DESC");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                employees.add(employeeJson(result.getString("employee_code"), result.getString("full_name"),
                        result.getString("department"), result.getString("email")));
            }
        }
        json(response, "[" + String.join(",", employees) + "]");
    }

    private void listAttendance(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        StringBuilder sql = new StringBuilder("""
                SELECT a.id, e.employee_code, e.full_name, e.department, a.event_type, a.recorded_at
                FROM attendance a JOIN employees e ON e.id = a.employee_id WHERE 1 = 1
                """);
        List<Object> parameters = new ArrayList<>();
        String employee = trim(request.getParameter("employee"));
        String date = trim(request.getParameter("date"));
        String from = trim(request.getParameter("from"));
        String to = trim(request.getParameter("to"));
        if (!employee.isEmpty()) {
            sql.append(" AND UPPER(e.employee_code) = ?");
            parameters.add(employee.toUpperCase());
        }
        if (!date.isEmpty()) {
            try {
                sql.append(" AND CAST(a.recorded_at AS DATE) = ?");
                parameters.add(Date.valueOf(LocalDate.parse(date)));
            } catch (RuntimeException exception) {
                jsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Choose a valid date.");
                return;
            }
        }
        try {
            if (!from.isEmpty()) {
                sql.append(" AND CAST(a.recorded_at AS TIME) >= ?");
                parameters.add(Time.valueOf(from + (from.length() == 5 ? ":00" : "")));
            }
            if (!to.isEmpty()) {
                sql.append(" AND CAST(a.recorded_at AS TIME) <= ?");
                parameters.add(Time.valueOf(to + (to.length() == 5 ? ":00" : "")));
            }
        } catch (IllegalArgumentException exception) {
            jsonError(response, HttpServletResponse.SC_BAD_REQUEST, "Choose a valid time range.");
            return;
        }
        sql.append(" ORDER BY a.recorded_at DESC, a.id DESC FETCH FIRST 500 ROWS ONLY");
        List<String> rows = new ArrayList<>();
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int index = 0; index < parameters.size(); index++) {
                statement.setObject(index + 1, parameters.get(index));
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    LocalDateTime recordedAt = result.getTimestamp("recorded_at").toLocalDateTime();
                    rows.add("{\"id\":" + result.getLong("id")
                            + ",\"employeeCode\":" + quote(result.getString("employee_code"))
                            + ",\"name\":" + quote(result.getString("full_name"))
                            + ",\"department\":" + quote(result.getString("department"))
                            + ",\"eventType\":" + quote(result.getString("event_type"))
                            + ",\"recordedAt\":" + quote(DATE_TIME_FORMAT.format(recordedAt)) + "}");
                }
            }
        }
        json(response, "[" + String.join(",", rows) + "]");
    }

    private void summary(HttpServletResponse response) throws SQLException, IOException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT
                         (SELECT COUNT(*) FROM employees) AS employee_count,
                         (SELECT COUNT(*) FROM attendance WHERE CAST(recorded_at AS DATE) = ?) AS today_count,
                         (SELECT COUNT(*) FROM (
                             SELECT employee_id, event_type,
                                    ROW_NUMBER() OVER (PARTITION BY employee_id ORDER BY recorded_at DESC, id DESC) AS rn
                             FROM attendance WHERE CAST(recorded_at AS DATE) = ?
                         ) latest WHERE rn = 1 AND event_type = 'IN') AS on_site_count
                     """)) {
            Date today = Date.valueOf(LocalDate.now());
            statement.setDate(1, today);
            statement.setDate(2, today);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                json(response, "{\"employeeCount\":" + result.getInt("employee_count")
                        + ",\"todayCount\":" + result.getInt("today_count")
                        + ",\"onSiteCount\":" + result.getInt("on_site_count") + "}");
            }
        }
    }

    private void photo(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, IOException {
        String id = request.getParameter("id");
        if (id == null || !id.matches("\\d+")) {
            jsonError(response, HttpServletResponse.SC_BAD_REQUEST, "A valid photo ID is required.");
            return;
        }
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT photo FROM attendance WHERE id = ?")) {
            statement.setLong(1, Long.parseLong(id));
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    jsonError(response, HttpServletResponse.SC_NOT_FOUND, "Photo not found.");
                    return;
                }
                response.setContentType("image/jpeg");
                response.setHeader("Cache-Control", "no-store");
                response.getOutputStream().write(result.getBytes("photo"));
            }
        }
    }

    private boolean requireAdmin(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (isAdmin(request)) {
            return true;
        }
        jsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Please sign in as an admin first.");
        return false;
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && Boolean.TRUE.equals(session.getAttribute(ADMIN_SESSION_KEY));
    }

    private static byte[] readPhoto(InputStream input) throws IOException {
        return input.readNBytes(MAX_PHOTO_BYTES + 1);
    }

    private static boolean isValidPhoto(byte[] photo) throws IOException {
        if (photo.length < 4 || (photo[0] & 0xff) != 0xff || (photo[1] & 0xff) != 0xd8) {
            return false;
        }
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(photo))) {
            if (imageInput == null) {
                return false;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                return false;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName();
                if (!"jpeg".equalsIgnoreCase(format) && !"jpg".equalsIgnoreCase(format)) {
                    return false;
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > 16_000_000) {
                    return false;
                }
                BufferedImage image = reader.read(0);
                return image != null;
            } finally {
                reader.dispose();
            }
        }
    }

    private static String employeeJson(String code, String name, String department, String email) {
        return "{\"employeeCode\":" + quote(code) + ",\"name\":" + quote(name)
                + ",\"department\":" + quote(department) + ",\"email\":" + quote(email) + "}";
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String quote(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    private static void json(HttpServletResponse response, String value) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(value);
    }

    private static void jsonError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        json(response, "{\"error\":" + quote(message) + "}");
    }
}
