package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.NotificationEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.model.UserStatus;
import vn.ai_study_hub_api.model.ViolationHistoryEntity;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.repository.ViolationHistoryRepository;
import vn.ai_study_hub_api.security.JwtTokenProvider;
import vn.ai_study_hub_api.service.EmailService;
import vn.ai_study_hub_api.service.RedisTokenService;
import vn.ai_study_hub_api.service.UserSanctionService;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSanctionServiceImpl implements UserSanctionService {

    private final UserRepository userRepository;
    private final ViolationHistoryRepository violationHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final RedisTokenService redisTokenService;
    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;

    @Override
    @Transactional
    public void banUser(UUID userId) {
        long warnCount = violationHistoryRepository.countByUserIdAndStatus(userId, "WARN");
        String reason = warnCount >= 3 
                ? "Tài khoản của bạn đã bị khóa (Ban) do nhận đủ 3 lần cảnh cáo."
                : "Tài khoản của bạn đã bị khóa do vi phạm các điều khoản dịch vụ của chúng tôi.";
        banUser(userId, reason);
    }

    @Override
    @Transactional
    public void banUser(UUID userId, String reason) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        user.setStatus(UserStatus.BANNED);
        userRepository.save(user);

        // Retrieve and blacklist all active access tokens for this user
        String redisKey = "active_tokens:" + userId.toString();
        Set<String> tokens = redisTemplate.opsForSet().members(redisKey);
        if (tokens != null) {
            for (String token : tokens) {
                long remaining = tokenProvider.getRemainingSeconds(token);
                if (remaining > 0) {
                    redisTokenService.blacklistAccessToken(token, remaining);
                }
            }
        }
        redisTemplate.delete(redisKey);

        // Revoke refresh token
        redisTokenService.deleteRefreshToken(userId.toString());

        // Log violation history
        ViolationHistoryEntity violation = ViolationHistoryEntity.builder()
                .user(user)
                .reason(reason)
                .status("BANNED")
                .build();
        violationHistoryRepository.save(violation);

        // Send ban notification
        NotificationEntity notification = NotificationEntity.builder()
                .user(user)
                .title("Tài khoản bị khóa (Banned)")
                .content(reason)
                .isRead(false)
                .build();
        notificationRepository.save(notification);
        
        // Send ban email
        emailService.sendBanEmail(user.getEmail(), reason);
        
        log.info("Successfully banned user: {} with reason: {}", userId, reason);
    }

    @Override
    @Transactional
    public void warnUser(UUID userId, String reason) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        // Log violation history
        ViolationHistoryEntity violation = ViolationHistoryEntity.builder()
                .user(user)
                .reason(reason)
                .status("WARN")
                .build();
        violationHistoryRepository.save(violation);

        long warnCount = violationHistoryRepository.countByUserIdAndStatus(userId, "WARN");

        // Send warning notification with count
        NotificationEntity notification = NotificationEntity.builder()
                .user(user)
                .title("Cảnh báo tài khoản (Warn)")
                .content(String.format("Bạn đã bị cảnh báo %d lần. Lý do: %s", warnCount, reason))
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        log.info("Successfully warned user: {} with reason: {}. Total warnings: {}", userId, reason, warnCount);

        // Check if warning count is >= 3
        if (warnCount >= 3) {
            log.info("User {} reached {} warnings. Automatically banning...", userId, warnCount);
            banUser(userId);
        }
    }

    @Override
    public void trackUserToken(UUID userId, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        String redisKey = "active_tokens:" + userId.toString();
        redisTemplate.opsForSet().add(redisKey, token);
        redisTemplate.expire(redisKey, 24, TimeUnit.HOURS);
        log.debug("Tracked token for user: {}", userId);
    }

    @Override
    @Transactional
    public void reactivateUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new AppException(HttpStatus.BAD_REQUEST, "User account is already active");
        }

        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        // Log reactivation in violation histories
        ViolationHistoryEntity violation = ViolationHistoryEntity.builder()
                .user(user)
                .reason("Account reactivated by administrator")
                .status("ACTIVE")
                .build();
        violationHistoryRepository.save(violation);

        // Send reactivation notification
        NotificationEntity notification = NotificationEntity.builder()
                .user(user)
                .title("Tài khoản được mở khóa (Reactivated)")
                .content("Tài khoản của bạn đã được quản trị viên mở khóa và kích hoạt lại.")
                .isRead(false)
                .build();
        notificationRepository.save(notification);

        log.info("Successfully reactivated user: {}", userId);
    }
}
