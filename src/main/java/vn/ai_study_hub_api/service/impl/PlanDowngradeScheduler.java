package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.UserService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Proactively downgrades users whose premium plan has expired, regardless of
 * whether they log back in. The lazy downgrade in {@code CustomUserDetailsService}
 * still covers the in-between window (user whose plan lapses between two runs);
 * this job guarantees no expired user keeps premium privileges indefinitely.
 *
 * <p>Naturally idempotent: after downgrade {@code planId} becomes 1, so the
 * {@code planId != 1} query filter excludes them on subsequent runs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlanDowngradeScheduler {

    private final UserRepository userRepository;
    private final UserService userService;

    private static final Integer FREE_PLAN_ID = 1;

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void downgradeExpiredPlans() {
        log.info("Running plan downgrade check...");

        List<UserEntity> expiredUsers = userRepository
                .findByPlanExpiresAtBeforeAndPlanIdNot(LocalDateTime.now(), FREE_PLAN_ID);

        if (expiredUsers.isEmpty()) {
            log.info("No expired premium users to downgrade.");
            return;
        }

        log.info("Found {} expired premium user(s) to downgrade.", expiredUsers.size());

        int downgraded = 0;
        for (UserEntity user : expiredUsers) {
            if (userService.downgradeToFreePlan(user)) {
                downgraded++;
            }
        }

        log.info("Plan downgrade check completed. Downgraded {} user(s) to free plan.", downgraded);
    }
}
