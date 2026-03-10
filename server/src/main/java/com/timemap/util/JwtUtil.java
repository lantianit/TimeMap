package com.timemap.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private static final String ADMIN_JWT_SECRET_SUFFIX = "-admin-web-console";

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private SecretKey getAdminSigningKey() {
        String adminSecret = secret + ADMIN_JWT_SECRET_SUFFIX;
        // Ensure at least 32 bytes for HMAC-SHA256
        byte[] bytes = adminSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            return Keys.hmacShaKeyFor(padded);
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(Long userId, String openid) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("openid", openid)
                .claim("type", "miniprogram")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateAdminToken(Long adminAccountId, String role, boolean rememberMe) {
        Date now = new Date();
        long exp = rememberMe ? 7 * 24 * 3600 * 1000L : 8 * 3600 * 1000L;
        Date expiryDate = new Date(now.getTime() + exp);

        return Jwts.builder()
                .subject(String.valueOf(adminAccountId))
                .claim("role", role)
                .claim("type", "admin_web")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getAdminSigningKey())
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims parseAdminToken(String token) {
        return Jwts.parser()
                .verifyWith(getAdminSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public Long getAdminIdFromToken(String token) {
        Claims claims = parseAdminToken(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getAdminRoleFromToken(String token) {
        Claims claims = parseAdminToken(token);
        return claims.get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isAdminTokenValid(String token) {
        try {
            Claims claims = parseAdminToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
