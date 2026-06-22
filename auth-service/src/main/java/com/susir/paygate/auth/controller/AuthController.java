package com.susir.paygate.auth.controller;

import com.susir.paygate.auth.dto.AuthRequest;
import com.susir.paygate.auth.dto.RegisterRequest;
import com.susir.paygate.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// This is the front door of auth-service.
// It exposes two endpoints to the outside world via the gateway:
// POST /auth/register — create a new account
// POST /auth/login    — sign in and get a JWT token
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // REGISTER endpoint
    // Receives: { "username": "susir", "password": "mypassword" }
    // Returns:  { "token": "eyJhbGci..." }
    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        String token = authService.register(request);
        return ResponseEntity.ok(token);
    }

    // LOGIN endpoint
    // Receives: { "username": "susir", "password": "mypassword" }
    // Returns:  { "token": "eyJhbGci..." }
    // In remittance terms: this is like authenticating onto the payment
    // network — you prove who you are once, get a session credential,
    // and use that credential for all subsequent transactions.
    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody AuthRequest request) {
        String token = authService.login(request);
        return ResponseEntity.ok(token);
    }
}