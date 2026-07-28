package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.model.NotificationEntity;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.NotificationRepository;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlanExpirationScheduler {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final StoragePlanRepository storagePlanRepository;

    private static final String EXPIRATION_NOTIFICATION_TITLE = "Storage Plan Expiring Soon";

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void checkExpiringPlans() {
        log.info("Running plan expiration check...");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threeDaysLater = now.plusDays(3);

        List<UserEntity> expiringUsers = userRepository.findByPlanExpiresAtBetweenAndPlanIdIsNotNull(now, threeDaysLater);

        if (expiringUsers.isEmpty()) {
            log.info("No users with expiring plans found.");
            return;
        }

        log.info("Found {} users with plans expiring within 3 days.", expiringUsers.size());

        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();

        int notificationCount = 0;
        for (UserEntity user : expiringUsers) {
            boolean alreadyNotified = notificationRepository
                    .existsByUserIdAndTitleAndCreatedAtAfter(user.getId(), EXPIRATION_NOTIFICATION_TITLE, todayStart);

            if (alreadyNotified) {
                log.debug("User {} already notified today, skipping.", user.getId());
                continue;
            }

            String planName = resolvePlanName(user.getPlanId());
            String content = String.format(
                    "Your %s plan will expire on %s. Please renew to avoid your documents being locked.",
                    planName,
                    user.getPlanExpiresAt().toLocalDate()
            );

            NotificationEntity notification = NotificationEntity.builder()
                    .user(user)
                    .title(EXPIRATION_NOTIFICATION_TITLE)
                    .content(content)
                    .type("PLAN_EXPIRING")
                    .targetId(user.getPlanId() != null ? user.getPlanId().toString() : "Premium")
                    .isRead(false)
                    .build();
            notificationRepository.save(notification);
            notificationCount++;
        }

        log.info("Plan expiration check completed. Sent {} notifications.", notificationCount);
    }

    private String resolvePlanName(Integer planId) {
        if (planId == null) {
            return "Premium";
        }
        return storagePlanRepository.findById(planId)
                .map(StoragePlanEntity::getName)
                .orElse("Premium");
    }
}
