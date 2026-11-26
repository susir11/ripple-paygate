package com.susir.paygate.auth.controller;

import com.susir.paygate.auth.service.AuthService;
import com.susir.paygate.auth.service.InvalidCredentialsException;
import com.susir.paygate.auth.service.UsernameTakenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void register_ok_returnsToken() throws Exception {
        when(authService.register(any())).thenReturn("TOKEN");

        register("{\"username\":\"intern_1\",\"password\":\"Passw0rd!\"}")
                .andExpect(status().isOk())
                .andExpect(content().string("TOKEN"));
    }

    @Test
    void register_usernameTaken_is409WithMessage() throws Exception {
        when(authService.register(any())).thenThrow(new UsernameTakenException());

        register("{\"username\":\"intern_1\",\"password\":\"Passw0rd!\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USERNAME_TAKEN"))
                .andExpect(jsonPath("$.message").value("That username is already taken."));
    }

    @Test
    void register_invalidInput_is400WithFieldMessages() throws Exception {
        String[] bad = {
                "{\"username\":\"ab\",\"password\":\"Passw0rd!\"}",          // too short
                "{\"username\":\"has space\",\"password\":\"Passw0rd!\"}",   // bad characters
                "{\"username\":\"intern_1\",\"password\":\"short\"}",        // password too short
                "{\"username\":\"intern_1\"}",                               // password missing
                "{}"
        };
        for (String body : bad) {
            register(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
        verify(authService, never()).register(any());
    }

    @Test
    void register_malformedJson_is400() throws Exception {
        register("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    @Test
    void login_badCredentials_is401WithSameMessageEitherWay() throws Exception {
        // Same answer for "no such user" and "wrong password", so an attacker
        // can't use login to find out which usernames exist.
        when(authService.login(any())).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody\",\"password\":\"whatever1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void login_missingFields_is400() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
        verify(authService, never()).login(any());
    }

    private org.springframework.test.web.servlet.ResultActions register(String body) throws Exception {
        return mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
