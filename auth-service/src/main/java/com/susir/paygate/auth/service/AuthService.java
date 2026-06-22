package com.susir.paygate.auth.service;

import com.susir.paygate.auth.dto.AuthRequest;
import com.susir.paygate.auth.dto.RegisterRequest;
import com.susir.paygate.auth.entity.User;
import com.susir.paygate.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// This is the business logic layer — the "brain" behind register and login.
// It uses JwtService to generate tokens and UserRepository to talk to the DB.
@Service
@RequiredArgsConstructor  // Lombok generates constructor for all final fields
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // REGISTER: Called when someone signs up.
    // 1. Check if username already exists — reject if so
    // 2. Encrypt the password with BCrypt before saving
    //    (we never store plain text passwords — ever)
    // 3. Save the new user to the database
    // 4. Return a JWT token so they're logged in immediately after registering
    public String register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new RuntimeException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        // BCrypt turns "mypassword" into something like "$2a$10$xyz..."
        // Even if someone steals the database, they can't reverse this
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole("ROLE_USER");

        userRepository.save(user);

        // Generate and return a token immediately after registration
        return jwtService.generateToken(request.getUsername());
    }

    // LOGIN: Called when someone signs in.
    // 1. Find the user by username — reject if not found
    // 2. Compare the submitted password against the stored BCrypt hash
    //    (BCrypt can verify a plain password against its own hash)
    // 3. If it matches, generate and return a JWT token
    // 4. If it doesn't match, reject with an error
    public String login(AuthRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid password");
        }

        return jwtService.generateToken(request.getUsername());
    }
}