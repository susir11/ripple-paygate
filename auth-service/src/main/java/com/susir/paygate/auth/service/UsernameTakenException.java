package com.susir.paygate.auth.service;

/** Registration failed because someone already has that username. Becomes HTTP 409. */
public class UsernameTakenException extends RuntimeException {
    public UsernameTakenException() {
        super("That username is already taken.");
    }
}
