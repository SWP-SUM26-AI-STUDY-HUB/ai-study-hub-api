package vn.ai_study_hub_api.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.ai_study_hub_api.controller.request.StudyMaterialRequest;
import vn.ai_study_hub_api.controller.response.FlashcardGenerateResponse;
import vn.ai_study_hub_api.controller.response.FlashcardItemResponse;
import vn.ai_study_hub_api.controller.response.QuizGenerateResponse;
import vn.ai_study_hub_api.controller.response.QuizQuestionResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.ChatMessageEntity;
import vn.ai_study_hub_api.model.ChatSessionEntity;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.model.MessageSender;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.ChatMessageRepository;
import vn.ai_study_hub_api.repository.ChatSessionRepository;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.AiQuotaService;
import vn.ai_study_hub_api.service.StudyMaterialClient;
import vn.ai_study_hub_api.service.StudyMaterialService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StudyMaterialServiceImpl}, focused on the chat-session persistence
 * contract: each generation is recorded as a user + bot message pair inside a (new or reused)
 * chat session so it shows up in chat history. Mirrors the style of {@code ChatServiceImplTest}:
 * pure Mockito, no Spring slices, ObjectMapper is real.
 */
@ExtendWith(MockitoExtension.class)
class StudyMaterialServiceImplTest {

    @Mock
    private AiQuotaService aiQuotaService;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private StudyMaterialClient studyMaterialClient;
    @Mock
    private ChatSessionRepository chatSessionRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StudyMaterialService studyMaterialService;

    private UUID userId;
    private UUID documentId;
    private UserEntity mockUser;
    private DocumentEntity ownedDocument;

    @BeforeEach
    void setUp() {
        studyMaterialService = new StudyMaterialServiceImpl(aiQuotaService, documentRepository,
                studyMaterialClient, chatSessionRepository, chatMessageRepository, userRepository, objectMapper);

        userId = UUID.randomUUID();
        documentId = UUID.randomUUID();
        mockUser = UserEntity.builder().id(userId).build();
        ownedDocument = DocumentEntity.builder()
                .id(documentId)
                .uploader(mockUser)
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE)
                .title("Machine Learning Basics")
                .build();

