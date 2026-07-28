package vn.ai_study_hub_api.service.impl;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import vn.ai_study_hub_api.config.CacheConfig;
import vn.ai_study_hub_api.controller.response.AiMetricsResponse;
import vn.ai_study_hub_api.controller.response.MetricRow;
import vn.ai_study_hub_api.controller.response.TimeSeriesPoint;
import vn.ai_study_hub_api.service.AiMetricsService;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Builds + serves the admin AI/RAG dashboard payload from the Langfuse Metrics API v2
 * ({@code docs/langfuse-metrics-cookbook.md} §3.1–3.10).
 *
 * <h3>Budget model (Langfuse Cloud Hobby plan = 100 Metrics API v2 requests/day)</h3>
 * <ul>
 *   <li><b>Writes only via {@link #refreshCache()}</b> — called by
 *   {@code AiMetricsRefreshScheduler} 6×/day (06:00–21:00 Asia/Ho_Chi_Minh, every 3h).
 *   Each refresh fans out 15 Langfuse queries in parallel → 6 × 15 = 90 requests/day,
 *   leaving 10 of headroom.</li>
 *   <li><b>Reads via {@link #getDashboard()}</b> — a cache-only lookup under the single
 *   key {@link #LATEST_KEY}. <strong>Never calls Langfuse</strong>, so admin refresh
 *   storms cannot burst the daily quota. A cold cache serves an all-empty fail-open
 *   payload (never a 5xx).</li>
 * </ul>
 *
 * <h3>Fan-out internals</h3>
 * <ul>
 *   <li><b>Parallel fan-out</b> — every widget query runs on its own virtual thread
 *   ({@link Executors#newVirtualThreadPerTaskExecutor()}). 15 round-trips collapse to
 *   ≈ the slowest single call (≤ the client's 10s timeout), instead of 15×1–3s sequential.</li>
 *   <li><b>Fail-open per widget</b> — {@link #async(String, Supplier, Object)} catches every
 *   failure and resolves to the widget's fallback. The Langfuse client additionally fails
 *   open on transport/auth/timeout, so {@code .join()} never throws.</li>
 *   <li><b>Unconfigured → empty</b> — when no Langfuse keys are set, returns an all-empty
 *   payload immediately (no threads spawned, no calls attempted).</li>
 * </ul>
 *
 * <p>Cache I/O is manual ({@link CacheManager}) rather than {@code @Cacheable} so the
 * read path can distinguish "serve cached" from "compute" — the endpoint must never
 * trigger the 15-query fan-out.
 *
 * <p>Caching behavior is verified structurally; the parallel orchestration is exercised
 * directly with a synchronous executor in {@code AiMetricsServiceImplTest}.
 */
@Service
@Slf4j
public class AiMetricsServiceImpl implements AiMetricsService {

    /** Chat/material routes recorded on root-trace metadata by the RAG service. */
    private static final List<String> ROUTES =
            List.of("qa", "smalltalk", "summary", "guardrail_block", "quiz", "flashcard");

    /** Default dashboard window: rolling last N days from "now" (UTC). */
    private static final long DEFAULT_WINDOW_DAYS = 7;

    /** Single cache key — there is only ever one "latest" dashboard payload. */
    static final String LATEST_KEY = "latest";

    private final LangfuseMetricsClient client;
    private final CacheManager cacheManager;

    /**
     * Process-wide virtual-thread executor dedicated to fanning out Langfuse queries.
     * Distinct from {@code taskExecutor} (document async) so a dashboard burst cannot starve
     * ingestion/chat callbacks. Daemon virtual threads; drained on shutdown.
     */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AiMetricsServiceImpl(LangfuseMetricsClient client, CacheManager cacheManager) {
        this.client = client;
        this.cacheManager = cacheManager;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    // --- Cache-only read (endpoint) ---------------------------------------

    @Override
    public AiMetricsResponse getDashboard() {
        Cache cache = aiMetricsCache();
        if (cache != null) {
            Cache.ValueWrapper wrapper = cache.get(LATEST_KEY);
            if (wrapper != null && wrapper.get() instanceof AiMetricsResponse cached) {
                return cached;
            }
        }
        // Cold cache (before first scheduled fire / after Redis flush) — fail open without
        // touching Langfuse. The default window is stamped so the UI labels stay sane.
        String[] window = defaultWindow();
        return AiMetricsResponse.empty(window[0], window[1], client.isConfigured());
    }

    // --- Scheduler-driven write -------------------------------------------

    @Override
    public void refreshCache() {
        String[] window = defaultWindow();
        AiMetricsResponse result;
        if (!client.isConfigured()) {
            result = AiMetricsResponse.empty(window[0], window[1], false);
        } else {
            try {
                result = computeMetrics(window[0], window[1]);
            } catch (Throwable t) {
                // Per-widget failures already fail-open inside computeMetrics; this guards
                // anything structural so a refresh never throws into the scheduler thread.
                log.warn("AI metrics refresh failed: {}", t.toString());
                result = AiMetricsResponse.empty(window[0], window[1], true);
            }
        }
        putCache(result);
        log.info("AI metrics cache refreshed (configured={}, dataAvailable={}).",
                result.isConfigured(), result.isDataAvailable());
    }

    @Override
    public void refreshCacheIfCold() {
        Cache cache = aiMetricsCache();
        if (cache != null && cache.get(LATEST_KEY) != null) {
            return; // warm — a redeploy with surviving Redis cache adds no Langfuse burst.
        }
        log.info("AI metrics cache cold on startup — refreshing.");
        refreshCache();
    }

    // --- Fan-out (the 15-query Langfuse orchestration) --------------------

    /**
     * Builds the full AI-metrics payload for the given UTC window by fanning out the
     * 15 Langfuse Metrics API v2 queries in parallel. Package-visible for unit tests;
     * production callers go through {@link #refreshCache()}.
     */
    AiMetricsResponse computeMetrics(String fromTs, String toTs) {
        if (!client.isConfigured()) {
            return AiMetricsResponse.empty(fromTs, toTs, false);
        }

        // §3.1 — p95 latency per observation name (pipeline stage funnel).
        CompletableFuture<List<MetricRow>> latencyByStage = async("latencyByStage",
                () -> rows(client.query(new Q(fromTs, toTs)
                        .metrics("latency", "p95").dim("name")
                        .order("p95_latency", "desc").limit(20).build()),
                        "name", "p95_latency"),
                List.of());

        // §3.2 — request count per trace name (SPAN observations only).
        CompletableFuture<List<MetricRow>> requestVolume = async("requestVolume",
                () -> rows(client.query(new Q(fromTs, toTs)
                        .metrics("count", "count").dim("traceName")
                        .filter(strFilter("type", "SPAN"))
                        .order("count_count", "desc").limit(20).build()),
                        "traceName", "count_count"),
                List.of());

        // §3.3 — p95 latency per trace name (SPAN observations only).
        CompletableFuture<List<MetricRow>> endpointLatency = async("endpointLatency",
                () -> rows(client.query(new Q(fromTs, toTs)
                        .metrics("latency", "p95").dim("traceName")
                        .filter(strFilter("type", "SPAN"))
                        .order("p95_latency", "desc").build()),
                        "traceName", "p95_latency"),
                List.of());

        // §3.4 — summed token usage per model.
        CompletableFuture<List<MetricRow>> tokenUsageByModel = async("tokenUsageByModel",
                () -> rows(client.query(new Q(fromTs, toTs)
                        .metrics("totalTokens", "sum").dim("providedModelName")
                        .order("sum_totalTokens", "desc").build()),
                        "providedModelName", "sum_totalTokens"),
                List.of());

        // §3.5 — summed USD cost per model (Langfuse auto-prices Gemini).
        CompletableFuture<List<MetricRow>> costByModel = async("costByModel",
                () -> rows(client.query(new Q(fromTs, toTs)
                        .metrics("totalCost", "sum").dim("providedModelName").build()),
                        "providedModelName", "sum_totalCost"),
                List.of());

        // §3.6 — daily summed token usage (time series).
        CompletableFuture<List<TimeSeriesPoint>> tokenTimeSeries = async("tokenTimeSeries",
                () -> series(client.query(new Q(fromTs, toTs)
                        .metrics("totalTokens", "sum").noDim()
                        .granularity("day").limit(50).build()),
                        "sum_totalTokens"),
                List.of());

        // §3.7 — average citation_coverage score (0..1).
        CompletableFuture<Double> citationCoverage = async("citationCoverage",
                () -> singleDouble(client.query(new Q(fromTs, toTs)
                        .view("scores-numeric")
                        .metrics("value", "avg").dim("name")
                        .filter(strFilter("name", "citation_coverage")).build()),
                        "avg_value"),
                0.0);

        // §3.8 — refusal count per material type (quiz/flashcard metadata.refused=true).
        CompletableFuture<List<MetricRow>> refusalCount = async("refusalCount",
                () -> rows(client.query(new Q(fromTs, toTs)
                        .metrics("count", "count").dim("name")
                        .filter(metaFilter("refused", "true")).build()),
                        "name", "count_count"),
                List.of());

        // §3.10 — empty-retrieval count per observation name (metadata.empty_retrieval=true).
        CompletableFuture<List<MetricRow>> emptyRetrievalByEndpoint = async("emptyRetrieval",
                () -> rows(client.query(new Q(fromTs, toTs)
                        .metrics("count", "count").dim("name")
                        .filter(metaFilter("empty_retrieval", "true")).build()),
                        "name", "count_count"),
                List.of());

        // §3.9 — route distribution. metadata.* cannot be GROUPed, so one count query per route.
        List<CompletableFuture<MetricRow>> routeFutures = ROUTES.stream()
                .map(route -> async("route:" + route,
                        () -> new MetricRow(route, singleLong(client.query(new Q(fromTs, toTs)
                                .metrics("count", "count").noDim()
                                .filter(metaFilter("route", route)).build()),
                                "count_count")),
                        new MetricRow(route, 0.0)))
                .toList();

        // Every task was submitted upfront and runs in parallel on virtual threads; each is
        // fail-open so join() cannot throw. Joining individually below collects results with the
        // same wall time as allOf (max of the per-call durations).

        List<MetricRow> reqVol = requestVolume.join();
        List<MetricRow> tokens = tokenUsageByModel.join();
        List<MetricRow> costs = costByModel.join();
        List<MetricRow> emptyByEp = emptyRetrievalByEndpoint.join();
        List<MetricRow> routes = routeFutures.stream().map(CompletableFuture::join).toList();
        long emptyRetrievalCount = (long) emptyByEp.stream().mapToDouble(MetricRow::getValue).sum();
        long totalRouteCounts = (long) routes.stream().mapToDouble(MetricRow::getValue).sum();

        boolean dataAvailable = !(latencyByStage.join().isEmpty() && reqVol.isEmpty()
                && endpointLatency.join().isEmpty() && tokens.isEmpty() && costs.isEmpty()
                && tokenTimeSeries.join().isEmpty() && citationCoverage.join() == 0.0
                && refusalCount.join().isEmpty() && emptyRetrievalCount == 0L && totalRouteCounts == 0L);

        return AiMetricsResponse.builder()
                .from(fromTs)
                .to(toTs)
                .generatedAt(LocalDateTime.now(ZoneOffset.UTC))
                .configured(true)
                .dataAvailable(dataAvailable)
                .latencyByStage(latencyByStage.join())
                .requestVolume(reqVol)
                .endpointLatency(endpointLatency.join())
                .tokenUsageByModel(tokens)
                .costByModel(costs)
                .tokenTimeSeries(tokenTimeSeries.join())
                .citationCoverageAvg(citationCoverage.join())
                .refusalCount(refusalCount.join())
                .routeDistribution(routes)
                .emptyRetrievalCount(emptyRetrievalCount)
                .emptyRetrievalByEndpoint(emptyByEp)
                .totalCost(costs.stream().mapToDouble(MetricRow::getValue).sum())
                .totalTokens((long) tokens.stream().mapToDouble(MetricRow::getValue).sum())
                .totalRequests((long) reqVol.stream().mapToDouble(MetricRow::getValue).sum())
                .build();
    }

    // --- Async fail-open helper --------------------------------------------

    private <T> CompletableFuture<T> async(String label, Supplier<T> task, T fallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.get();
            } catch (Throwable t) {
                log.warn("AI metrics [{}] failed: {}", label, t.toString());
                return fallback;
            }
        }, executor);
    }

    // --- Row mapping --------------------------------------------------------

    private List<MetricRow> rows(JsonNode data, String labelField, String valueField) {
        List<MetricRow> out = new ArrayList<>();
        if (data == null || !data.isArray()) {
            return out;
        }
        for (JsonNode r : data) {
            out.add(new MetricRow(r.path(labelField).asText(""), r.path(valueField).asDouble(0.0)));
        }
        return out;
    }

    private List<TimeSeriesPoint> series(JsonNode data, String valueField) {
        List<TimeSeriesPoint> out = new ArrayList<>();
        if (data == null || !data.isArray()) {
            return out;
        }
        for (JsonNode r : data) {
            out.add(new TimeSeriesPoint(r.path("time_dimension").asText(""), r.path(valueField).asDouble(0.0)));
        }
        return out;
    }

    private double singleDouble(JsonNode data, String valueField) {
        if (data == null || !data.isArray() || data.isEmpty()) {
            return 0.0;
        }
        return data.get(0).path(valueField).asDouble(0.0);
    }

    private long singleLong(JsonNode data, String valueField) {
        if (data == null || !data.isArray() || data.isEmpty()) {
            return 0L;
        }
        return data.get(0).path(valueField).asLong(0L);
    }

    // --- Cache + window helpers -------------------------------------------

    private Cache aiMetricsCache() {
        try {
            return cacheManager.getCache(CacheConfig.CACHE_AI_METRICS);
        } catch (Throwable t) {
            log.warn("AI metrics cache lookup failed: {}", t.toString());
            return null;
        }
    }

    private void putCache(AiMetricsResponse result) {
        Cache cache = aiMetricsCache();
        if (cache == null) {
            return;
        }
        try {
            cache.put(LATEST_KEY, result);
        } catch (Throwable t) {
            log.warn("AI metrics cache put failed: {}", t.toString());
        }
    }

    /** Default rolling window as ISO-8601 UTC instants: {@code [from, to]} = [now-7d, now]. */
    private static String[] defaultWindow() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return new String[]{
                iso(now.minusDays(DEFAULT_WINDOW_DAYS)),
                iso(now)
        };
    }

    private static String iso(LocalDateTime t) {
        return DateTimeFormatter.ISO_INSTANT.format(t.toInstant(ZoneOffset.UTC));
    }

    // --- Query payload builders --------------------------------------------

    private static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Object> strFilter(String column, String value) {
        return obj("column", column, "operator", "=", "value", value, "type", "string");
    }

    private static Map<String, Object> metaFilter(String key, String value) {
        // metadata.* filters MUST use type=stringObject with string values (cookbook §4).
        return obj("column", "metadata", "operator", "=", "key", key, "value", value, "type", "stringObject");
    }

    /** Fluent builder for a Langfuse query payload; captures the per-call time window. */
    private static final class Q {
        private final String from;
        private final String to;
        private String view = "observations";
        private List<Map<String, Object>> metrics = List.of();
        private List<Map<String, Object>> dimensions = List.of();
        private List<Map<String, Object>> filters = List.of();
        private List<Map<String, Object>> orderBy = List.of();
        private Integer rowLimit;
        private String granularity;

        Q(String from, String to) {
            this.from = from;
            this.to = to;
        }

        Q view(String v) { this.view = v; return this; }
        Q metrics(String measure, String agg) { this.metrics = List.of(obj("measure", measure, "aggregation", agg)); return this; }
        Q dim(String field) { this.dimensions = List.of(obj("field", field)); return this; }
        Q noDim() { this.dimensions = List.of(); return this; }
        Q filter(Map<String, Object> f) { this.filters = List.of(f); return this; }
        Q order(String field, String dir) { this.orderBy = List.of(obj("field", field, "direction", dir)); return this; }
        Q limit(int n) { this.rowLimit = n; return this; }
        Q granularity(String g) { this.granularity = g; return this; }

        Map<String, Object> build() {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("view", view);
            q.put("metrics", metrics);
            q.put("dimensions", dimensions);
            if (!filters.isEmpty()) { q.put("filters", filters); }
            if (!orderBy.isEmpty()) { q.put("orderBy", orderBy); }
            if (granularity != null) { q.put("timeDimension", obj("granularity", granularity)); }
            if (rowLimit != null) { q.put("config", obj("row_limit", rowLimit)); }
            q.put("fromTimestamp", from);
            q.put("toTimestamp", to);
            return q;
        }
    }
}
