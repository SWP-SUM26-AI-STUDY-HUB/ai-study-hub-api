package vn.ai_study_hub_api.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
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

import java.util.Map;
import java.util.UUID;

/**
 * Service implementation managing user login, token refresh, logout routines, and Google OAuth2.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RedisTokenService redisTokenService;

    // Khởi tạo hằng số RestClient an toàn
    private final RestClient restClient = RestClient.create();

    // Inject các cấu hình từ application.yml
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String googleRedirectUri;

    @Value("${spring.security.oauth2.client.provider.google.user-info-uri}")
    private String googleUserInfoUri;

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
    public String generateAuthUrl(String loginType) {
        if (!"google".equalsIgnoreCase(loginType)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Unsupported social login provider: " + loginType);
        }

        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", googleClientId)
                .queryParam("redirect_uri", googleRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "email profile")
                .queryParam("prompt", "select_account")
                .build()
                .toUriString();
    }

    @Override
    @Transactional
    public LoginResponse processGoogleLogin(String code) {
        // 1. Đổi "code" lấy Google Access Token
        String googleAccessToken = exchangeCodeForGoogleToken(code);

        // 2. Sử dụng Google Access Token để lấy thông tin Email, Name từ Google
        Map<String, Object> userInfo = fetchGoogleUserInfo(googleAccessToken);

        String email = (String) userInfo.get("email");
        String fullName = (String) userInfo.get("name");

        if (!StringUtils.hasText(email)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Failed to retrieve email from Google profile.");
        }

        // 3. Tìm tài khoản trong Database hoặc tự động tạo mới
        UserEntity user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    UserEntity newUser = new UserEntity();
                    newUser.setEmail(email);
                    newUser.setFullName(fullName != null ? fullName : "Google User");
                    newUser.setRole("USER");
                    newUser.setStatus("active");
                    newUser.setPasswordHash(null);
                    return userRepository.save(newUser);
                });

        // 4. Kiểm tra trạng thái tài khoản
        if ("banned".equalsIgnoreCase(user.getStatus())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Your account has been banned.");
        }
        if ("inactive".equalsIgnoreCase(user.getStatus())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Your account is currently inactive.");
        }

        // 5. Cấp cặp JWT Token của hệ thống (Access & Refresh Token)
        CustomUserDetails userDetails = CustomUserDetails.build(user);
        String accessToken = tokenProvider.generateAccessToken(userDetails);
        String refreshToken = tokenProvider.generateRefreshToken(userDetails);

        // Lưu trữ Refresh Token vào Redis
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

    /**
     * Gửi request POST sang Google để đổi Authorization Code lấy Google Access Token
     */
    private String exchangeCodeForGoogleToken(String code) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", googleClientId);
        params.add("client_secret", googleClientSecret);
        params.add("redirect_uri", googleRedirectUri);
        params.add("grant_type", "authorization_code");

        try {
            Map<String, Object> response = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(params)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});

            if (response != null && response.containsKey("access_token")) {
                return (String) response.get("access_token");
            }
            throw new AppException(HttpStatus.BAD_REQUEST, "Google did not return an access token.");
        } catch (Exception e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to exchange code with Google: " + e.getMessage());
        }
    }

    /**
     * Gửi request GET kèm Access Token để lấy thông tin chi tiết người dùng từ Google
     */
    private Map<String, Object> fetchGoogleUserInfo(String googleAccessToken) {
        try {
            return restClient.get()
                    .uri(googleUserInfoUri)
                    .header("Authorization", "Bearer " + googleAccessToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch user info from Google: " + e.getMessage());
        }
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

        String newAccessToken = tokenProvider.generateAccessToken(userDetails);
        String newRefreshToken = tokenProvider.generateRefreshToken(userDetails);

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

                if (remainingSeconds > 0) {
                    redisTokenService.blacklistAccessToken(jwt, remainingSeconds);
                }

                redisTokenService.deleteRefreshToken(userId.toString());
            }
        }
        SecurityContextHolder.clearContext();
    }
}