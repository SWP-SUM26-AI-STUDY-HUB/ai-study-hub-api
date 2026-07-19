package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Aggregated AI/RAG observability payload for the admin dashboard, sourced from
 * the Langfuse Metrics API v2 (see {@code docs/langfuse-metrics-cookbook.md}).
 *
 * <p>One fetch populates every dashboard widget: latency funnel, request volume,
 * endpoint latency, token/cost-by-model, daily token series, citation coverage,
 * refusal counts, chat-route distribution, and empty-retrieval rate. The service
 * fan-outs the underlying Langfuse queries in parallel and <strong>fails open</strong>:
 * if Langfuse is unconfigured or unreachable, every field degrades to empty/zero
 * rather than surfacing a 5xx — the dashboard is auxiliary and must never break
 * the admin console.
 *
 * <p>Widget → cookbook section mapping is documented on each field.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMetricsResponse {

    /** ISO-8601 UTC lower bound of the queried window (inclusive). */
    private String from;

    /** ISO-8601 UTC upper bound of the queried window (exclusive). */
    private String to;

    /** Server-side generation timestamp (UTC). */
    private LocalDateTime generatedAt;

    /** {@code true} when Langfuse API keys are configured (else the payload is all-empty). */
    private boolean configured;

    /** {@code true} when at least one widget returned data (else Langfuse is down / no traces). */
    private boolean dataAvailable;

    // --- Latency / volume ---------------------------------------------------

    /** §3.1 — p95 latency (ms) per observation name (pipeline stage), desc. */
    private List<MetricRow> latencyByStage;

    /** §3.2 — request count per trace name (SPAN observations only), desc. */
    private List<MetricRow> requestVolume;

    /** §3.3 — p95 latency (ms) per trace name (SPAN observations only), desc. */
    private List<MetricRow> endpointLatency;

    // --- Cost / tokens ------------------------------------------------------

    /** §3.4 — summed token usage per model, desc. */
    private List<MetricRow> tokenUsageByModel;

    /** §3.5 — summed USD cost per model (Langfuse auto-prices Gemini). */
    private List<MetricRow> costByModel;

    /** §3.6 — daily summed token usage (time series). */
    private List<TimeSeriesPoint> tokenTimeSeries;

    // --- RAG quality --------------------------------------------------------

    /** §3.7 — average {@code citation_coverage} score (0..1) across QA turns. */
    private double citationCoverageAvg;

    /** §3.8 — refusal count per material type (quiz/flashcard {@code metadata.refused=true}). */
    private List<MetricRow> refusalCount;

    /** §3.9 — chat/material route distribution (metadata cannot be grouped → N filtered counts). */
    private List<MetricRow> routeDistribution;

    /** §3.10 — total QA turns that retrieved zero docs ({@code metadata.empty_retrieval=true}). */
    private long emptyRetrievalCount;

    /** §3.10 — empty-retrieval count broken down by observation name. */
    private List<MetricRow> emptyRetrievalByEndpoint;

    // --- Derived summary cards ----------------------------------------------

    /** Sum of {@link #costByModel} values (USD). */
    private double totalCost;

    /** Sum of {@link #tokenUsageByModel} values. */
    private long totalTokens;

    /** Sum of {@link #requestVolume} values. */
    private long totalRequests;

    /** All-empty payload for the unconfigured / fail-open path. */
    public static AiMetricsResponse empty(String from, String to, boolean configured) {
        return AiMetricsResponse.builder()
                .from(from)
                .to(to)
                .generatedAt(LocalDateTime.now(java.time.ZoneOffset.UTC))
                .configured(configured)
                .dataAvailable(false)
                .latencyByStage(List.of())
                .requestVolume(List.of())
                .endpointLatency(List.of())
                .tokenUsageByModel(List.of())
                .costByModel(List.of())
                .tokenTimeSeries(List.of())
                .citationCoverageAvg(0.0)
                .refusalCount(List.of())
                .routeDistribution(List.of())
                .emptyRetrievalCount(0L)
                .emptyRetrievalByEndpoint(List.of())
                .totalCost(0.0)
                .totalTokens(0L)
                .totalRequests(0L)
                .build();
    }
}
