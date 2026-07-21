package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.response.AiMetricsResponse;

/**
 * Serves the admin AI/RAG observability dashboard from Langfuse, budget-bounded
 * against the Langfuse Cloud Hobby-plan limit of <strong>100 Metrics API v2
 * requests/day</strong>.
 *
 * <p>The dashboard payload is produced <em>only</em> by {@link #refreshCache()} —
 * invoked by {@code AiMetricsRefreshScheduler} 6×/day (06:00–21:00 Asia/Ho_Chi_Minh,
 * every 3h). Each refresh fans out 15 Langfuse queries → 6 × 15 = 90 requests/day,
 * leaving 10 of headroom. {@link #getDashboard()} is a <strong>cache-only read</strong>
 * that never calls Langfuse, so admin traffic cannot burst the daily quota; a cold
 * cache serves an all-empty fail-open payload (never a 5xx).
 *
 * <p>All paths fail open — an unconfigured or unreachable Langfuse yields an empty
 * payload, never a 5xx. The dashboard is auxiliary.
 */
public interface AiMetricsService {

    /**
     * Cache-only read for the admin dashboard endpoint. Returns the last payload stored
     * by {@link #refreshCache()}, or an all-empty fail-open payload when the cache is
     * cold (e.g. right after a Redis flush, before the first scheduled fire).
     * <strong>Never calls Langfuse.</strong>
     */
    AiMetricsResponse getDashboard();

    /**
     * Recomputes the default 7-day window payload (15 Langfuse Metrics API v2 queries)
     * and stores it in the {@code aiMetrics} cache under key {@code "latest"}. Idempotent
     * and fail-open. Called by {@code AiMetricsRefreshScheduler}.
     */
    void refreshCache();

    /**
     * Runs {@link #refreshCache()} only when the cache is cold — used on application
     * startup so the first admin visit after a deploy sees data instead of an empty
     * payload. A warm cache surviving from the last scheduled fire is left untouched,
     * avoiding an extra 15-query burst on every restart.
     */
    void refreshCacheIfCold();
}
