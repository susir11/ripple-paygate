package com.susir.paygate.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

// This class tells Spring Security what to protect and what to leave open.
// Without this, Spring Security locks down EVERYTHING by default —
// including /auth/login and /auth/register, which would make it impossible
// to log in at all. So we explicitly say: these two endpoints are public,
// everything else requires authentication.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF (Cross Site Request Forgery) protection.
                // CSRF is needed for browser-based session apps, but we're using
                // JWT tokens instead of sessions, so CSRF doesn't apply here.
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        // These two endpoints must be PUBLIC — no token required.
                        // This is the only way a user can GET a token in the first place.
                        .requestMatchers("/auth/register", "/auth/login").permitAll()
                        // Every other request to auth-service requires authentication.
                        .anyRequest().authenticated()
                )

                // STATELESS means: don't create or use HTTP sessions.
                // Every request must carry its own JWT token — the server
                // remembers nothing between requests. This is the standard
                // approach for microservices and REST APIs.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );

        return http.build();
    }

    // BCrypt is the industry standard password hashing algorithm.
    // It's intentionally slow to make brute force attacks impractical.
    // This bean is used in AuthService to encode and verify passwords.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}