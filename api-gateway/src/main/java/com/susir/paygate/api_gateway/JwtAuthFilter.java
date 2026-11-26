package com.susir.paygate.api_gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

// GlobalFilter means this runs on EVERY request that hits the gateway —
// before it gets routed anywhere. Think of it as the security checkpoint
// at the entrance of a building. Nobody gets past without being checked.
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    // Header that tells downstream services WHO made the request.
    // Only this filter may set it — see stripUserHeader below.
    static final String USER_HEADER = "X-Authenticated-User";

    // Same secret key as auth-service — must match exactly.
    // Auth-service SIGNS tokens with this key.
    // Gateway VERIFIES tokens with this same key.
    private final SecretKey key;

    public JwtAuthFilter(@Value("${jwt.secret:}") String secret) {
        // Refuse to start with a missing or weak secret, instead of rejecting
        // every request later with a confusing 401.
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be set and at least 32 characters long (HMAC-SHA256 needs a 256-bit key)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // Never trust a user header sent by the client — otherwise anyone could
        // claim to be someone else. Remove it first, always; we add our own below
        // only after the token checks out.
        ServerWebExchange cleaned = withRequest(exchange, stripUserHeader(exchange.getRequest()));

        String path = cleaned.getRequest().getURI().getPath();

        // Public paths need no token — see isPublicPath below.
        if (isPublicPath(path)) {
            return chain.filter(cleaned);
        }

        // For all other requests (like /api/payments/esewa/initiate),
        // check for the Authorization header.
        // Standard format: "Authorization: Bearer eyJhbGci..."
        String authHeader = cleaned.getRequest().getHeaders().getFirst("Authorization");

        // If there's no Authorization header at all — reject immediately.
        // 401 Unauthorized: "I don't know who you are."
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return reject(cleaned);
        }

        // Strip "Bearer " prefix to get the raw token string
        String token = authHeader.substring(7);

        Claims claims;
        try {
            // Verify the token signature using our secret key.
            // If the token was tampered with, expired, or signed with
            // a different key — this throws an exception and we reject.
            claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            // 401 Unauthorized: "Your credentials are not valid."
            return reject(cleaned);
        }

        // Token is valid — tell downstream services who is making the request.
        // Requests can't be changed in place: mutate() builds a NEW request, and
        // we must pass that new one on, or the header is silently lost.
        ServerHttpRequest withUser = cleaned.getRequest().mutate()
                .header(USER_HEADER, claims.getSubject())
                .build();
        return chain.filter(withRequest(cleaned, withUser));
    }

    // Paths that skip the JWT check:
    //  - /auth/**  — this is how users GET a token in the first place.
    //  - eSewa's success/failure redirects — the user's browser comes back
    //    from eSewa's site without our token. payment-service doesn't trust
    //    these blindly: it checks eSewa's signature and asks eSewa directly.
    // Matched exactly, so e.g. /api/payments/esewa/initiate still needs a token.
    static boolean isPublicPath(String path) {
        return path.startsWith("/auth/")
                || path.equals("/api/payments/esewa/success")
                || path.equals("/api/payments/esewa/failure");
    }

    private static ServerHttpRequest stripUserHeader(ServerHttpRequest request) {
        if (!request.getHeaders().containsHeader(USER_HEADER)) {
            return request;
        }
        return request.mutate().headers(h -> h.remove(USER_HEADER)).build();
    }

    private static ServerWebExchange withRequest(ServerWebExchange exchange, ServerHttpRequest request) {
        return request == exchange.getRequest() ? exchange : exchange.mutate().request(request).build();
    }

    private static Mono<Void> reject(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }

    // Priority order — lower number = runs first.
    // We want JWT check to run before anything else.
    @Override
    public int getOrder() {
        return -1;
    }
}
