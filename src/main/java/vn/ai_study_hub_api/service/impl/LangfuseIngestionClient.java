package vn.ai_study_hub_api.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thin, fail-open HTTP client for the Langfuse <strong>(legacy) Ingestion API</strong>
 * ({@code POST {base}/api/public/ingestion}, Basic auth, a batch of {@code trace-create} /
 * {@code generation-create} events).
 *
 * <p><strong>Why this exists.</strong> The OpenAI Moderation API is called from <em>this</em>
 * Java backend ({@link AutoModerationServiceImpl}), not from the Langfuse-instrumented RAG
 * service, so moderation cost / latency / volume are otherwise invisible in Langfuse. This
 * client lets moderation push a trace + generations directly, reusing the same {@code langfuse.*}
 * keys already wired for {@link LangfuseMetricsClient}. It is a sibling of that client: same
 * shared {@link WebClient} bean, same blocking {@code .timeout(...).block()} style, same
 * fail-open contract — any failure logs a WARN and never affects the moderation path.
 *
 * <p><strong>Token usage is estimated.</strong> The OpenAI Moderation endpoint (free of charge)
 * does not return a {@code usage} object, so callers estimate input tokens from the request and
 * pass them here. To surface cost, add a Model Definition for {@code omni-moderation-latest} in
 * the Langfuse UI (typically $0; image-tier pricing if you want to track rate-limit spend).
 *
 * <p><strong>Future migration.</strong> Langfuse recommends the OpenTelemetry ingestion endpoint
 * over this (legacy) Ingestion API for new traces. If/when it is removed, migrate by adding the
 * OTel SDK + OTLP exporter pointed at {@code /api/public/otel} — the fail-open boundary here
 * keeps that a localized change.
 *
 * @see LangfuseMetricsClient
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LangfuseIngestionClient {

    private final WebClient webClient;

    @Value("${langfuse.base-url:https://cloud.langfuse.com}")
    private String baseUrl;

    @Value("${langfuse.public-key:}")
    private String publicKey;

    @Value("${langfuse.secret-key:}")
    private String secretKey;

    @Value("${langfuse.timeout-seconds:10}")
    private int timeout;

    /** Full ingestion endpoint, derived once from {@code baseUrl} (trailing slashes stripped). */
    private String endpoint;

    /** {@code "Basic " + base64(publicKey:secretKey)}, or {@code null} when unconfigured. */
    private String authHeader;

    @PostConstruct
    void init() {
        String base = baseUrl == null ? "" : baseUrl.strip();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.endpoint = base + "/api/public/ingestion";
        this.authHeader = isPresent(publicKey) && isPresent(secretKey)
                ? "Basic " + Base64.getEncoder().encodeToString(
                        (publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8))
                : null;
        log.info("Langfuse ingestion client: endpoint={}, configured={}", endpoint, authHeader != null);
    }

    /** {@code true} when both API keys are present (i.e. ingestion will actually be attempted). */
    public boolean isConfigured() {
        return authHeader != null;
    }

    /**
     * POST a batch of ingestion events. Fail-open: blank keys, empty batch, transport error,
     * timeout, or non-2xx are all swallowed with a WARN — moderation must never depend on
     * Langfuse being reachable.
     */
    public void ingest(List<Map<String, Object>> batch) {
        if (authHeader == null || batch == null || batch.isEmpty()) {
            return;
        }
        try {
            webClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("batch", batch))
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeout))
                    .block();
        } catch (Exception e) {
            log.warn("Langfuse ingestion failed (fail-open): {}", e.toString());
        }
    }

    // --- Langfuse legacy ingestion event builders (Map-based; JSON-native, codec-serialized) ---

    /** A {@code trace-create} event. */
    public static Map<String, Object> traceEvent(String traceId, String name,
                                                 String userId, List<String> tags,
                                                 Map<String, Object> metadata) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", traceId);
        body.put("name", name);
        if (userId != null && !userId.isBlank()) {
            body.put("userId", userId);
        }
        if (tags != null && !tags.isEmpty()) {
            body.put("tags", tags);
        }
        if (metadata != null && !metadata.isEmpty()) {
            body.put("metadata", metadata);
        }
        return event("trace-create", body);
    }

    /**
     * A {@code generation-create} event linked to a trace.
     *
     * @param level         Langfuse observation level — {@code "DEFAULT"} on success,
     *                      {@code "ERROR"} on failure (lets the dashboard filter errored calls).
     * @param statusMessage optional status detail (e.g. exception text on failure); nullable.
     */
    public static Map<String, Object> generationEvent(String traceId, String generationId,
                                                     String name, String model,
                                                     String startTime, String endTime,
                                                     String level, String statusMessage,
                                                     int inputTokens, int outputTokens,
                                                     Map<String, Object> metadata) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", generationId);
        body.put("traceId", traceId);
        body.put("name", name);
        body.put("model", model);
        if (startTime != null) {
            body.put("startTime", startTime);
        }
        if (endTime != null) {
            body.put("endTime", endTime);
        }
        if (level != null) {
            body.put("level", level);
        }
        if (statusMessage != null && !statusMessage.isBlank()) {
            body.put("statusMessage", statusMessage);
        }
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("input", inputTokens);
        usage.put("output", outputTokens);
        usage.put("unit", "TOKENS");
        body.put("usage", usage);
        if (metadata != null && !metadata.isEmpty()) {
            body.put("metadata", metadata);
        }
        return event("generation-create", body);
    }

    private static Map<String, Object> event(String type, Map<String, Object> body) {
        Map<String, Object> ev = new LinkedHashMap<>();
        ev.put("id", UUID.randomUUID().toString());
        ev.put("type", type);
        ev.put("body", body);
        return ev;
    }

    private static boolean isPresent(String s) {
        return s != null && !s.isBlank();
    }
}
