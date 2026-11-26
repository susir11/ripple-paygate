package com.susir.paygate.auth.service;

import com.susir.paygate.auth.dto.AuthRequest;
import com.susir.paygate.auth.dto.RegisterRequest;
import com.susir.paygate.auth.entity.User;
import com.susir.paygate.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository users;
    private JwtService jwt;
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // fast for tests
    private AuthService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        jwt = mock(JwtService.class);
        when(jwt.generateToken(any())).thenReturn("TOKEN");
        service = new AuthService(users, encoder, jwt);
    }

    @Test
    void register_savesHashedPassword() {
        when(users.findByUsername("intern_1")).thenReturn(Optional.empty());

        assertEquals("TOKEN", service.register(register("intern_1", "Passw0rd!")));

        verify(users).saveAndFlush(argThat(u ->
                u.getUsername().equals("intern_1")
                        && !u.getPassword().equals("Passw0rd!")
                        && encoder.matches("Passw0rd!", u.getPassword())
                        && u.getRole().equals("ROLE_USER")));
    }

    @Test
    void register_existingUsername_throwsUsernameTaken() {
        when(users.findByUsername("intern_1")).thenReturn(Optional.of(new User()));

        assertThrows(UsernameTakenException.class, () -> service.register(register("intern_1", "Passw0rd!")));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void register_raceOnUniqueConstraint_throwsUsernameTaken() {
        // Two sign-ups with the same name at the same moment: both pass the check,
        // the database's unique constraint stops the second one.
        when(users.findByUsername("intern_1")).thenReturn(Optional.empty());
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(UsernameTakenException.class, () -> service.register(register("intern_1", "Passw0rd!")));
    }

    @Test
    void login_unknownUserOrWrongPassword_throwsInvalidCredentials() {
        User user = new User(1L, "intern_1", encoder.encode("Passw0rd!"), "ROLE_USER");
        when(users.findByUsername("intern_1")).thenReturn(Optional.of(user));
        when(users.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> service.login(login("nobody", "Passw0rd!")));
        assertThrows(InvalidCredentialsException.class, () -> service.login(login("intern_1", "wrong-one")));
        assertEquals("TOKEN", service.login(login("intern_1", "Passw0rd!")));
    }

    private static RegisterRequest register(String username, String password) {
        RegisterRequest r = new RegisterRequest();
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }

    private static AuthRequest login(String username, String password) {
        AuthRequest r = new AuthRequest();
        r.setUsername(username);
        r.setPassword(password);
        return r;
    }
}
