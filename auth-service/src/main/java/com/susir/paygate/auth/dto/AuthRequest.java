package com.susir.paygate.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

// Same shape as RegisterRequest — used for POST /auth/login.
// Keeping them as separate classes is good practice because
// they might diverge later (e.g. login might add a 2FA field).
// Login only requires both fields to be present: the stricter rules apply
// when an account is created, and accounts made before them must still work.
@Getter @Setter
public class AuthRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
