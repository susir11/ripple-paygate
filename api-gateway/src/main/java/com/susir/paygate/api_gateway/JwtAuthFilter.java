package com.susir.paygate.api_gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;

// GlobalFilter means this runs on EVERY request that hits the gateway —
// before it gets routed anywhere. Think of it as the security checkpoint
// at the entrance of a building. Nobody gets past without being checked.
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    // Same secret key as auth-service — must match exactly.
    // Auth-service SIGNS tokens with this key.
    // Gateway VERIFIES tokens with this same key.
    @Value("${jwt.secret}")
    private String secretKey;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getURI().getPath();

        // /auth/** endpoints are PUBLIC — no token needed.
        // These are how users GET a token in the first place,
        // so we can't require a token to access them.
        if (path.startsWith("/auth/")) {
            return chain.filter(exchange);
        }

        // For all other requests (like /api/payments/process),
        // check for the Authorization header.
        // Standard format: "Authorization: Bearer eyJhbGci..."
        String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst("Authorization");

        // If there's no Authorization header at all — reject immediately.
        // 401 Unauthorized: "I don't know who you are."
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Strip "Bearer " prefix to get the raw token string
        String token = authHeader.substring(7);

        try {
            // Verify the token signature using our secret key.
            // If the token was tampered with, expired, or signed with
            // a different key — this throws an exception and we reject.
            SecretKey key = Keys.hmacShaKeyFor(secretKey.getBytes());
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Token is valid — add the username as a header so
            // downstream services know who is making the request.
            // payment-service can read this header if it ever needs
            // to know which user initiated the payment.
            String username = claims.getSubject();
            exchange.getRequest().mutate()
                    .header("X-Authenticated-User", username)
                    .build();

            // Pass the request through to payment-service
            return chain.filter(exchange);

        } catch (Exception e) {
            // Token was invalid, expired, or tampered with — reject.
            // 401 Unauthorized: "Your credentials are not valid."
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    // Priority order — lower number = runs first.
    // We want JWT check to run before anything else.
    @Override
    public int getOrder() {
        return -1;
    }
}