package com.deskweave.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents an employee or facilities administrator account.
 */
public class User {

    private final int userId;
    private final String fullName;
    private final String email;
    private final String passwordHash;
    private final UserRole role;
    private final String department;
    private final LocalDateTime createdAt;

    public User(int userId, String fullName, String email, String passwordHash,
                UserRole role, String department, LocalDateTime createdAt) {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name must not be blank");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        this.userId = userId;
        this.fullName = fullName.trim();
        this.email = email.trim().toLowerCase();
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.department = department == null ? "" : department.trim();
        this.createdAt = createdAt;
    }

    public int getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public String getDepartment() {
        return department;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    @Override
    public String toString() {
        return fullName + " <" + email + "> [" + role + "]";
    }
}
