package com.vault.authservice.service;

import com.vault.authservice.dto.RegisterRequest;
import com.vault.authservice.dto.RegisterResponse;
import com.vault.authservice.exception.EmailAlreadyExistsException;
import com.vault.authservice.exception.EmailConstraintViolationException;
import com.vault.authservice.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.vault.authservice.entity.User;
import org.hibernate.exception.ConstraintViolationException;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private boolean isEmailUniqueConstraintViolation(
            DataIntegrityViolationException exception) {
        Throwable cause = exception;

        while (cause != null) {

            if(cause instanceof ConstraintViolationException violation) {
                return "uk_users_email".equals(violation.getConstraintName());
            }

            cause = cause.getCause();
        }

        return false;
    }

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if(userRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyExistsException("Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getName().trim(),
                email,
                passwordHash
        );

        User savedUser;

        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {

            if (isEmailUniqueConstraintViolation(exception)) {
                throw new EmailConstraintViolationException(
                        "Email is already registered",
                        exception
                );
            }

            throw exception;
        }

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getCreatedAt()
        );
    }

}
