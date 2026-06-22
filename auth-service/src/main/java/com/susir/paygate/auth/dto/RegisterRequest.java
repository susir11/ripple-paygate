package com.susir.paygate.auth.dto;

import lombok.Getter;
import lombok.Setter;

// This is the shape of data we expect when someone calls POST /auth/register.
// The request body must contain a username and password — nothing else needed.
@Getter @Setter
public class RegisterRequest {
    private String username;
    private String password;
}