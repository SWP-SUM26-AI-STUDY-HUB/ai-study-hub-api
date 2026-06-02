package vn.ai_study_hub_api.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.AuthService;
import vn.ai_study_hub_api.controller.request.LoginRequest;
import vn.ai_study_hub_api.controller.request.RefreshTokenRequest;
import vn.ai_study_hub_api.controller.response.LoginResponse;
import vn.ai_study_hub_api.security.JwtTokenProvider;
import vn.ai_study_hub_api.service.RedisTokenService;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.security.CustomUserDetails;
import java.util.UUID;

/**
 * Service implementation managing user login, token refresh, and logout routines.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RedisTokenService redisTokenService;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider,
                           RedisTokenService redisTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.redisTokenService = redisTokenService;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));

        if (user.getPasswordHash() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "This account does not have a local password set. Try OAuth login.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        if ("banned".equalsIgnoreCase(user.getStatus())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Your account has been banned.");
        }
        if ("inactive".equalsIgnoreCase(user.getStatus())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Your account is currently inactive.");
        }

        CustomUserDetails userDetails = CustomUserDetails.build(user);

        String accessToken = tokenProvider.generateAccessToken(userDetails);
        String refreshToken = tokenProvider.generateRefreshToken(userDetails);

        // Store refresh token in Redis with 7 days TTL (converted from MS to seconds)
        long ttlSeconds = tokenProvider.getJwtRefreshExpirationMs() / 1000;
        redisTokenService.saveRefreshToken(user.getId().toString(), refreshToken, ttlSeconds);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String oldRefreshToken = request.getRefreshToken();

        if (!tokenProvider.validateToken(oldRefreshToken)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token.");
        }

        UUID userId = tokenProvider.getUserIdFromJwt(oldRefreshToken);
        String storedToken = redisTokenService.getRefreshToken(userId.toString());

        if (storedToken == null || !storedToken.equals(oldRefreshToken)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token is invalid or has been revoked.");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User profile not found."));

        if ("banned".equalsIgnoreCase(user.getStatus())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Your account has been banned.");
        }

        CustomUserDetails userDetails = CustomUserDetails.build(user);

        // Rotate tokens
        String newAccessToken = tokenProvider.generateAccessToken(userDetails);
        String newRefreshToken = tokenProvider.generateRefreshToken(userDetails);

        // Save new refresh token and expire the old one
        long ttlSeconds = tokenProvider.getJwtRefreshExpirationMs() / 1000;
        redisTokenService.saveRefreshToken(user.getId().toString(), newRefreshToken, ttlSeconds);

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .build();
    }

    @Override
    public void logout(String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);

            if (tokenProvider.validateToken(jwt)) {
                UUID userId = tokenProvider.getUserIdFromJwt(jwt);
                long remainingSeconds = tokenProvider.getRemainingSeconds(jwt);

                // 1. Blacklist the access token in Redis
                if (remainingSeconds > 0) {
                    redisTokenService.blacklistAccessToken(jwt, remainingSeconds);
                }

                // 2. Remove refresh token from Redis
                redisTokenService.deleteRefreshToken(userId.toString());
            }
        }
        SecurityContextHolder.clearContext();
    }
}
