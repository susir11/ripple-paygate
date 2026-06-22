package com.susir.paygate.auth.dto;

import lombok.Getter;
import lombok.Setter;

// Same shape as RegisterRequest — used for POST /auth/login.
// Keeping them as separate classes is good practice because
// they might diverge later (e.g. login might add a 2FA field).
@Getter @Setter
public class AuthRequest {
    private String username;
    private String password;
}