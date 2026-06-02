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
            throw new AppException(HttpStatus.BAD_REQUEST, "Email đã tồn tại!");
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

        // Log kiểm tra của ông giữ nguyên
        System.out.println("DEBUG >> Email nhận: " + email);
        System.out.println("DEBUG >> OTP nhận: " + otp);
        System.out.println("DEBUG >> Key đang tìm: otp:" + email);
        System.out.println("DEBUG >> Giá trị Redis trả về: " + storedOtp);

        if (storedOtp == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "OTP không tồn tại! (Kiểm tra lại xem đã đăng ký chưa hoặc OTP đã hết hạn)");
        }
        if (!storedOtp.equals(otp)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "OTP không khớp! ");
        }


        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng với email này!"));


        user.setStatus(UserStatus.active);


        userRepository.save(user);


        redisTokenService.deleteOtp(email);

    }

    @Override
    public void resendOtp(String email) {
        if (!userRepository.existsByEmail(email)) throw new AppException(HttpStatus.NOT_FOUND, "User không tồn tại!");
        String otp = String.format("%06d", new java.util.Random().nextInt(999999));
        redisTokenService.saveOtp(email, otp, 300);
        emailService.sendOtpEmail(email, otp);
    }
}