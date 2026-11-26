package com.vault.authservice.service;

import com.vault.authservice.dto.RegisterRequest;
import com.vault.authservice.dto.RegisterResponse;
import com.vault.authservice.entity.User;

import com.vault.authservice.exception.EmailAlreadyExistsException;
import com.vault.authservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_shouldSaveUserAndReturnResponse() {

        //Arrange
        RegisterRequest request = new RegisterRequest();
        request.setName("Shashank");
        request.setEmail("SHASHANK@GMAIL.COM");
        request.setPassword("password123");

        when(userRepository.findByEmail("shashank@gmail.com"))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode("password123"))
                .thenReturn("hashedPassword");


        User savedUser = new User(
                "Shashank",
                "shashank@gmail.com",
                "hashedPassword"
        );

        when(userRepository.saveAndFlush(any(User.class)))
                .thenReturn(savedUser);

        //Act
        RegisterResponse response = authService.register(request);

        // Assert
        assertNotNull(response);
        assertEquals("Shashank", response.getName());
        assertEquals("shashank@gmail.com", response.getEmail());

        verify(userRepository).findByEmail("shashank@gmail.com");
        verify(passwordEncoder).encode("password123");
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).saveAndFlush(userCaptor.capture());

        User capturedUser = userCaptor.getValue();

        assertEquals("Shashank", capturedUser.getName());
        assertEquals("shashank@gmail.com", capturedUser.getEmail());
        assertEquals("hashedPassword", capturedUser.getPasswordHash());

        assertNotEquals(
                "password123",
                capturedUser.getPasswordHash()
        );

        verify(passwordEncoder).encode("password123");
    }

    @Test
    void register_shouldThrowExceptionWhenEmailAlreadyExists() {

        RegisterRequest request = new RegisterRequest();
        request.setName("Shashank");
        request.setEmail("shashank@gmail.com");
        request.setPassword("password123");

        User existingUser = new User(
                "Existing User",
                "shashank@gmail.com",
                "existingHash"
        );

        when(userRepository.findByEmail("shashank@gmail.com"))
                .thenReturn(Optional.of(existingUser));

        assertThrows(
                EmailAlreadyExistsException.class,
                () -> authService.register(request)
        );

        verify(userRepository).findByEmail("shashank@gmail.com");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }
}
