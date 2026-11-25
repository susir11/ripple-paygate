package com.susir.paygate.api_gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthFilterTest {

    @Test
    void isPublicPath() {
        assertTrue(JwtAuthFilter.isPublicPath("/auth/login"));
        assertTrue(JwtAuthFilter.isPublicPath("/auth/register"));
        assertTrue(JwtAuthFilter.isPublicPath("/api/payments/esewa/success"));
        assertTrue(JwtAuthFilter.isPublicPath("/api/payments/esewa/failure"));

        assertFalse(JwtAuthFilter.isPublicPath("/api/payments/esewa/initiate"));
        assertFalse(JwtAuthFilter.isPublicPath("/api/payments/process"));
        assertFalse(JwtAuthFilter.isPublicPath("/api/payments/esewa/success/extra"));
        assertFalse(JwtAuthFilter.isPublicPath("/api/payments/esewa/successful"));
    }
}