        // save() echoes the entity back, the same way ChatServiceImplTest stubs it.
        lenient().when(chatSessionRepository.save(any(ChatSessionEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private StudyMaterialRequest request(UUID docId, Integer count, String focus, UUID sessionId) {
        StudyMaterialRequest req = new StudyMaterialRequest();
        req.setDocumentId(docId);
        req.setCount(count);
        req.setFocus(focus);
        req.setSessionId(sessionId);
        return req;
    }

    private List<QuizQuestionResponse> sampleQuiz() {
        return List.of(QuizQuestionResponse.builder()
                .question("What is supervised learning?")
                .options(List.of("Labeled data", "Unlabeled data", "Reinforcement", "None"))
                .correctIndex(0)
                .explanation("Uses labeled examples.")
                .build());
    }

    private List<FlashcardItemResponse> sampleFlashcards() {
        return List.of(FlashcardItemResponse.builder()
                .term("Overfitting")
                .definition("Poor generalization.")
                .build());
    }

    // ------------------------------------------------------------------
    // Persistence: new session
    // ------------------------------------------------------------------

    @Test
    void generateQuiz_newSession_createsSession_persistsUserAndBotMessages_returnsSessionId() {
        when(aiQuotaService.checkAndIncrement(userId)).thenReturn(new AiQuotaService.QuotaInfo(1, 10, 9));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(ownedDocument));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(studyMaterialClient.generateQuiz(eq(documentId), eq(10), eq(null)))
                .thenReturn(new StudyMaterialClient.QuizResult(false, null, sampleQuiz()));

        QuizGenerateResponse response = studyMaterialService
                .generateQuiz(request(documentId, 10, null, null), userId).body();

        // A new session is created (no sessionId in the request) and its id is returned.
        ArgumentCaptor<ChatSessionEntity> sessionCaptor = ArgumentCaptor.forClass(ChatSessionEntity.class);
        verify(chatSessionRepository).save(sessionCaptor.capture());
        ChatSessionEntity savedSession = sessionCaptor.getValue();
        assertNotNull(savedSession.getId());
        assertEquals(savedSession.getId(), response.getSessionId());
        assertTrue(savedSession.getTitle().startsWith("Quiz · "), "session title should be prefixed with 'Quiz · '");
        // The document is attached to the session for history context.
        assertEquals(1, savedSession.getDocuments().size());
        assertEquals(documentId, savedSession.getDocuments().get(0).getId());

        // Exactly two messages are persisted: a USER request turn and a BOT result turn.
        ArgumentCaptor<ChatMessageEntity> msgCaptor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(chatMessageRepository, times(2)).save(msgCaptor.capture());
        List<ChatMessageEntity> saved = msgCaptor.getAllValues();
        ChatMessageEntity userMsg = saved.stream().filter(m -> m.getSender() == MessageSender.USER).findFirst().orElseThrow();
        ChatMessageEntity botMsg = saved.stream().filter(m -> m.getSender() == MessageSender.BOT).findFirst().orElseThrow();

        assertEquals(savedSession.getId(), userMsg.getSession().getId());
        assertTrue(userMsg.getContent().contains("Generate"), "user message should describe the request");
        assertNull(userMsg.getMaterialPayload(), "user message carries no material payload");

        // Bot message carries the structured quiz payload, not the raw items as text.
        assertNotNull(botMsg.getMaterialPayload());
        assertPayload(botMsg.getMaterialPayload(), "QUIZ", 1);

        // Quota fields are threaded through unchanged.
        assertEquals(9, response.getRemainingRequests());
        assertEquals(10, response.getDailyLimit());
        assertEquals(1, response.getQuiz().size());
    }

    @Test
    void generateFlashcard_newSession_persistsFlashcardPayload() {
        when(aiQuotaService.checkAndIncrement(userId)).thenReturn(new AiQuotaService.QuotaInfo(1, 10, 8));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(ownedDocument));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(studyMaterialClient.generateFlashcard(eq(documentId), eq(15), eq("CNN")))
                .thenReturn(new StudyMaterialClient.FlashcardResult(false, null, sampleFlashcards()));

        FlashcardGenerateResponse response = studyMaterialService
                .generateFlashcard(request(documentId, 15, "CNN", null), userId).body();

        ArgumentCaptor<ChatMessageEntity> msgCaptor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(chatMessageRepository, times(2)).save(msgCaptor.capture());
        ChatMessageEntity botMsg = msgCaptor.getAllValues().stream()
                .filter(m -> m.getSender() == MessageSender.BOT).findFirst().orElseThrow();
        assertPayload(botMsg.getMaterialPayload(), "FLASHCARD", 1);

        assertNotNull(response.getSessionId());
        assertEquals(1, response.getFlashcards().size());
    }

    // ------------------------------------------------------------------
    // Persistence: reuse existing session
    // ------------------------------------------------------------------

    @Test
    void generateQuiz_existingSession_reusesIt_doesNotCreateNewSession() {
        UUID existingSessionId = UUID.randomUUID();
        ChatSessionEntity existing = ChatSessionEntity.builder()
                .id(existingSessionId)
                .user(mockUser)
                .title("prior chat")
                .documents(new ArrayList<>())
                .build();

        when(aiQuotaService.checkAndIncrement(userId)).thenReturn(new AiQuotaService.QuotaInfo(1, 10, 7));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(ownedDocument));
        when(chatSessionRepository.findByIdAndUserId(existingSessionId, userId)).thenReturn(Optional.of(existing));
        when(studyMaterialClient.generateQuiz(eq(documentId), eq(10), eq(null)))
                .thenReturn(new StudyMaterialClient.QuizResult(false, null, sampleQuiz()));

        QuizGenerateResponse response = studyMaterialService
                .generateQuiz(request(documentId, 10, null, existingSessionId), userId).body();

        // The provided session id is reused — no user lookup, and the returned id matches.
        assertEquals(existingSessionId, response.getSessionId());
        verify(userRepository, never()).findById(any());
        verify(chatSessionRepository).save(existing);
        // The document is still attached, and both turns are appended.
        assertTrue(existing.getDocuments().stream().anyMatch(d -> d.getId().equals(documentId)));
        verify(chatMessageRepository, times(2)).save(any(ChatMessageEntity.class));
    }

    // ------------------------------------------------------------------
    // Refusal still persists (and still consumes quota)
    // ------------------------------------------------------------------

    @Test
    void generateQuiz_refusal_persistsReasonAsContent_andNoPayload_butStillSavesBotMessage() {
        when(aiQuotaService.checkAndIncrement(userId)).thenReturn(new AiQuotaService.QuotaInfo(1, 10, 5));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(ownedDocument));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        // RAG refused: empty questions + a reason. Quota was already incremented before the call.
        when(studyMaterialClient.generateQuiz(eq(documentId), eq(5), eq(null)))
                .thenReturn(new StudyMaterialClient.QuizResult(true, "Document too short.", List.of()));

        StudyMaterialService.Outcome<QuizGenerateResponse> outcome =
                studyMaterialService.generateQuiz(request(documentId, 5, null, null), userId);

        assertTrue(outcome.refused(), "outcome should be flagged as a refusal");
        assertEquals("Document too short.", outcome.message());

