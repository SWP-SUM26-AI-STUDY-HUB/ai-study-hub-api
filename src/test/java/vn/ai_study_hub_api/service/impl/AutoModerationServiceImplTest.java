package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.repository.DocumentChunkRepository;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.projection.ChunkContentProjection;
import vn.ai_study_hub_api.service.DocumentService;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AutoModerationServiceImplTest {

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private WebClient webClient;

    @Mock
    private DocumentImageExtractor imageExtractor;

    @Mock
    private DocumentService documentService;
    @Mock
    private LangfuseIngestionClient langfuseIngestion;

    @InjectMocks
    private AutoModerationServiceImpl autoModerationService;

    private UUID documentId;
    private DocumentEntity mockDocument;

    @BeforeEach
    void setUp() {
        documentId = UUID.randomUUID();
        mockDocument = DocumentEntity.builder()
                .id(documentId)
                .status(DocumentStatus.PENDING)
                .build();

        // "mock_api_key" is NOT the "mock_key" sentinel, so moderation proceeds to the (mocked) OpenAI call.
        ReflectionTestUtils.setField(autoModerationService, "openAiApiKey", "mock_api_key");
        ReflectionTestUtils.setField(autoModerationService, "moderationUrl", "https://api.openai.com/v1/moderations");
        ReflectionTestUtils.setField(autoModerationService, "documentService", documentService);
        // Image moderation defaults OFF here — existing text-only cases are unaffected. Tests that
        // exercise the image path flip imageModerationEnabled=true explicitly.
        ReflectionTestUtils.setField(autoModerationService, "imageModerationEnabled", false);
        ReflectionTestUtils.setField(autoModerationService, "imageMaxPerDoc", 30);
        ReflectionTestUtils.setField(autoModerationService, "imageBatchSize", 5);
    }

    @Test
    void process_DocumentNotFound() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        autoModerationService.process(documentId);

        verify(chunkRepository, never()).findChunkContentsByDocumentId(any());
        verify(webClient, never()).post();
    }

    @Test
    void process_StatusNotPending() {
        mockDocument.setStatus(DocumentStatus.COMPLETED);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));

        autoModerationService.process(documentId);

        verify(chunkRepository, never()).findChunkContentsByDocumentId(any());
    }

    @Test
    void process_NoChunks() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(Collections.emptyList());

        autoModerationService.process(documentId);

        verify(webClient, never()).post();
    }

    @Test
    void process_GreenZone_AutoApprove() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        ChunkContentProjection mockChunk = mock(ChunkContentProjection.class);
        when(mockChunk.getContent()).thenReturn("This is clean content");
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(List.of(mockChunk));

        AutoModerationServiceImpl.ModerationResponse mockResponse = new AutoModerationServiceImpl.ModerationResponse();
        AutoModerationServiceImpl.ModerationResult mockResult = new AutoModerationServiceImpl.ModerationResult();
        mockResult.setFlagged(false);
        mockResult.setCategoryScores(Map.of("hate", 0.01, "violence", 0.05));
        mockResponse.setResults(List.of(mockResult));
        setupMockWebClient(Mono.just(mockResponse));

        autoModerationService.process(documentId);

        verify(documentService, times(1)).approveDocument(documentId);
        verify(documentService, never()).rejectDocument(any(), anyString());
    }

    @Test
    void process_RedZone_AutoReject() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        ChunkContentProjection mockChunk = mock(ChunkContentProjection.class);
        when(mockChunk.getContent()).thenReturn("This contains violence content");
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(List.of(mockChunk));

        AutoModerationServiceImpl.ModerationResponse mockResponse = new AutoModerationServiceImpl.ModerationResponse();
        AutoModerationServiceImpl.ModerationResult mockResult = new AutoModerationServiceImpl.ModerationResult();
        mockResult.setFlagged(true);
        mockResult.setCategoryScores(Map.of("hate", 0.1, "violence", 0.85));
        mockResponse.setResults(List.of(mockResult));
        setupMockWebClient(Mono.just(mockResponse));

        autoModerationService.process(documentId);

        verify(documentService, never()).approveDocument(any());
        verify(documentService, times(1)).rejectDocument(eq(documentId), contains("violence"));
    }

    @Test
    void process_YellowZone_PendingReview() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        ChunkContentProjection mockChunk = mock(ChunkContentProjection.class);
        when(mockChunk.getContent()).thenReturn("This content is questionable");
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(List.of(mockChunk));

        AutoModerationServiceImpl.ModerationResponse mockResponse = new AutoModerationServiceImpl.ModerationResponse();
        AutoModerationServiceImpl.ModerationResult mockResult = new AutoModerationServiceImpl.ModerationResult();
        mockResult.setFlagged(false);
        mockResult.setCategoryScores(Map.of("hate", 0.55, "violence", 0.1));
        mockResponse.setResults(List.of(mockResult));
        setupMockWebClient(Mono.just(mockResponse));

        autoModerationService.process(documentId);

        verify(documentService, never()).approveDocument(any());
        verify(documentService, never()).rejectDocument(any(), anyString());
    }

    /**
     * NEW semantics: failures PROPAGATE (no swallow) so the stream consumer can leave the message
     * unacked and retry it — the fix for the old silent-PENDING bug.
     */
    @Test
    void process_OpenAiError_PropagatesException() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        ChunkContentProjection mockChunk = mock(ChunkContentProjection.class);
        when(mockChunk.getContent()).thenReturn("content");
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(List.of(mockChunk));

        setupMockWebClient(Mono.error(new RuntimeException("OpenAI down")));

        assertThrows(RuntimeException.class, () -> autoModerationService.process(documentId));

        verify(documentService, never()).approveDocument(any());
        verify(documentService, never()).rejectDocument(any(), anyString());
    }

    // --- Image moderation ---

    /**
     * Policy: any failure in the image-moderation flow defers the document to manual review (PENDING)
     * rather than auto-approving on text alone. Text here is clean (would normally approve), but image
     * extraction fails -> neither approve nor reject fires.
     */
    @Test
    void process_ImageModerationFailure_LeavesPendingForManualReview() {
        mockDocument.setFileUrl("u/d.pdf");
        mockDocument.setFileType(".pdf");
        ReflectionTestUtils.setField(autoModerationService, "imageModerationEnabled", true);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        ChunkContentProjection mockChunk = mock(ChunkContentProjection.class);
        when(mockChunk.getContent()).thenReturn("clean text");
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(List.of(mockChunk));

        setupMockWebClient(Mono.just(lowScoreResponse()));
        when(imageExtractor.extract(eq("u/d.pdf"), eq(".pdf"), anyInt()))
                .thenThrow(new RuntimeException("S3 down"));

        autoModerationService.process(documentId);

        verify(documentService, never()).approveDocument(any());
        verify(documentService, never()).rejectDocument(any(), anyString());
        verify(imageExtractor).extract(eq("u/d.pdf"), eq(".pdf"), eq(30));
    }

    @Test
    void process_ImageViolation_AutoReject() {
        mockDocument.setFileUrl("u/d.docx");
        mockDocument.setFileType(".docx");
        ReflectionTestUtils.setField(autoModerationService, "imageModerationEnabled", true);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        ChunkContentProjection mockChunk = mock(ChunkContentProjection.class);
        when(mockChunk.getContent()).thenReturn("text");
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(List.of(mockChunk));

        setupMockWebClient(Mono.just(highScoreResponse()));
        when(imageExtractor.extract(any(), any(), anyInt())).thenReturn(
                List.of(new DocumentImageExtractor.ExtractedImage(new byte[]{1, 2, 3}, "image/jpeg")));

        autoModerationService.process(documentId);

        verify(documentService, times(1)).rejectDocument(eq(documentId), contains("violence"));
        verify(imageExtractor).extract(eq("u/d.docx"), eq(".docx"), eq(30));
    }

    @Test
    void process_ImagesClean_AutoApprove() {
        mockDocument.setFileUrl("u/d.docx");
        mockDocument.setFileType(".docx");
        ReflectionTestUtils.setField(autoModerationService, "imageModerationEnabled", true);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        ChunkContentProjection mockChunk = mock(ChunkContentProjection.class);
        when(mockChunk.getContent()).thenReturn("clean text");
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(List.of(mockChunk));

        setupMockWebClient(Mono.just(lowScoreResponse()));
        when(imageExtractor.extract(any(), any(), anyInt())).thenReturn(
                List.of(new DocumentImageExtractor.ExtractedImage(new byte[]{1, 2, 3}, "image/jpeg")));

        autoModerationService.process(documentId);

        verify(documentService, times(1)).approveDocument(documentId);
        verify(imageExtractor).extract(eq("u/d.docx"), eq(".docx"), eq(30));
    }

    @Test
    void process_ImageDisabled_SkipsExtraction() {
        // imageModerationEnabled stays false (from setUp).
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        ChunkContentProjection mockChunk = mock(ChunkContentProjection.class);
        when(mockChunk.getContent()).thenReturn("clean text");
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(List.of(mockChunk));

        setupMockWebClient(Mono.just(lowScoreResponse()));

        autoModerationService.process(documentId);

        verify(documentService, times(1)).approveDocument(documentId);
        verify(imageExtractor, never()).extract(any(), any(), anyInt());
    }

    // --- Langfuse ingestion (fail-open) ---

    @Test
    void process_FlushesLangfuseTraceWithDecision() {
        ReflectionTestUtils.setField(autoModerationService, "langfuseEnabled", true);
        when(langfuseIngestion.isConfigured()).thenReturn(true);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        ChunkContentProjection mockChunk = mock(ChunkContentProjection.class);
        when(mockChunk.getContent()).thenReturn("clean content");
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(List.of(mockChunk));

        setupMockWebClient(Mono.just(lowScoreResponse()));

        autoModerationService.process(documentId);

        verify(documentService, times(1)).approveDocument(documentId);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> captor = ArgumentCaptor.forClass(List.class);
        verify(langfuseIngestion, times(1)).ingest(captor.capture());

        List<Map<String, Object>> batch = captor.getValue();
        // 1 trace-create + 1 generation-create (single text batch).
        assertEquals(2, batch.size());
        Map<String, Object> traceEvent = batch.get(0);
        assertEquals("trace-create", traceEvent.get("type"));
        Map<?, ?> traceBody = (Map<?, ?>) traceEvent.get("body");
        assertEquals("moderation", traceBody.get("name"));
        assertEquals("APPROVED", ((Map<?, ?>) traceBody.get("metadata")).get("decision"));
        assertEquals("generation-create", batch.get(1).get("type"));
    }

    @Test
    void process_LangfuseFailureIsFailOpen_DoesNotBreakModeration() {
        ReflectionTestUtils.setField(autoModerationService, "langfuseEnabled", true);
        when(langfuseIngestion.isConfigured()).thenReturn(true);
        // Ingest throws — the service must swallow it (observability never blocks moderation).
        doThrow(new RuntimeException("Langfuse ingest down")).when(langfuseIngestion).ingest(any());
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        ChunkContentProjection mockChunk = mock(ChunkContentProjection.class);
        when(mockChunk.getContent()).thenReturn("clean content");
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(List.of(mockChunk));

        setupMockWebClient(Mono.just(lowScoreResponse()));

        assertDoesNotThrow(() -> autoModerationService.process(documentId));
        verify(documentService, times(1)).approveDocument(documentId);
    }

    private AutoModerationServiceImpl.ModerationResponse lowScoreResponse() {
        AutoModerationServiceImpl.ModerationResult r = new AutoModerationServiceImpl.ModerationResult();
        r.setCategoryScores(Map.of("hate", 0.01, "violence", 0.02));
        AutoModerationServiceImpl.ModerationResponse resp = new AutoModerationServiceImpl.ModerationResponse();
        resp.setResults(List.of(r));
        return resp;
    }

    private AutoModerationServiceImpl.ModerationResponse highScoreResponse() {
        AutoModerationServiceImpl.ModerationResult r = new AutoModerationServiceImpl.ModerationResult();
        r.setFlagged(true);
        r.setCategoryScores(Map.of("hate", 0.1, "violence", 0.85));
        AutoModerationServiceImpl.ModerationResponse resp = new AutoModerationServiceImpl.ModerationResponse();
        resp.setResults(List.of(r));
        return resp;
    }

    @SuppressWarnings("unchecked")
    private void setupMockWebClient(Mono<AutoModerationServiceImpl.ModerationResponse> responseMono) {
        WebClient.RequestBodyUriSpec requestBodyUriSpec = mock(WebClient.RequestBodyUriSpec.class);
        WebClient.RequestBodySpec requestBodySpec = mock(WebClient.RequestBodySpec.class);
        WebClient.RequestHeadersSpec requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(Class.class))).thenReturn(responseMono);
    }
}
