package com.property.repair.service;

import com.property.repair.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenProvider.
 * Uses reflection to set @Value fields, then calls init() manually.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() throws Exception {
        jwtTokenProvider = new JwtTokenProvider();

        // Set secretString via reflection
        Field secretField = JwtTokenProvider.class.getDeclaredField("secretString");
        secretField.setAccessible(true);
        secretField.set(jwtTokenProvider, "testSecretKeyForPropertyRepairSystemThatIsLongEnoughForHmacSha256");

        // Set accessTokenExpiration via reflection (1 hour)
        Field accessExpField = JwtTokenProvider.class.getDeclaredField("accessTokenExpiration");
        accessExpField.setAccessible(true);
        accessExpField.set(jwtTokenProvider, 3600000L);

        // Set refreshTokenExpiration via reflection (7 days)
        Field refreshExpField = JwtTokenProvider.class.getDeclaredField("refreshTokenExpiration");
        refreshExpField.setAccessible(true);
        refreshExpField.set(jwtTokenProvider, 604800000L);

        // Call @PostConstruct init() to initialize the SecretKey
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("1. Generate and validate access token")
    void testGenerateAndValidateAccessToken() {
        String token = jwtTokenProvider.generateAccessToken(1L, "admin", "ADMIN");

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    @DisplayName("2. Generate and validate refresh token")
    void testGenerateAndValidateRefreshToken() {
        String token = jwtTokenProvider.generateRefreshToken(1L, "admin");

        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    @DisplayName("3. getUserIdFromToken returns correct userId")
    void testGetUserIdFromToken() {
        String token = jwtTokenProvider.generateAccessToken(42L, "testuser", "WORKER");

        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        assertEquals(42L, userId);
    }

    @Test
    @DisplayName("4. getRoleFromToken returns correct role")
    void testGetRoleFromToken() {
        String token = jwtTokenProvider.generateAccessToken(1L, "admin", "ADMIN");

        String role = jwtTokenProvider.getRoleFromToken(token);
        assertEquals("ADMIN", role);
    }

    @Test
    @DisplayName("4b. getUsernameFromToken returns correct username")
    void testGetUsernameFromToken() {
        String token = jwtTokenProvider.generateAccessToken(1L, "admin", "ADMIN");

        String username = jwtTokenProvider.getUsernameFromToken(token);
        assertEquals("admin", username);
    }

    @Test
    @DisplayName("5. Expired token returns false on validation")
    void testExpiredToken() throws Exception {
        // Set a very short expiration (1 millisecond)
        Field accessExpField = JwtTokenProvider.class.getDeclaredField("accessTokenExpiration");
        accessExpField.setAccessible(true);
        accessExpField.set(jwtTokenProvider, 1L);

        String token = jwtTokenProvider.generateAccessToken(1L, "admin", "ADMIN");

        // Wait a bit to ensure the token expires
        Thread.sleep(50);

        assertFalse(jwtTokenProvider.validateToken(token));

        // Restore normal expiration for other tests
        accessExpField.set(jwtTokenProvider, 3600000L);
    }

    @Test
    @DisplayName("6. Invalid token returns false on validation")
    void testInvalidToken() {
        assertFalse(jwtTokenProvider.validateToken("completely.invalid.token"));
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken("eyJhbGciOiJIUzI1NiJ9.invalid.signature"));
    }

    @Test
    @DisplayName("7. getExpirationFromToken returns future timestamp")
    void testGetExpirationFromToken() {
        String token = jwtTokenProvider.generateAccessToken(1L, "admin", "ADMIN");

        long expiration = jwtTokenProvider.getExpirationFromToken(token);
        assertTrue(expiration > System.currentTimeMillis());
    }

    @Test
    @DisplayName("8. Refresh token has no role claim")
    void testRefreshTokenHasNoRole() {
        String token = jwtTokenProvider.generateRefreshToken(1L, "admin");

        // Refresh tokens do not contain a role claim
        String role = jwtTokenProvider.getRoleFromToken(token);
        assertNull(role);
    }
}
