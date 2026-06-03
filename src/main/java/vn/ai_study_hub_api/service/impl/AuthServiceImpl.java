package vn.ai_study_hub_api.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.ai_study_hub_api.controller.request.*;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserRole;
import vn.ai_study_hub_api.model.UserStatus;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.AuthService;
import vn.ai_study_hub_api.controller.response.LoginResponse;
import vn.ai_study_hub_api.security.JwtTokenProvider;
import vn.ai_study_hub_api.service.RedisTokenService;
import vn.ai_study_hub_api.service.EmailService;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.security.CustomUserDetails;
import java.util.UUID;


@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RedisTokenService redisTokenService;
    private final EmailService emailService;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtTokenProvider tokenProvider,
                           RedisTokenService redisTokenService,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.redisTokenService = redisTokenService;
        this.emailService = emailService;
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

        // Account status checks
        if (UserStatus.banned == user.getStatus() || "banned".equalsIgnoreCase(user.getStatus().name())) {
            throw new AppException(HttpStatus.FORBIDDEN, "Your account has been banned.");
        }
        if (UserStatus.inactive == user.getStatus() || "inactive".equalsIgnoreCase(user.getStatus().name())) {
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

        if (UserStatus.banned == user.getStatus() || "banned".equalsIgnoreCase(user.getStatus().name())) {
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

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Email already exists!");
        }

        UserEntity user = UserEntity.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .status(UserStatus.inactive)
                .role(UserRole.user)
                .planId(1)
                .build();
        userRepository.save(user);

        String otp = String.format("%06d", new java.util.Random().nextInt(999999));
        redisTokenService.saveOtp(request.getEmail(), otp, 300);
        emailService.sendOtpEmail(request.getEmail(), otp);
    }

    @Override
    @Transactional
    public void verifyAccount(String email, String otp) {
        String storedOtp = redisTokenService.getOtp(email);

        // Debug logs
        System.out.println("DEBUG >> Received Email: " + email);
        System.out.println("DEBUG >> Received OTP: " + otp);
        System.out.println("DEBUG >> Searching Key: otp:" + email);
        System.out.println("DEBUG >> Redis Return Value: " + storedOtp);

        if (storedOtp == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "OTP does not exist! (Please check if you have registered or if the OTP has expired)");
        }
        if (!storedOtp.equals(otp)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid OTP! The code does not match.");
        }

        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found with this email!"));

        user.setStatus(UserStatus.active);
        userRepository.save(user);
        redisTokenService.deleteOtp(email);
    }

    @Override
    public void resendOtp(String email) {
        if (!userRepository.existsByEmail(email)) {
            throw new AppException(HttpStatus.NOT_FOUND, "User does not exist!");
        }
        String otp = String.format("%06d", new java.util.Random().nextInt(999999));
        redisTokenService.saveOtp(email, otp, 300);
        emailService.sendOtpEmail(email, otp);
    }
    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        UserEntity user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Email address not found!"));

        String resetToken = java.util.UUID.randomUUID().toString();
        redisTokenService.saveOtp("reset:" + resetToken, user.getEmail(), 900);
        emailService.sendResetPasswordEmail(user.getEmail(), resetToken);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {

        String email = redisTokenService.getOtp("reset:" + request.getToken());
        if (email == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid or expired reset token!");
        }


        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found!"));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        redisTokenService.deleteOtp("reset:" + request.getToken());
    }
}