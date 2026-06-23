package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.StoragePlanEntity;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.StoragePlanRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.AiQuotaService;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiQuotaServiceImpl implements AiQuotaService {

    private static final String KEY_PREFIX = "user:ai_limit:";
    private static final Duration TTL = Duration.ofSeconds(86400); // 24h (FR F-MON-03.3)

    private final StringRedisTemplate redis;
    private final UserRepository userRepository;
    private final StoragePlanRepository storagePlanRepository;

    @Override
    public QuotaInfo checkAndIncrement(UUID userId) {
        int limit = resolveDailyLimit(userId);
        String key = key(userId);

        String cur = redis.opsForValue().get(key);
        long current = (cur == null) ? 0L : parseLong(cur);

        if (current >= limit) {
            // AC F-AI-01 scenario 2: block without calling the LLM and WITHOUT incrementing.
            log.info("User {} reached daily AI limit ({}). Blocking request.", userId, limit);
            throw new AppException(HttpStatus.TOO_MANY_REQUESTS,
                    "Daily AI request limit reached. Please upgrade to Premium for more request chat");
        }

        Long after = redis.opsForValue().increment(key);
        int count = (after == null) ? (int) (current + 1) : after.intValue();

        if (after != null && after == 1L) {
            // First request of the day: set the 24h TTL.
            redis.expire(key, TTL);
        }

        int remaining = Math.max(0, limit - count);
        return new QuotaInfo(count, limit, remaining);
    }

    @Override
    public QuotaInfo getUsage(UUID userId) {
        int limit = resolveDailyLimit(userId);
        String cur = redis.opsForValue().get(key(userId));
        int count = (cur == null) ? 0 : (int) parseLong(cur);
        int remaining = Math.max(0, limit - count);
        return new QuotaInfo(count, limit, remaining);
    }

    private int resolveDailyLimit(UUID userId) {
        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            throw new AppException(HttpStatus.NOT_FOUND, "User not found with ID: " + userId);
        }
        Integer rawPlanId = userOpt.get().getPlanId();
        final Integer planId = (rawPlanId == null) ? 1 : rawPlanId; // default Free plan
        return storagePlanRepository.findById(planId)
                .map(StoragePlanEntity::getMaxAiRequestsPerDay)
                .orElseThrow(() -> new AppException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Storage plan not found with ID: " + planId));
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId + ":" + LocalDate.now();
    }

    private long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            log.warn("Non-numeric AI quota counter value '{}', treating as 0", s);
            return 0L;
        }
    }
}
