package vn.ai_study_hub_api.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.Decoders;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;

/**
 * Utility component for generating, parsing, and validating JSON Web Tokens (JWT).
 * Built using modern JJWT 0.12.x API.
 */
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-expiration-ms}")
    private long jwtAccessExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long jwtRefreshExpirationMs;

    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        try {
            byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
            this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        }
    }

    /**
     * Generate an Access Token for an authenticated user.
     * @param authentication Authentication principal details
     * @return Generated JWT string
     */
    public String generateAccessToken(Authentication authentication) {
        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();
        return generateAccessToken(userPrincipal);
    }

    /**
     * Generate an Access Token for a CustomUserDetails instance.
     * @param userPrincipal User detail principal
     * @return Generated JWT string
     */
    public String generateAccessToken(CustomUserDetails userPrincipal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtAccessExpirationMs);

        String role = userPrincipal.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");

        return Jwts.builder()
                .subject(userPrincipal.getEmail())
                .claim("userId", userPrincipal.getId().toString())
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Generate a Refresh Token for a CustomUserDetails instance.
     * @param userPrincipal User detail principal
     * @return Generated JWT string
     */
    public String generateRefreshToken(CustomUserDetails userPrincipal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtRefreshExpirationMs);

        return Jwts.builder()
                .subject(userPrincipal.getEmail())
                .claim("userId", userPrincipal.getId().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Extract username (email) from JWT token.
     * @param token JWT token
     * @return User email string
     */
    public String getEmailFromJwt(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    /**
     * Extract user UUID from JWT token.
     * @param token JWT token
     * @return User UUID
     */
    public UUID getUserIdFromJwt(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.get("userId", String.class));
    }

    /**
     * Get remaining lifetime of the token in seconds.
     * Used for blacklisting Access Tokens with precise TTL.
     * @param token Access token
     * @return Remaining lifetime in seconds
     */
    public long getRemainingSeconds(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Date expiration = claims.getExpiration();
            long diff = expiration.getTime() - System.currentTimeMillis();
            return diff > 0 ? diff / 1000 : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Validate the JWT token's signature, format, and expiration.
     * @param authToken Auth token string
     * @return true if valid, false otherwise
     */
    public boolean validateToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public long getJwtRefreshExpirationMs() {
        return jwtRefreshExpirationMs;
    }
}
