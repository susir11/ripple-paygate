package com.susir.paygate.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

// This class is the "token printing press" of the whole system.
// It does two things:
// 1. GENERATE a token when a user logs in successfully
// 2. VALIDATE a token when the gateway receives a request
@Service
public class JwtService {

    // This secret key is read from application.yml — never hardcoded here.
    // Think of it like the signing key a central bank uses to sign banknotes.
    // Anyone can read the banknote (token), but only the bank (your server)
    // can produce a valid signature. Without this key, tokens can't be forged.
    @Value("${jwt.secret}")
    private String secretKey;

    // How long the token is valid — read from application.yml.
    // After this time, the token expires and the user must log in again.
    // In remittance terms: like a value date — instructions are only
    // valid within a specific processing window.
    @Value("${jwt.expiration}")
    private long expirationMs;

    // Converts the raw secret string into a cryptographic signing key.
    // This is called internally every time we sign or verify a token.
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    // GENERATE: Called after a successful login.
    // Takes the username, builds a signed token, and returns it.
    // The token contains:
    // - subject: who this token belongs to (the username)
    // - issuedAt: when it was created
    // - expiration: when it stops being valid
    // - signature: cryptographic proof it hasn't been tampered with
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // VALIDATE: Called by the gateway on every incoming request.
    // Parses the token and verifies the signature.
    // If the token was tampered with or expired, this throws an exception
    // and the request gets rejected before reaching payment-service.
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Extracts just the username from inside a token.
    // Used to identify WHO is making the request.
    public String extractUsername(String token) {
        return validateToken(token).getSubject();
    }
}