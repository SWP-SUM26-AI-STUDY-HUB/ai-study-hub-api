package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.ai_study_hub_api.controller.response.AiMetricsResponse;
import vn.ai_study_hub_api.controller.response.MetricRow;
import vn.ai_study_hub_api.controller.response.TimeSeriesPoint;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pure-Mockito unit tests for {@link AiMetricsServiceImpl}.
 *
 * <p>{@link LangfuseMetricsClient} is mocked; its {@code query(Map)} is stubbed with an
 * {@link org.mockito.stubbing.Answer} that dispatches a fixture per payload (so the test
 * exercises both the query-construction AND the field-mapping). The service's own
 * virtual-thread fan-out runs for real — {@code .join()} makes the result deterministic.
 *
 * <p>Cache I/O ({@link CacheManager}/{@link Cache}) is mocked; the scheduler-driven write
 * path and cache-only read path are asserted directly.
 */
@ExtendWith(MockitoExtension.class)
class AiMetricsServiceImplTest {

    private static final String FROM = "2026-07-10T00:00:00Z";
    private static final String TO = "2026-07-18T00:00:00Z";

    @Mock
    private LangfuseMetricsClient client;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache aiMetricsCache;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiMetricsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiMetricsServiceImpl(client, cacheManager);
    }

    // --- Fan-out (computeMetrics) -----------------------------------------

    @Test
    void computeMetrics_unconfiguredReturnsEmptyWithoutQuerying() {
        when(client.isConfigured()).thenReturn(false);

        AiMetricsResponse resp = service.computeMetrics(FROM, TO);

        assertEquals(FROM, resp.getFrom());
        assertEquals(TO, resp.getTo());
        assertFalse(resp.isConfigured());
        assertFalse(resp.isDataAvailable());
        assertTrue(resp.getLatencyByStage().isEmpty());
        assertTrue(resp.getRouteDistribution().isEmpty());
        assertEquals(0L, resp.getTotalRequests());
        verify(client, never()).query(any());
    }

    @Test
    void computeMetrics_assemblesEveryWidgetFromLangfuseResponses() {
        when(client.isConfigured()).thenReturn(true);
        when(client.query(anyMap())).thenAnswer(inv -> fixtureFor(inv.getArgument(0)));

        AiMetricsResponse resp = service.computeMetrics(FROM, TO);

        assertTrue(resp.isConfigured());
        assertTrue(resp.isDataAvailable());

        // §3.1 latencyByStage
        List<MetricRow> latency = resp.getLatencyByStage();
        assertEquals(2, latency.size());
        assertEquals("chat", latency.get(0).getLabel());
        assertEquals(2421.6, latency.get(0).getValue(), 0.001);

        // §3.2 requestVolume + derived totalRequests
        assertEquals(2, resp.getRequestVolume().size());
        assertEquals("chat", resp.getRequestVolume().get(0).getLabel());
        assertEquals(17L, (long) resp.getRequestVolume().get(0).getValue());
        assertEquals(25L, resp.getTotalRequests()); // 17 + 8

        // §3.3 endpointLatency
        assertEquals(1, resp.getEndpointLatency().size());
        assertEquals(2745.0, resp.getEndpointLatency().get(0).getValue(), 0.001);

        // §3.4 tokenUsageByModel + derived totalTokens
        assertEquals(1, resp.getTokenUsageByModel().size());
        assertEquals("gemini-2.5-flash-lite", resp.getTokenUsageByModel().get(0).getLabel());
        assertEquals(51379L, resp.getTotalTokens());

        // §3.5 costByModel + derived totalCost
        assertEquals(1, resp.getCostByModel().size());
        assertEquals(0.0036994, resp.getTotalCost(), 0.0000001);

        // §3.6 tokenTimeSeries
        List<TimeSeriesPoint> ts = resp.getTokenTimeSeries();
        assertEquals(1, ts.size());
        assertEquals("2026-07-17", ts.get(0).getDate());
        assertEquals(51379.0, ts.get(0).getValue(), 0.001);

        // §3.7 citationCoverageAvg
        assertEquals(0.5, resp.getCitationCoverageAvg(), 0.001);

        // §3.8 refusalCount
        assertEquals(2, resp.getRefusalCount().size());
        assertEquals("quiz", resp.getRefusalCount().get(0).getLabel());
        assertEquals(2.0, resp.getRefusalCount().get(0).getValue(), 0.001);

        // §3.9 routeDistribution (metadata cannot be grouped → N filtered counts)
        List<MetricRow> routes = resp.getRouteDistribution();
        assertEquals(6, routes.size());
        assertEquals("qa", routes.get(0).getLabel());
        assertEquals(1.0, routes.get(0).getValue(), 0.001);
        // Sum: qa1 + smalltalk2 + summary1 + guardrail_block1 + quiz2 + flashcard1 = 8
        assertEquals(8.0, routes.stream().mapToDouble(MetricRow::getValue).sum(), 0.001);

        // §3.10 emptyRetrievalCount (sum of by-endpoint counts)
        assertEquals(3L, resp.getEmptyRetrievalCount());
        assertEquals(1, resp.getEmptyRetrievalByEndpoint().size());

        // Exactly 15 Langfuse round-trips: 9 widgets + 6 route queries.
        verify(client, times(15)).query(anyMap());
    }

    @Test
    void computeMetrics_failOpenWhenClientThrowsOnEveryQuery() {
        when(client.isConfigured()).thenReturn(true);
        when(client.query(anyMap())).thenThrow(new RuntimeException("Langfuse down"));

        // Must NOT propagate — the dashboard degrades to empty, never 5xx.
        AiMetricsResponse resp = assertDoesNotThrow(() -> service.computeMetrics(FROM, TO));

        assertTrue(resp.isConfigured());      // keys present, but
        assertFalse(resp.isDataAvailable());  // every widget fail-opened to empty
        assertTrue(resp.getLatencyByStage().isEmpty());
        assertTrue(resp.getRequestVolume().isEmpty());
        // Route distribution always yields one row per known route (value 0 on failure).
        assertEquals(6, resp.getRouteDistribution().size());
        assertTrue(resp.getRouteDistribution().stream().allMatch(r -> r.getValue() == 0.0));
        assertEquals(0L, resp.getTotalRequests());
        assertEquals(0L, resp.getTotalTokens());
        assertEquals(0.0, resp.getTotalCost());
        assertEquals(0.0, resp.getCitationCoverageAvg());
    }

    @Test
    void computeMetrics_dataAvailableFalseWhenLangfuseHasNoTraces() {
        when(client.isConfigured()).thenReturn(true);
        when(client.query(anyMap())).thenReturn(objectMapper.createArrayNode()); // all empty

        AiMetricsResponse resp = service.computeMetrics(FROM, TO);

        assertTrue(resp.isConfigured());
        assertFalse(resp.isDataAvailable());
        assertEquals(0L, resp.getTotalRequests());
    }

    // --- Cache-only read (getDashboard) -----------------------------------

    @Test
    void getDashboard_cacheHit_returnsCachedPayloadWithoutQueryingLangfuse() {
        AiMetricsResponse cached = AiMetricsResponse.builder()
                .configured(true).dataAvailable(true).totalRequests(42L).build();
        Cache.ValueWrapper wrapper = mock(Cache.ValueWrapper.class);
        when(wrapper.get()).thenReturn(cached);
        when(cacheManager.getCache("aiMetrics")).thenReturn(aiMetricsCache);
        when(aiMetricsCache.get(AiMetricsServiceImpl.LATEST_KEY)).thenReturn(wrapper);

        AiMetricsResponse resp = service.getDashboard();

        assertSame(cached, resp);
        verify(client, never()).query(any());
        verify(client, never()).isConfigured();
    }

    @Test
    void getDashboard_cacheMiss_returnsEmptyFailOpenWithoutQueryingLangfuse() {
        when(cacheManager.getCache("aiMetrics")).thenReturn(aiMetricsCache);
        when(aiMetricsCache.get(AiMetricsServiceImpl.LATEST_KEY)).thenReturn(null);
        when(client.isConfigured()).thenReturn(true);

        AiMetricsResponse resp = service.getDashboard();

        assertTrue(resp.isConfigured());
        assertFalse(resp.isDataAvailable());
        // Never bursts the quota — the endpoint must never call Langfuse.
        verify(client, never()).query(any());
    }

    // --- Scheduler-driven write (refreshCache / refreshCacheIfCold) -------

    @Test
    void refreshCache_configured_fansOutAndStoresUnderLatestKey() {
        when(client.isConfigured()).thenReturn(true);
        when(client.query(anyMap())).thenAnswer(inv -> fixtureFor(inv.getArgument(0)));
        when(cacheManager.getCache("aiMetrics")).thenReturn(aiMetricsCache);

        service.refreshCache();

        // 15 Langfuse queries fanned out, then persisted under the stable cache key.
        verify(client, times(15)).query(anyMap());
        ArgumentCaptor<AiMetricsResponse> captor = ArgumentCaptor.forClass(AiMetricsResponse.class);
        verify(aiMetricsCache).put(eq(AiMetricsServiceImpl.LATEST_KEY), captor.capture());
        AiMetricsResponse stored = captor.getValue();
        assertTrue(stored.isConfigured());
        assertTrue(stored.isDataAvailable());
        assertEquals(25L, stored.getTotalRequests());
    }

    @Test
    void refreshCache_unconfigured_storesEmptyPayloadWithoutQuerying() {
        when(client.isConfigured()).thenReturn(false);
        when(cacheManager.getCache("aiMetrics")).thenReturn(aiMetricsCache);

        service.refreshCache();

        verify(client, never()).query(any());
        ArgumentCaptor<AiMetricsResponse> captor = ArgumentCaptor.forClass(AiMetricsResponse.class);
        verify(aiMetricsCache).put(eq(AiMetricsServiceImpl.LATEST_KEY), captor.capture());
        assertFalse(captor.getValue().isConfigured());
        assertFalse(captor.getValue().isDataAvailable());
    }

    @Test
    void refreshCacheIfCold_warmCache_doesNotRefresh() {
        Cache.ValueWrapper wrapper = mock(Cache.ValueWrapper.class);
        when(cacheManager.getCache("aiMetrics")).thenReturn(aiMetricsCache);
        when(aiMetricsCache.get(AiMetricsServiceImpl.LATEST_KEY)).thenReturn(wrapper);

        service.refreshCacheIfCold();

        // A warm cache surviving a restart must NOT add a 7th Langfuse burst.
        verify(client, never()).isConfigured();
        verify(client, never()).query(any());
        verify(aiMetricsCache, never()).put(any(), any());
    }

    @Test
    void refreshCacheIfCold_coldCache_triggersRefresh() {
        when(cacheManager.getCache("aiMetrics")).thenReturn(aiMetricsCache);
        when(aiMetricsCache.get(AiMetricsServiceImpl.LATEST_KEY)).thenReturn(null);
        when(client.isConfigured()).thenReturn(false); // cheap refresh path

        service.refreshCacheIfCold();

        verify(aiMetricsCache).put(eq(AiMetricsServiceImpl.LATEST_KEY), any());
    }

    // --- Fixture dispatcher -------------------------------------------------

    /** Returns the cookbook-verified response for the given query payload. */
    @SuppressWarnings("unchecked")
    private JsonNode fixtureFor(Map<String, Object> payload) {
        try {
            String json = fixtureJson(payload);
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static String fixtureJson(Map<String, Object> payload) {
        String view = (String) payload.get("view");
        List<Map<String, Object>> metrics = (List<Map<String, Object>>) payload.get("metrics");
        Map<String, Object> metric = metrics.get(0);
        String measure = (String) metric.get("measure");
        String agg = (String) metric.get("aggregation");
        List<Map<String, Object>> dims = (List<Map<String, Object>>) payload.get("dimensions");
        String dimField = dims.isEmpty() ? "" : (String) dims.get(0).get("field");
        String metaKey = metaKey(payload);
        boolean timeSeries = payload.containsKey("timeDimension");

        if ("scores-numeric".equals(view)) {
            return "[{\"name\":\"citation_coverage\",\"avg_value\":0.5}]";
        }
        if ("latency".equals(measure) && "name".equals(dimField)) {
            return "[{\"name\":\"chat\",\"p95_latency\":2421.6},{\"name\":\"ingest-index\",\"p95_latency\":2091}]";
        }
        if ("count".equals(measure) && "traceName".equals(dimField)) {
            return "[{\"traceName\":\"chat\",\"count_count\":17},{\"traceName\":\"quiz\",\"count_count\":8}]";
        }
        if ("latency".equals(measure) && "traceName".equals(dimField)) {
            return "[{\"traceName\":\"chat\",\"p95_latency\":2745}]";
        }
        if ("totalTokens".equals(measure) && "providedModelName".equals(dimField)) {
            return "[{\"providedModelName\":\"gemini-2.5-flash-lite\",\"sum_totalTokens\":51379}]";
        }
        if ("totalCost".equals(measure)) {
            return "[{\"providedModelName\":\"gemini-2.5-flash-lite\",\"sum_totalCost\":0.0036994}]";
        }
        if ("totalTokens".equals(measure) && timeSeries) {
            return "[{\"time_dimension\":\"2026-07-17\",\"sum_totalTokens\":51379}]";
        }
        if ("count".equals(measure) && "refused".equals(metaKey)) {
            return "[{\"name\":\"quiz\",\"count_count\":2},{\"name\":\"flashcard\",\"count_count\":1}]";
        }
        if ("count".equals(measure) && "empty_retrieval".equals(metaKey)) {
            return "[{\"name\":\"chat\",\"count_count\":3}]";
        }
        if ("count".equals(measure) && "route".equals(metaKey)) {
            String route = metaValue(payload, "route");
            long n = switch (route) {
                case "qa" -> 1L;
                case "smalltalk" -> 2L;
                case "summary" -> 1L;
                case "guardrail_block" -> 1L;
                case "quiz" -> 2L;
                case "flashcard" -> 1L;
                default -> 0L;
            };
            return "[{\"count_count\":" + n + "}]";
        }
        return "[]";
    }

    /** Finds the {@code key} of a metadata (stringObject) filter, or {@code null}. */
    @SuppressWarnings("unchecked")
    private static String metaKey(Map<String, Object> payload) {
        Object filters = payload.get("filters");
        if (!(filters instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        for (Object f : list) {
            Map<String, Object> filter = (Map<String, Object>) f;
            if ("metadata".equals(filter.get("column"))) {
                return (String) filter.get("key");
            }
        }
        return null;
    }

    /** Finds the {@code value} of a metadata filter by key. */
    @SuppressWarnings("unchecked")
    private static String metaValue(Map<String, Object> payload, String key) {
        Object filters = payload.get("filters");
        if (!(filters instanceof List<?> list)) {
            return null;
        }
        for (Object f : list) {
            Map<String, Object> filter = (Map<String, Object>) f;
            if ("metadata".equals(filter.get("column")) && key.equals(filter.get("key"))) {
                return String.valueOf(filter.get("value"));
            }
        }
        return null;
    }
}
