package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.response.AiMetricsResponse;

/**
 * Aggregates AI/RAG observability metrics from Langfuse for the admin dashboard.
 *
 * <p>The single method fans out the Langfuse Metrics API v2 queries defined in
 * {@code docs/langfuse-metrics-cookbook.md} (§3.1–3.10) in parallel and assembles
 * one {@link AiMetricsResponse}. The result is Redis-cached ({@code aiMetrics},
 * ~5 min TTL) and <strong>fails open</strong> — an unconfigured or unreachable
 * Langfuse yields an all-empty payload, never a 5xx.
 */
public interface AiMetricsService {

    /**
     * Builds the full AI-metrics dashboard payload for the given UTC window.
     *
     * @param fromTs ISO-8601 UTC lower bound (inclusive), e.g. {@code "2026-07-10T00:00:00Z"}.
     * @param toTs   ISO-8601 UTC upper bound (exclusive).
     * @return the aggregated dashboard payload (cached, fail-open).
     */
    AiMetricsResponse getAiMetrics(String fromTs, String toTs);
}
