package vn.ai_study_hub_api.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Thin, blocking HTTP client for the <strong>Langfuse Metrics API v2</strong>
 * ({@code GET {base}/api/public/v2/metrics?query={URL-encoded JSON}}, Basic auth).
 *
 * <p>Sibling of {@code DocumentRagClient} / {@code StudyMaterialClientImpl}: same shared
 * {@link WebClient} bean, same blocking {@code .timeout(...).block()} style. The single
 * method {@link #query(Map)} serializes the query payload, URL-encodes it into the
 * {@code query} query-param, and returns the response's {@code data} array.
 *
 * <p><strong>Fail-open by design.</strong> A missing/blank key, transport error, non-2xx,
 * timeout, or malformed body all resolve to an empty {@link JsonNode} array — the dashboard
 * is auxiliary and must never surface a 5xx. Per-call logging is at WARN so a down Langfuse
 * is observable without flooding. Mirrors the RAG service's {@code LANGFUSE_ENABLED}
 * fail-open philosophy.
 *
 * @see <a href="docs/langfuse-metrics-cookbook.md">Langfuse Metrics API v2 Cookbook</a>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LangfuseMetricsClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${langfuse.base-url:https://cloud.langfuse.com}")
    private String baseUrl;

    @Value("${langfuse.public-key:}")
    private String publicKey;

    @Value("${langfuse.secret-key:}")
    private String secretKey;

    @Value("${langfuse.timeout-seconds:10}")
    private int timeout;

    /** Full metrics endpoint, derived once from {@code baseUrl} (trailing slashes stripped). */
    private String endpoint;

    /** {@code "Basic " + base64(publicKey:secretKey)}, or {@code null} when unconfigured. */
    private String authHeader;

    @PostConstruct
    void init() {
        String base = baseUrl == null ? "" : baseUrl.strip();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.endpoint = base + "/api/public/v2/metrics";
        this.authHeader = isPresent(publicKey) && isPresent(secretKey)
                ? "Basic " + Base64.getEncoder().encodeToString(
                        (publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8))
                : null;
        log.info("Langfuse metrics client: endpoint={}, configured={}", endpoint, authHeader != null);
    }

    /** {@code true} when both API keys are present (i.e. calls will actually be attempted). */
    public boolean isConfigured() {
        return authHeader != null;
    }

    /**
     * Executes a Langfuse Metrics v2 query and returns the {@code data} array. Fail-open:
     * any failure (unconfigured, transport, timeout, malformed body) yields an empty array
     * so callers can treat the result uniformly.
     *
     * @param payload the query object (view, metrics, dimensions, filters, timeDimension, …).
     *                {@code fromTimestamp}/{@code toTimestamp} are owned by the caller.
     * @return the response {@code data} node (an array), or an empty array on failure.
     */
    public JsonNode query(Map<String, Object> payload) {
        if (authHeader == null) {
            return objectMapper.createArrayNode();
        }
        String queryJson;
        try {
            queryJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("Langfuse: failed to serialize query payload ({})", e.getMessage());
            return objectMapper.createArrayNode();
        }
        // Build the request URI manually: URLEncoder produces application/x-www-form-urlencoded
        // output (the cookbook-mandated encoding) and — unlike UriComponentsBuilder.queryParam —
        // does not misread the JSON's '{' / '}' as URI-template variables. Passing a pre-encoded
        // URI object (not a String) ensures WebClient uses it verbatim without re-encoding.
        URI uri = URI.create(endpoint + "?query=" + URLEncoder.encode(queryJson, StandardCharsets.UTF_8));
        try {
            String body = webClient.get()
                    .uri(uri)
                    .header("Authorization", authHeader)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(timeout))
                    .block();
            JsonNode root = (body == null || body.isBlank()) ? null : objectMapper.readTree(body);
            JsonNode data = root == null ? null : root.path("data");
            return (data != null && data.isArray()) ? data : objectMapper.createArrayNode();
        } catch (Exception e) {
            // Fail-open: never let a Langfuse outage 5xx the admin dashboard.
            log.warn("Langfuse metrics query failed: {}", e.toString());
            return objectMapper.createArrayNode();
        }
    }

    private static boolean isPresent(String s) {
        return s != null && !s.isBlank();
    }
}
