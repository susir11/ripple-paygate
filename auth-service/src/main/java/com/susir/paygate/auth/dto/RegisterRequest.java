package com.susir.paygate.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

// This is the shape of data we expect when someone calls POST /auth/register.
// The annotations are checked automatically because the controller uses @Valid;
// a request that breaks them gets a 400 with an explanation.
@Getter @Setter
public class RegisterRequest {

    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "[A-Za-z0-9_.-]+",
            message = "may only contain letters, digits, '_', '.' and '-'")
    private String username;

    // BCrypt only uses the first 72 bytes of a password, so longer ones are refused.
    @NotBlank
    @Size(min = 8, max = 72)
    private String password;
}
