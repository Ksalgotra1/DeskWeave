package com.deskweave.service;

import com.deskweave.config.AppLogger;
import com.deskweave.config.PasswordHasher;
import com.deskweave.dao.UserDao;
import com.deskweave.exception.AuthenticationException;
import com.deskweave.exception.DeskWeaveException;
import com.deskweave.model.User;
import com.deskweave.model.UserRole;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Handles user authentication and registration.
 */
public class AuthService {

    private static final Logger LOGGER = AppLogger.getLogger(AuthService.class);

    private final UserDao userDao;

    public AuthService(UserDao userDao) {
        this.userDao = Objects.requireNonNull(userDao, "userDao must not be null");
    }

    /**
     * Authenticates an employee or administrator by email and plaintext password.
     */
    public User authenticate(String email, String plainPassword) throws AuthenticationException {
        if (email == null || email.isBlank() || plainPassword == null || plainPassword.isEmpty()) {
            throw new AuthenticationException("Email and password are required");
        }
        String normalizedEmail = email.trim().toLowerCase();
        User user = userDao.findByEmail(normalizedEmail)
                .orElseThrow(() -> {
                    LOGGER.warning("Authentication failed: unknown email " + normalizedEmail);
                    return new AuthenticationException("Invalid email or password");
                });

        if (!PasswordHasher.verify(plainPassword, user.getPasswordHash())) {
            LOGGER.warning("Authentication failed: invalid password for " + normalizedEmail);
            throw new AuthenticationException("Invalid email or password");
        }

        LOGGER.info("User signed in: " + user.getEmail() + " (" + user.getRole() + ")");
        return user;
    }

    /**
     * Registers a new user account after validating email uniqueness.
     */
    public User registerUser(String fullName,
                             String email,
                             String plainPassword,
                             UserRole role,
                             String department) throws DeskWeaveException {
        if (email == null || email.isBlank()) {
            throw new DeskWeaveException("Email must not be blank");
        }
        if (plainPassword == null || plainPassword.length() < 6) {
            throw new DeskWeaveException("Password must be at least 6 characters");
        }
        String normalizedEmail = email.trim().toLowerCase();
        if (userDao.findByEmail(normalizedEmail).isPresent()) {
            throw new DeskWeaveException("An account already exists for email: " + normalizedEmail);
        }

        String hash = PasswordHasher.hash(plainPassword);
        User created = userDao.save(new User(
                0,
                fullName,
                normalizedEmail,
                hash,
                Objects.requireNonNull(role, "role must not be null"),
                department,
                LocalDateTime.now()
        ));
        LOGGER.info("Registered new user: " + created.getEmail() + " [" + created.getRole() + "]");
        return created;
    }

    public List<User> listAllUsers() {
        return userDao.findAll();
    }
}