        ArgumentCaptor<ChatMessageEntity> msgCaptor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(chatMessageRepository, times(2)).save(msgCaptor.capture());
        ChatMessageEntity botMsg = msgCaptor.getAllValues().stream()
                .filter(m -> m.getSender() == MessageSender.BOT).findFirst().orElseThrow();
        // On refusal the reason becomes the textual content and no structured payload is stored.
        assertEquals("Document too short.", botMsg.getContent());
        assertNull(botMsg.getMaterialPayload());
        // The session is still created so the failed attempt is visible in history.
        assertNotNull(outcome.body().getSessionId());
    }

    // ------------------------------------------------------------------
    // Document not ready (processing / failed): refuse WITHOUT charging quota or calling RAG
    // ------------------------------------------------------------------

    @Test
    void generateQuiz_documentStillProcessing_returnsEmptyList_noQuotaNoRag() {
        ownedDocument.setStatus(DocumentStatus.PROCESSING);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(ownedDocument));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(aiQuotaService.getUsage(userId)).thenReturn(new AiQuotaService.QuotaInfo(3, 10, 7));

        StudyMaterialService.Outcome<QuizGenerateResponse> outcome =
                studyMaterialService.generateQuiz(request(documentId, 10, null, null), userId);

        assertTrue(outcome.refused(), "not-ready should surface as a refusal");
        assertEquals("Tài liệu đang được xử lý, vui lòng đợi trong giây lát rồi thử lại.",
                outcome.message());
        assertTrue(outcome.body().getQuiz().isEmpty());
        assertNotNull(outcome.body().getSessionId());
        assertEquals(7, outcome.body().getRemainingRequests());
        assertEquals(10, outcome.body().getDailyLimit());

        // No RAG call and no quota charged for a still-processing document.
        verify(studyMaterialClient, never()).generateQuiz(any(), any(), any());
        verify(aiQuotaService, never()).checkAndIncrement(any());
        verify(chatMessageRepository, times(2)).save(any(ChatMessageEntity.class));
    }

    @Test
    void generateFlashcard_documentFailed_returnsEmptyList_noQuotaNoRag() {
        ownedDocument.setStatus(DocumentStatus.FAILED);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(ownedDocument));
        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(aiQuotaService.getUsage(userId)).thenReturn(new AiQuotaService.QuotaInfo(0, 10, 10));

        StudyMaterialService.Outcome<FlashcardGenerateResponse> outcome =
                studyMaterialService.generateFlashcard(request(documentId, 15, null, null), userId);

        assertTrue(outcome.refused());
        assertEquals("Tài liệu xử lý thất bại. Vui lòng tải lại tài liệu rồi thử lại.",
                outcome.message());
        assertTrue(outcome.body().getFlashcards().isEmpty());
        verify(studyMaterialClient, never()).generateFlashcard(any(), any(), any());
        verify(aiQuotaService, never()).checkAndIncrement(any());
    }

    // ------------------------------------------------------------------
    // Access / validation guards (must run before any persistence)
    // ------------------------------------------------------------------

    @Test
    void generateQuiz_documentNotAccessible_forbidden() {
        DocumentEntity others = DocumentEntity.builder()
                .id(documentId)
                .uploader(UserEntity.builder().id(UUID.randomUUID()).build())
                .status(DocumentStatus.COMPLETED)
                .visibility(DocumentVisibility.PRIVATE) // not owner + not public → FORBIDDEN
                .build();
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(others));

        AppException ex = assertThrows(AppException.class,
                () -> studyMaterialService.generateQuiz(request(documentId, 10, null, null), userId));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(chatSessionRepository, never()).save(any());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void generateQuiz_deletedDocument_notFound() {
        ownedDocument.setStatus(DocumentStatus.DELETED);
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(ownedDocument));

        AppException ex = assertThrows(AppException.class,
                () -> studyMaterialService.generateQuiz(request(documentId, 10, null, null), userId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void generateQuiz_sessionNotOwned_notFound() {
        UUID strangerSessionId = UUID.randomUUID();
        when(aiQuotaService.checkAndIncrement(userId)).thenReturn(new AiQuotaService.QuotaInfo(1, 10, 9));
        when(documentRepository.findById(documentId)).thenReturn(Optional.of(ownedDocument));
        when(chatSessionRepository.findByIdAndUserId(strangerSessionId, userId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> studyMaterialService.generateQuiz(request(documentId, 10, null, strangerSessionId), userId));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(chatMessageRepository, never()).save(any());
    }

    @Test
    void generateQuiz_nullDocumentId_badRequest() {
        AppException ex = assertThrows(AppException.class,
                () -> studyMaterialService.generateQuiz(request(null, 10, null, null), userId));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verifyNoInteractions(aiQuotaService, documentRepository, chatSessionRepository, chatMessageRepository);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void assertPayload(String payload, String expectedType, int expectedItems) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            assertEquals(expectedType, node.path("type").asText());
            assertTrue(node.path("items").isArray());
            assertEquals(expectedItems, node.path("items").size());
        } catch (Exception e) {
            fail("material_payload is not valid JSON: " + payload, e);
        }
    }
}
