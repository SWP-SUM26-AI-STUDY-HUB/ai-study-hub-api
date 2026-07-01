package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private DocumentService documentService;

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

        ReflectionTestUtils.setField(autoModerationService, "openAiApiKey", "mock_api_key");
        ReflectionTestUtils.setField(autoModerationService, "moderationUrl", "https://api.openai.com/v1/moderations");
        ReflectionTestUtils.setField(autoModerationService, "documentService", documentService);
    }

    @Test
    void moderateDocumentAsync_DocumentNotFound() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.empty());

        autoModerationService.moderateDocumentAsync(documentId);

        verify(chunkRepository, never()).findChunkContentsByDocumentId(any());
        verify(webClient, never()).post();
    }

    @Test
    void moderateDocumentAsync_StatusNotPending() {
        mockDocument.setStatus(DocumentStatus.COMPLETED);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));

        autoModerationService.moderateDocumentAsync(documentId);

        verify(chunkRepository, never()).findChunkContentsByDocumentId(any());
    }

    @Test
    void moderateDocumentAsync_NoChunks() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(Collections.emptyList());

        autoModerationService.moderateDocumentAsync(documentId);

        verify(webClient, never()).post();
    }

    @Test
    void moderateDocumentAsync_GreenZone_AutoApprove() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));

        ChunkContentProjection mockChunk = mock(ChunkContentProjection.class);
        when(mockChunk.getContent()).thenReturn("This is clean content");
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(List.of(mockChunk));

        // Mock OpenAI WebClient call
        AutoModerationServiceImpl.ModerationResponse mockResponse = new AutoModerationServiceImpl.ModerationResponse();
        AutoModerationServiceImpl.ModerationResult mockResult = new AutoModerationServiceImpl.ModerationResult();
        mockResult.setFlagged(false);
        mockResult.setCategoryScores(Map.of("hate", 0.01, "violence", 0.05));
        mockResponse.setResults(List.of(mockResult));

        setupMockWebClient(mockResponse);

        autoModerationService.moderateDocumentAsync(documentId);

        verify(documentService, times(1)).approveDocument(documentId);
        verify(documentService, never()).rejectDocument(any(), anyString());
    }

    @Test
    void moderateDocumentAsync_RedZone_AutoReject() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));

        ChunkContentProjection mockChunk = mock(ChunkContentProjection.class);
        when(mockChunk.getContent()).thenReturn("This contains violence content");
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(List.of(mockChunk));

        // Mock OpenAI WebClient call
        AutoModerationServiceImpl.ModerationResponse mockResponse = new AutoModerationServiceImpl.ModerationResponse();
        AutoModerationServiceImpl.ModerationResult mockResult = new AutoModerationServiceImpl.ModerationResult();
        mockResult.setFlagged(true);
        mockResult.setCategoryScores(Map.of("hate", 0.1, "violence", 0.85));
        mockResponse.setResults(List.of(mockResult));

        setupMockWebClient(mockResponse);

        autoModerationService.moderateDocumentAsync(documentId);

        verify(documentService, never()).approveDocument(any());
        verify(documentService, times(1)).rejectDocument(eq(documentId), contains("violence"));
    }

    @Test
    void moderateDocumentAsync_YellowZone_PendingReview() {
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(mockDocument));

        ChunkContentProjection mockChunk = mock(ChunkContentProjection.class);
        when(mockChunk.getContent()).thenReturn("This content is questionable");
        when(chunkRepository.findChunkContentsByDocumentId(documentId)).thenReturn(List.of(mockChunk));

        // Mock OpenAI WebClient call
        AutoModerationServiceImpl.ModerationResponse mockResponse = new AutoModerationServiceImpl.ModerationResponse();
        AutoModerationServiceImpl.ModerationResult mockResult = new AutoModerationServiceImpl.ModerationResult();
        mockResult.setFlagged(false);
        mockResult.setCategoryScores(Map.of("hate", 0.55, "violence", 0.1));
        mockResponse.setResults(List.of(mockResult));

        setupMockWebClient(mockResponse);

        autoModerationService.moderateDocumentAsync(documentId);

        // Yellow zone: should not call approve or reject, status remains PENDING
        verify(documentService, never()).approveDocument(any());
        verify(documentService, never()).rejectDocument(any(), anyString());
    }

    @SuppressWarnings("unchecked")
    private void setupMockWebClient(AutoModerationServiceImpl.ModerationResponse mockResponse) {
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
        when(responseSpec.bodyToMono(any(Class.class))).thenReturn(Mono.just(mockResponse));
    }
}
