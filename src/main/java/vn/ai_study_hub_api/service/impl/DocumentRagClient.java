package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Thin, blocking HTTP client for the FastAPI RAG service.
 *
 * <p>Centralizes every RAG integration call ({@code /process},
 * {@code /extract}, {@code /index}, {@code /documents/{id}/visibility},
 * {@code /documents/{id}}) that used to be scattered across
 * {@code DocumentServiceImpl}. Each call blocks with a 10s timeout and
 * <strong>propagates</strong> exceptions; the owning {@code @Async} state
 * machine in {@code DocumentServiceImpl} decides status transitions
 * (COMPLETED / FAILED) and logging. Extracted from the former god class
 * (Single Responsibility: this component owns <em>RAG transport</em> only).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentRagClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;

    @Value("${fastapi.rag-process-url}")
    private String fastApiProcessUrl;

    @Value("${fastapi.base-url}")
    private String fastApiBaseUrl;

    /** PRIVATE flow: extract + index + summary in one background job. */
    public void triggerProcess(UUID documentId, String fileUrl) {
        post(fastApiProcessUrl, Map.of("document_id", documentId.toString(), "file_url", fileUrl));
        log.info("FastAPI /process triggered for document ID: {}", documentId);
    }

    /** PUBLIC flow: extract only — chunks stored with embedding deferred until approval. */
    public void triggerExtract(UUID documentId, String fileUrl) {
        post(fastApiBaseUrl + "/extract", Map.of("document_id", documentId.toString(), "file_url", fileUrl));
        log.info("RAG /extract triggered for public document ID: {}", documentId);
    }

    /** Embed pending chunks for a document (idempotent). Used on approve / PUBLIC-&gt;PRIVATE. */
    public void triggerIndex(UUID documentId) {
        post(fastApiBaseUrl + "/index", Map.of("document_id", documentId.toString()));
        log.info("RAG /index triggered for document ID: {}", documentId);
    }

    /** Stamp visibility into chunk metadata (metadata only; retrieval is gated by this API). */
    public void updateVisibility(UUID documentId, String visibility) {
        webClient.patch()
                .uri(fastApiBaseUrl + "/documents/" + documentId + "/visibility")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("visibility", visibility))
                .retrieve()
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
        log.info("Updated RAG visibility for document ID: {} -> {}", documentId, visibility);
    }

    /** Purge all chunks + parent docs for a document (reject / delete flow). */
    public void deleteVectors(UUID documentId) {
        webClient.delete()
                .uri(fastApiBaseUrl + "/documents/" + documentId)
                .retrieve()
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
        log.info("Deleted vectors in RAG for document ID: {}", documentId);
    }

    private void post(String uri, Map<String, String> payload) {
        webClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .timeout(TIMEOUT)
                .block();
    }
}
