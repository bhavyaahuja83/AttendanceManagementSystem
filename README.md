# Daymark Attendance

A Java 17+ Jakarta Servlet application for employee check-in, with an H2 SQL
database and a responsive admin dashboard.

## Run locally

1. Deploy the generated WAR to a Jakarta-compatible Tomcat installation
   (Tomcat 10.1+), or run it from NetBeans with a Jakarta-compatible server.
2. Keep the existing `admin_password` entry in the project-root `.env` file.
   The app looks for that file from its working directory and deployed web-root
   ancestors. Alternatively, set `ADMIN_PASSWORD` in the server environment,
   or set `ADMIN_PASSWORD_FILE` to the absolute path of a file containing
   `admin_password=...`.
3. Build with `mvn package`, then deploy
   `target/AttendanceManagementSystem-1.0-SNAPSHOT.war`.
4. Open `/AttendanceManagementSystem/` for employee check-in or
   `/AttendanceManagementSystem/admin.html` for the admin dashboard.

The default H2 database is stored under the current operating-system user's
home directory in `.attendance-management/attendance`. Set the server
environment variable `ATTENDANCE_DB_PATH` to choose a different database file.

Employees enter their generated code and submit a photo with each punch. The
first punch of the day is recorded as check-in; the next is check-out, and
subsequent punches alternate. Photos are stored as SQL BLOBs and can only be
viewed by a signed-in admin. The photo is an attendance record, not automated
face recognition or identity verification.

Camera access requires HTTPS in a deployed environment (browsers also allow
camera access on `localhost`). Attendance timestamps use the application
server's local time.
