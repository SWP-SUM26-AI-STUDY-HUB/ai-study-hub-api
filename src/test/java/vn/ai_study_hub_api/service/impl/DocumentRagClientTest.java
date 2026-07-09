package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("rawtypes")
class DocumentRagClientTest {

    private static final String BASE_URL = "http://rag:8000/api/v1/rag";
    private static final String PROCESS_URL = "http://rag:8000/api/v1/rag/process";

    @Mock
    private WebClient webClient;

    @InjectMocks
    private DocumentRagClient ragClient;

    private final UUID documentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ragClient, "fastApiBaseUrl", BASE_URL);
        ReflectionTestUtils.setField(ragClient, "fastApiProcessUrl", PROCESS_URL);
    }

    @Test
    void triggerProcess_postsToProcessUrl() {
        AtomicReference<String> uri = stubPostChain();

        assertDoesNotThrow(() -> ragClient.triggerProcess(documentId, "https://files/doc.pdf"));

        assertEquals(PROCESS_URL, uri.get());
        verify(webClient).post();
    }

    @Test
    void triggerExtract_postsToExtractUrl() {
        AtomicReference<String> uri = stubPostChain();

        ragClient.triggerExtract(documentId, "https://files/doc.pdf");

        assertEquals(BASE_URL + "/extract", uri.get());
    }

    @Test
    void triggerIndex_postsToIndexUrl() {
        AtomicReference<String> uri = stubPostChain();

        ragClient.triggerIndex(documentId);

        assertEquals(BASE_URL + "/index", uri.get());
    }

    @Test
    void updateVisibility_patchesDocumentVisibilityUrl() {
        WebClient.RequestBodyUriSpec patchSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        AtomicReference<String> uri = new AtomicReference<>();

        when(webClient.patch()).thenReturn(patchSpec);
        when(patchSpec.uri(anyString())).thenAnswer(inv -> {
            uri.set(inv.getArgument(0));
            return bodySpec;
        });
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        ragClient.updateVisibility(documentId, "PUBLIC");

        assertEquals(BASE_URL + "/documents/" + documentId + "/visibility", uri.get());
        verify(webClient).patch();
    }

    @Test
    void deleteVectors_deletesDocumentUrl() {
        WebClient.RequestHeadersUriSpec deleteSpec = mock(WebClient.RequestHeadersUriSpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        AtomicReference<String> uri = new AtomicReference<>();

        when(webClient.delete()).thenReturn(deleteSpec);
        when(deleteSpec.uri(anyString())).thenAnswer(inv -> {
            uri.set(inv.getArgument(0));
            return headersSpec;
        });
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        ragClient.deleteVectors(documentId);

        assertEquals(BASE_URL + "/documents/" + documentId, uri.get());
        verify(webClient).delete();
    }

    /**
     * Key contract: the client must PROPAGATE failures so the @Async orchestrator
     * in DocumentServiceImpl owns status transitions (FAILED). It must not swallow.
     */
    @Test
    void triggerProcess_propagatesErrors() {
        stubPostChainError();

        assertThrows(RuntimeException.class, () -> ragClient.triggerProcess(documentId, "https://files/doc.pdf"));
    }

    /** Stubs the post(...) chain, returning a holder the test reads AFTER invocation. */
    private AtomicReference<String> stubPostChain() {
        WebClient.RequestBodyUriSpec bodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);
        AtomicReference<String> uri = new AtomicReference<>();

        when(webClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenAnswer(inv -> {
            uri.set(inv.getArgument(0));
            return bodySpec;
        });
        when(bodySpec.contentType(eq(MediaType.APPLICATION_JSON))).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));
        return uri;
    }

    private void stubPostChainError() {
        WebClient.RequestBodyUriSpec bodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.bodyValue(any())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.error(new RuntimeException("RAG down")));
    }
}
