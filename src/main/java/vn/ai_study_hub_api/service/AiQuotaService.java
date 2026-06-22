package vn.ai_study_hub_api.service;

import java.util.UUID;

/**
 * AI Guard: enforces the per-user daily chat quota (F-AI-01.2, F-MON-03).
 *
 * The daily counter lives in Redis under {@code user:ai_limit:{userId}:{yyyy-MM-dd}},
 * incremented atomically, with a 24h TTL set on first use.
 */
public interface AiQuotaService {

    /**
     * Checks the quota and atomically increments the daily counter.
     *
     * @throws vn.ai_study_hub_api.exception.AppException HTTP 429 when the user has already
     *         reached the plan's daily limit (counter is NOT incremented in that case).
     */
    QuotaInfo checkAndIncrement(UUID userId);

    /**
     * Read-only snapshot of current usage (does not increment). Used for the quota dashboard.
     */
    QuotaInfo getUsage(UUID userId);

    record QuotaInfo(int currentCount, int dailyLimit, int remaining) {
    }
}
