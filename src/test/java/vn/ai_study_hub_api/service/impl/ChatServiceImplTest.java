package vn.ai_study_hub_api.service.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import vn.ai_study_hub_api.controller.request.ChatRequest;
import vn.ai_study_hub_api.controller.response.ChatResponse;
import vn.ai_study_hub_api.controller.response.CitationView;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.ChatSessionEntity;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.ChatMessageRepository;
import vn.ai_study_hub_api.repository.ChatSessionRepository;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.AiQuotaService;
import vn.ai_study_hub_api.service.ChatbotClient;
import vn.ai_study_hub_api.service.ChatService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AiQuotaService aiQuotaService;
    @Mock
    private ChatbotClient chatbotClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ChatService chatService;

    private UUID userId;
    private UUID documentId;
    private UserEntity mockUser;
    private DocumentEntity ownedDocument;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(chatSessionRepository, chatMessageRepository,
                documentRepository, userRepository, aiQuotaService, chatbotClient, objectMapper);

        userId = UUID.randomUUID();
        documentId = UUID.randomUUID();
        mockUser = UserEntity.builder().id(userId).build();
        ownedDocument = DocumentEntity.builder()
                .id(documentId)
                .uploader(mockUser)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .build();

        lenient().when(chatSessionRepository.save(any(ChatSessionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private ChatRequest request(String query, UUID docId, UUID sessionId) {
        ChatRequest req = new ChatRequest();
        req.setQuery(query);
        req.setDocumentId(docId);
        req.setSessionId(sessionId);
        return req;
    }

    private JsonNode qaDebug(UUID docId, int page) throws Exception {
        String json = "{\"documents\":[{\"content\":\"[Title: A, Page: " + page + "]\\nactual snippet body text\"," +
                "\"metadata\":{\"document_id\":\"" + docId + "\",\"document_title\":\"Doc A\"," +
                "\"source_file\":\"a.pdf\",\"page_number\":" + page + ",\"chunk_citation\":\"Page: " + page + "\"}}]}";
        return objectMapper.readTree(json);
    }

    @Test
    void chat_newSession_savesBothMessages_andCallsChatbotOnce() throws Exception {
        when(aiQuotaService.checkAndIncrement(userId)).thenReturn(new AiQuotaService.QuotaInfo(1, 15, 14));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(ownedDocument));
        when(chatbotClient.chat(eq("What is X?"), eq(userId), eq(documentId), anyList()))
                .thenReturn(new ChatbotClient.ChatbotResponse(true, "ok",
                        new ChatbotClient.ResponseData("answer text", qaDebug(documentId, 3))));

        ChatResponse response = chatService.chat(request("What is X?", documentId, null), userId);

        assertNotNull(response.getSessionId());
        assertEquals("answer text", response.getAnswer());
        assertEquals(14, response.getRemainingRequests());
        assertEquals(15, response.getDailyLimit());

        assertEquals(1, response.getCitations().size());
        CitationView citation = response.getCitations().get(0);
        assertEquals(documentId, citation.getDocumentId());
        assertEquals(1, citation.getId());
        assertEquals("a.pdf", citation.getFileName());
        assertEquals(3, citation.getPageNumber());
        assertEquals("actual snippet body text", citation.getSnippet());

        verify(chatMessageRepository, times(2)).save(any());
        verify(chatbotClient, times(1)).chat(eq("What is X?"), eq(userId), eq(documentId), anyList());
    }

    @Test
    void chat_multipleDocuments_assignsSequentialIds() throws Exception {
        when(aiQuotaService.checkAndIncrement(userId)).thenReturn(new AiQuotaService.QuotaInfo(1, 15, 14));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(ownedDocument));

        StringBuilder docs = new StringBuilder("[");
        for (int i = 1; i <= 3; i++) {
            if (i > 1) {
                docs.append(",");
            }
            docs.append("{\"content\":\"[Title: A, Page: ").append(i).append("]\\nbody ").append(i).append("\",")
                    .append("\"metadata\":{\"document_id\":\"").append(documentId)
                    .append("\",\"source_file\":\"a.pdf\",\"page_number\":").append(i).append("}}");
        }
        docs.append("]");
        JsonNode debug = objectMapper.readTree("{\"documents\":" + docs + "}");

        when(chatbotClient.chat(eq("q"), eq(userId), eq(documentId), anyList()))
                .thenReturn(new ChatbotClient.ChatbotResponse(true, "ok",
                        new ChatbotClient.ResponseData("answer [1][3]", debug)));

        ChatResponse response = chatService.chat(request("q", documentId, null), userId);

        assertEquals(3, response.getCitations().size());
        assertEquals(1, response.getCitations().get(0).getId());
        assertEquals(2, response.getCitations().get(1).getId());
        assertEquals(3, response.getCitations().get(2).getId());
    }

    @Test
    void chat_documentNotAccessible_forbidden() {
        UUID otherUser = UUID.randomUUID();
        DocumentEntity foreignDoc = DocumentEntity.builder()
                .id(documentId)
                .uploader(UserEntity.builder().id(otherUser).build())
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .build();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(foreignDoc));

        AppException ex = assertThrows(AppException.class,
                () -> chatService.chat(request("q", documentId, null), userId));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(chatbotClient, never()).chat(anyString(), any(), any(), anyList());
    }

    @Test
    void chat_documentStillProcessing_returnsNotReadyMessage_noQuotaNoChatbot() {
        DocumentEntity processingDoc = DocumentEntity.builder()
                .id(documentId)
                .uploader(mockUser)
                .status(DocumentStatus.PROCESSING)
                .visibility(DocumentVisibility.PRIVATE)
                .build();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(processingDoc));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(aiQuotaService.getUsage(userId)).thenReturn(new AiQuotaService.QuotaInfo(3, 15, 12));

        ChatResponse response = chatService.chat(request("What is X?", documentId, null), userId);

        assertEquals(
                "Tài liệu đang được xử lý, vui lòng đợi trong giây lát rồi gửi lại câu hỏi nhé.",
                response.getAnswer());
        assertTrue(response.getCitations().isEmpty());
        assertNotNull(response.getSessionId());
        assertEquals(12, response.getRemainingRequests());
        assertEquals(15, response.getDailyLimit());

        // Still-processing doc: no LLM/RAG call and no quota charged.
        verify(chatbotClient, never()).chat(anyString(), any(), any(), anyList());
        verify(aiQuotaService, never()).checkAndIncrement(any());
        verify(chatMessageRepository, times(2)).save(any());
    }

    @Test
    void chat_documentFailed_returnsFailureMessage() {
        DocumentEntity failedDoc = DocumentEntity.builder()
                .id(documentId)
                .uploader(mockUser)
                .status(DocumentStatus.FAILED)
                .visibility(DocumentVisibility.PRIVATE)
                .build();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(failedDoc));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(aiQuotaService.getUsage(userId)).thenReturn(new AiQuotaService.QuotaInfo(0, 15, 15));

        ChatResponse response = chatService.chat(request("q", documentId, null), userId);

        assertEquals(
                "Tài liệu xử lý thất bại nên chưa thể trò chuyện. Vui lòng tải lại tài liệu rồi thử lại.",
                response.getAnswer());
        verify(chatbotClient, never()).chat(anyString(), any(), any(), anyList());
        verify(aiQuotaService, never()).checkAndIncrement(any());
    }

    @Test
    void chat_sessionNotOwned_notFound() {
        UUID sessionId = UUID.randomUUID();
        when(aiQuotaService.checkAndIncrement(userId)).thenReturn(new AiQuotaService.QuotaInfo(1, 15, 14));
        when(chatSessionRepository.findByIdAndUserId(sessionId, userId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> chatService.chat(request("q", null, sessionId), userId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(chatbotClient, never()).chat(anyString(), any(), any(), anyList());
    }

    @Test
    void chat_summaryBranch_emptyDebug_returnsNoCitations() {
        when(aiQuotaService.checkAndIncrement(userId)).thenReturn(new AiQuotaService.QuotaInfo(2, 15, 13));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(ownedDocument));
        when(chatbotClient.chat(eq("Summarize this document"), eq(userId), eq(documentId), anyList()))
                .thenReturn(new ChatbotClient.ChatbotResponse(true, "ok",
                        new ChatbotClient.ResponseData("a summary", null)));

        ChatResponse response = chatService.chat(request("Summarize this document", documentId, null), userId);

        assertEquals("a summary", response.getAnswer());
        assertTrue(response.getCitations().isEmpty());
    }

    @Test
    void chat_blankQuery_badRequest() {
        AppException ex = assertThrows(AppException.class,
                () -> chatService.chat(request("   ", null, null), userId));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(aiQuotaService);
        verifyNoInteractions(chatbotClient);
    }

    @Test
    void renameSession_blankTitle_badRequest() {
        AppException ex = assertThrows(AppException.class,
                () -> chatService.renameSession(UUID.randomUUID(), "  ", userId));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(chatSessionRepository);
    }
}
