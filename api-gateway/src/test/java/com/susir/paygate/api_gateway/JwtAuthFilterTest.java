package com.susir.paygate.api_gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JwtAuthFilterTest {

    static final String SECRET = "test-secret-that-is-at-least-32-characters-long";

    private JwtAuthFilter filter;
    // The exchange the filter passed on to the rest of the chain (null = request was stopped)
    private final AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(SECRET);
    }

    @Test
    void isPublicPath() {
        assertTrue(JwtAuthFilter.isPublicPath("/auth/login"));
        assertTrue(JwtAuthFilter.isPublicPath("/auth/register"));
        assertTrue(JwtAuthFilter.isPublicPath("/api/payments/esewa/success"));
        assertTrue(JwtAuthFilter.isPublicPath("/api/payments/esewa/failure"));

        assertFalse(JwtAuthFilter.isPublicPath("/api/payments/esewa/initiate"));
        assertFalse(JwtAuthFilter.isPublicPath("/api/payments/mine"));
        assertFalse(JwtAuthFilter.isPublicPath("/api/payments/esewa/success/extra"));
        assertFalse(JwtAuthFilter.isPublicPath("/api/payments/esewa/successful"));
    }

    @Test
    void validToken_forwardsTheUsernameDownstream() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/payments/esewa/initiate")
                .header("Authorization", "Bearer " + token("alice", SECRET)));

        run(exchange);

        assertNotNull(forwarded.get(), "request should be passed on");
        assertEquals("alice", forwarded.get().getRequest().getHeaders().getFirst("X-Authenticated-User"));
    }

    @Test
    void clientCannotPretendToBeSomeoneElse() {
        // A client-supplied X-Authenticated-User must be replaced by the name in the token...
        MockServerWebExchange withToken = exchange(MockServerHttpRequest.post("/api/payments/esewa/initiate")
                .header("Authorization", "Bearer " + token("alice", SECRET))
                .header("X-Authenticated-User", "bob"));
        run(withToken);
        assertEquals(java.util.List.of("alice"),
                forwarded.get().getRequest().getHeaders().get("X-Authenticated-User"));

        // ...and removed entirely on public paths, where there is no token.
        forwarded.set(null);
        MockServerWebExchange publicPath = exchange(MockServerHttpRequest.get("/api/payments/esewa/success")
                .header("X-Authenticated-User", "bob"));
        run(publicPath);
        assertNull(forwarded.get().getRequest().getHeaders().getFirst("X-Authenticated-User"));
    }

    @Test
    void missingOrInvalidToken_is401AndStops() {
        String[] badHeaders = {null, "Basic abc", "Bearer not-a-jwt", "Bearer " + token("alice", SECRET + "-other")};
        for (String auth : badHeaders) {
            forwarded.set(null);
            MockServerHttpRequest.BaseBuilder<?> req = MockServerHttpRequest.post("/api/payments/esewa/initiate");
            if (auth != null) req.header("Authorization", auth);
            MockServerWebExchange exchange = exchange(req);

            run(exchange);

            assertNull(forwarded.get(), "must not forward: " + auth);
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode(), String.valueOf(auth));
        }
    }

    @Test
    void shortOrMissingSecret_refusesToStart() {
        assertThrows(IllegalStateException.class, () -> new JwtAuthFilter("too-short"));
        assertThrows(IllegalStateException.class, () -> new JwtAuthFilter(null));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void run(MockServerWebExchange exchange) {
        filter.filter(exchange, ex -> {
            forwarded.set(ex);
            return Mono.empty();
        }).block();
    }

    private static MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request.build());
    }

    private static String token(String username, String secret) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
