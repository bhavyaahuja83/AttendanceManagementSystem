# Daymark Attendance

A Java 17+ Jakarta Servlet application for employee check-in, with a local
MySQL database and a responsive admin dashboard.

## Run locally

1. Start a MySQL 8+ server locally on port `3306`.
2. Fill in `MYSQL_DATABASE`, `MYSQL_USER`, and `MYSQL_PASSWORD` in the
   project-root `.env` file. Host and port default to `127.0.0.1` and `3306`;
   override those with `MYSQL_HOST` and `MYSQL_PORT` if needed.
3. Run [`database/schema.sql`](./database/schema.sql) against your local MySQL
   instance before starting the app. It creates the `attendance_management`
   database and the employee and attendance tables. The configured MySQL user
   must have privileges to read and write those tables. For example, from a
   MySQL administrator account, create a dedicated login with
   `CREATE USER 'attendance_app'@'127.0.0.1' IDENTIFIED BY 'choose-a-password';`
   and `GRANT SELECT, INSERT, UPDATE, DELETE ON attendance_management.* TO
   'attendance_app'@'127.0.0.1';`. Use that username and password in `.env`.
4. Keep the existing `admin_password` entry in the project-root `.env` file.
   The app looks for that file from its working directory and deployed web-root
   ancestors. Alternatively, set `ADMIN_PASSWORD` in the server environment,
   or set `ADMIN_PASSWORD_FILE` to the absolute path of a file containing
   `admin_password=...`.
5. Build with `mvn package`, then deploy
   `target/AttendanceManagementSystem-1.0-SNAPSHOT.war`.
6. Open `/AttendanceManagementSystem/` for employee check-in or
   `/AttendanceManagementSystem/admin.html` for the admin dashboard.

Employees enter their generated code and submit a photo with each punch. The
first punch of the day is recorded as check-in; the next is check-out, and
subsequent punches alternate. Photos are stored as SQL BLOBs and can only be
viewed by a signed-in admin. The photo is an attendance record, not automated
face recognition or identity verification.

Camera access requires HTTPS in a deployed environment (browsers also allow
camera access on `localhost`). Attendance timestamps use the application
server's local time.
