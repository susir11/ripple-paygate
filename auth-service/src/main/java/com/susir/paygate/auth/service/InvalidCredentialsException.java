package com.susir.paygate.auth.service;

/**
 * Login failed. Used for BOTH "no such user" and "wrong password" on purpose:
 * different answers would let an attacker find out which usernames exist.
 * Becomes HTTP 401.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid username or password.");
    }
}
