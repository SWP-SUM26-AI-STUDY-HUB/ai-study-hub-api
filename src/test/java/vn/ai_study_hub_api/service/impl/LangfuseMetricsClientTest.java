package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure-Mockito unit tests for {@link LangfuseMetricsClient}. The WebClient GET chain is
 * stubbed exactly as in {@code DocumentRagClientTest}: mock every link in
 * {@code webClient.get().uri(URI).header(...).retrieve().bodyToMono(String.class).timeout(...).block()}.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class LangfuseMetricsClientTest {

    private static final String BASE_URL = "https://jp.cloud.langfuse.com";
    private static final String PUBLIC_KEY = "pk-lf-test";
    private static final String SECRET_KEY = "sk-lf-test";

    @Mock
    private WebClient webClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LangfuseMetricsClient client;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        client = new LangfuseMetricsClient(webClient, objectMapper);
        ReflectionTestUtils.setField(client, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(client, "publicKey", PUBLIC_KEY);
        ReflectionTestUtils.setField(client, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(client, "timeout", 10);
        client.init();
    }

    @Test
    void init_stripsTrailingSlashAndMarksConfigured() {
        assertEquals(BASE_URL + "/api/public/v2/metrics",
                ReflectionTestUtils.getField(client, "endpoint"));
        assertTrue(client.isConfigured());
    }

    @Test
    void init_unconfiguredWhenKeyBlank() {
        LangfuseMetricsClient blank = new LangfuseMetricsClient(webClient, objectMapper);
        ReflectionTestUtils.setField(blank, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(blank, "publicKey", "");
        ReflectionTestUtils.setField(blank, "secretKey", "");
        blank.init();

        assertFalse(blank.isConfigured());
    }

    @Test
    void query_unconfiguredReturnsEmptyArrayWithoutCallingWebClient() {
        LangfuseMetricsClient blank = new LangfuseMetricsClient(webClient, objectMapper);
        ReflectionTestUtils.setField(blank, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(blank, "publicKey", "");
        ReflectionTestUtils.setField(blank, "secretKey", "");
        blank.init();

        JsonNode result = blank.query(Map.of("view", "observations"));

        assertTrue(result.isArray());
        assertTrue(result.isEmpty());
        verifyNoInteractions(webClient);
    }

    @Test
    void query_returnsDataArrayAndAttachesBasicAuthHeader() {
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        String body = "{\"data\":[{\"name\":\"chat\",\"p95_latency\":2421.6}]}";

        stubGetChain(body, requestedUri, authHeader);

        JsonNode data = client.query(Map.of("view", "observations"));

        assertTrue(data.isArray());
        assertEquals(1, data.size());
        assertEquals("chat", data.get(0).path("name").asText());
        assertEquals(2421.6, data.get(0).path("p95_latency").asDouble(0.0), 0.001);

        // The query payload MUST be URL-encoded into the `query` query-param.
        assertNotNull(requestedUri.get());
        assertTrue(requestedUri.get().getRawQuery().startsWith("query="),
                "expected URL-encoded `query` param, got: " + requestedUri.get());

        // Basic auth header derived from the configured keys must be attached.
        assertNotNull(authHeader.get());
        assertTrue(authHeader.get().startsWith("Basic "));
    }

    @Test
    void query_failOpenOnTransportError() {
        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(URI.class))).thenReturn(headersSpec);
        when(headersSpec.header(eq("Authorization"), any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class)))
                .thenReturn(Mono.error(new RuntimeException("Langfuse down")));

        JsonNode data = client.query(Map.of("view", "observations"));

        assertTrue(data.isArray());
        assertTrue(data.isEmpty());
    }

    @Test
    void query_failOpenWhenDataNodeAbsent() {
        stubGetChain("{\"meta\":\"no data field here\"}", new AtomicReference<>(), new AtomicReference<>());

        JsonNode data = client.query(Map.of("view", "observations"));

        assertTrue(data.isArray());
        assertTrue(data.isEmpty());
    }

    /** Stubs the full GET chain and records the requested URI + Authorization header. */
    private void stubGetChain(String body, AtomicReference<URI> requestedUri, AtomicReference<String> authHeader) {
        WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.get()).thenReturn(uriSpec);
        when(uriSpec.uri(any(URI.class))).thenAnswer(inv -> {
            requestedUri.set(inv.getArgument(0));
            return headersSpec;
        });
        when(headersSpec.header(eq("Authorization"), any())).thenAnswer(inv -> {
            // header(String, String...) packs the value(s) into the varargs array at arg index 1.
            Object arg1 = inv.getArgument(1);
            if (arg1 instanceof String[] arr && arr.length > 0) {
                authHeader.set(arr[0]);
            } else if (arg1 instanceof String s) {
                authHeader.set(s);
            }
            return headersSpec;
        });
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(String.class)))
                .thenReturn(Mono.just(body).timeout(Duration.ofSeconds(10)));
    }
}
