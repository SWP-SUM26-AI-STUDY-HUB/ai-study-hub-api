package vn.ai_study_hub_api.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.ai_study_hub_api.model.UserRole;
import vn.ai_study_hub_api.model.UserStatus;
import vn.ai_study_hub_api.service.*;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.controller.request.*;
import vn.ai_study_hub_api.controller.response.LoginResponse;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RedisTokenService redisTokenService;
    private final EmailService emailService;

    @Autowired
    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           RedisTokenService redisTokenService,
                           EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redisTokenService = redisTokenService;
        this.emailService = emailService;
    }

    @Override
    public LoginResponse login(LoginRequest request) { return null; }

    @Override
    public LoginResponse refreshToken(RefreshTokenRequest request) { return null; }

    @Override
    public void logout(String authHeader) {}

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

        // Debug logs translated to English
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
}