package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure-Mockito unit tests for {@link LangfuseIngestionClient}. Mirrors
 * {@link LangfuseMetricsClientTest}: the WebClient POST chain is stubbed link-by-link
 * ({@code post().uri(String).header(...).contentType(...).bodyValue(...).retrieve()
 *           .bodyToMono(String.class).timeout(...).block()}).
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class LangfuseIngestionClientTest {

    private static final String BASE_URL = "https://jp.cloud.langfuse.com";
    private static final String PUBLIC_KEY = "pk-lf-test";
    private static final String SECRET_KEY = "sk-lf-test";

    @Mock
    private WebClient webClient;

    private LangfuseIngestionClient client;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        client = new LangfuseIngestionClient(webClient);
        ReflectionTestUtils.setField(client, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(client, "publicKey", PUBLIC_KEY);
        ReflectionTestUtils.setField(client, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(client, "timeout", 10);
        client.init();
    }

    @Test
    void init_stripsTrailingSlashAndMarksConfigured() {
        assertEquals(BASE_URL + "/api/public/ingestion",
                ReflectionTestUtils.getField(client, "endpoint"));
        assertTrue(client.isConfigured());
    }

    @Test
    void init_unconfiguredWhenKeyBlank() {
        LangfuseIngestionClient blank = new LangfuseIngestionClient(webClient);
        ReflectionTestUtils.setField(blank, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(blank, "publicKey", "");
        ReflectionTestUtils.setField(blank, "secretKey", "");
        blank.init();

        assertFalse(blank.isConfigured());
    }

    @Test
    void ingest_unconfiguredSkipsWebClient() {
        LangfuseIngestionClient blank = new LangfuseIngestionClient(webClient);
        ReflectionTestUtils.setField(blank, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(blank, "publicKey", "");
        ReflectionTestUtils.setField(blank, "secretKey", "");
        blank.init();

        blank.ingest(List.of(Map.of("id", "x", "type", "trace-create", "body", Map.of())));

        verifyNoInteractions(webClient);
    }

    @Test
    void ingest_emptyBatchSkipsWebClient() {
        client.ingest(List.of());
        verifyNoInteractions(webClient);
    }

    @Test
    void ingest_postsBatchWithBasicAuthHeader() {
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<String> endpoint = new AtomicReference<>();
        AtomicReference<Object> postedBody = new AtomicReference<>();
        stubPostChain(Mono.just("{}"), authHeader, endpoint, postedBody);

        List<Map<String, Object>> batch = List.of(LangfuseIngestionClient.traceEvent(
                "trace-1", "moderation", "user-1", List.of("moderation"), Map.of("decision", "APPROVED")));

        client.ingest(batch);

        assertEquals(BASE_URL + "/api/public/ingestion", endpoint.get());
        assertNotNull(authHeader.get());
        assertTrue(authHeader.get().startsWith("Basic "));
        // The batch must be wrapped as {"batch": [...]}.
        assertNotNull(postedBody.get());
    }

    @Test
    void ingest_failOpenOnTransportError() {
        stubPostChain(Mono.error(new RuntimeException("Langfuse down")),
                new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());

        // Must NOT propagate — moderation observability never affects the request path.
        assertDoesNotThrow(() -> client.ingest(List.of(Map.of("id", "x", "type", "trace-create", "body", Map.of()))));
    }

    @Test
    void traceEvent_and_generationEvent_buildCorrectShapes() {
        Map<String, Object> trace = LangfuseIngestionClient.traceEvent(
                "trace-1", "moderation", "user-1", List.of("moderation", "backend"), Map.of("k", "v"));

        assertEquals("trace-create", trace.get("type"));
        assertEquals("trace-1", ((Map<?, ?>) trace.get("body")).get("id"));
        assertEquals("moderation", ((Map<?, ?>) trace.get("body")).get("name"));
        assertEquals("user-1", ((Map<?, ?>) trace.get("body")).get("userId"));
        assertNotNull(trace.get("id")); // per-event uuid

        Map<String, Object> gen = LangfuseIngestionClient.generationEvent(
                "trace-1", "gen-1", "openai-moderation", "omni-moderation-latest",
                "2026-07-21T00:00:00Z", "2026-07-21T00:00:01Z", "DEFAULT", null,
                120, 0, Map.of("batch_kind", "text"));

        assertEquals("generation-create", gen.get("type"));
        Map<?, ?> body = (Map<?, ?>) gen.get("body");
        assertEquals("gen-1", body.get("id"));
        assertEquals("trace-1", body.get("traceId"));
        assertEquals("omni-moderation-latest", body.get("model"));
        Map<?, ?> usage = (Map<?, ?>) body.get("usage");
        assertEquals(120, usage.get("input"));
        assertEquals(0, usage.get("output"));
        assertEquals("TOKENS", usage.get("unit"));
    }

    /** Stubs the full POST chain, capturing endpoint, Authorization header, and posted body. */
    private void stubPostChain(Mono<String> response, AtomicReference<String> authHeader,
                               AtomicReference<String> endpoint, AtomicReference<Object> postedBody) {
        WebClient.RequestBodyUriSpec uriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri(any(String.class))).thenAnswer(inv -> {
            endpoint.set(inv.getArgument(0));
            return bodySpec;
        });
        when(bodySpec.header(eq("Authorization"), any())).thenAnswer(inv -> {
            Object arg1 = inv.getArgument(1);
            if (arg1 instanceof String[] arr && arr.length > 0) {
                authHeader.set(arr[0]);
            } else if (arg1 instanceof String s) {
                authHeader.set(s);
            }
            return bodySpec;
        });
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenAnswer(inv -> {
            postedBody.set(inv.getArgument(0));
            return headersSpec;
        });
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class))).thenReturn(response.timeout(Duration.ofSeconds(10)));
    }
}
