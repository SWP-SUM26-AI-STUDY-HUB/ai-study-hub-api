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


    public String generateAccessToken(Authentication authentication) {
        CustomUserDetails userPrincipal = (CustomUserDetails) authentication.getPrincipal();
        return generateAccessToken(userPrincipal);
    }


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


    public String getEmailFromJwt(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }


    public UUID getUserIdFromJwt(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.get("userId", String.class));
    }


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
