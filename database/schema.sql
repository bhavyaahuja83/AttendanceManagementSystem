CREATE DATABASE IF NOT EXISTS attendance_management
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE attendance_management;

CREATE TABLE IF NOT EXISTS employees (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employee_code VARCHAR(24) NULL,
    full_name VARCHAR(120) NOT NULL,
    department VARCHAR(120) NOT NULL,
    email VARCHAR(180) NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_employees_employee_code (employee_code)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS attendance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    employee_id BIGINT NOT NULL,
    event_type VARCHAR(3) NOT NULL,
    recorded_at DATETIME NOT NULL,
    photo MEDIUMBLOB NOT NULL,
    PRIMARY KEY (id),
    KEY idx_attendance_employee_time (employee_id, recorded_at),
    CONSTRAINT fk_attendance_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
