package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudyMaterialServiceImpl implements StudyMaterialService {

    private static final int TITLE_MAX_LENGTH = 50;

    private final AiQuotaService aiQuotaService;
    private final DocumentRepository documentRepository;
    private final StudyMaterialClient studyMaterialClient;
    // Persistence: quiz/flashcard are recorded into a chat session so they show up in history.
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Outcome<QuizGenerateResponse> generateQuiz(StudyMaterialRequest request, UUID userId) {
        validate(request);
        // 1. Quota — enforced before the RAG call (matches ChatService.chat). A refusal still
        //    consumes the quota.
        AiQuotaService.QuotaInfo quota = aiQuotaService.checkAndIncrement(userId);
        // 2. Document access (owner OR public+completed, never deleted).
        DocumentEntity document = loadAccessibleDocument(request.getDocumentId(), userId);
        // 3. Resolve/create the chat session the generation is recorded under.
        ChatSessionEntity session = resolveOrCreateSession(request.getSessionId(), userId,
                "Quiz · " + truncate(document.getTitle(), TITLE_MAX_LENGTH));
        attachDocument(session, document);
        session = chatSessionRepository.save(session);

        // 4. Persist the user-side message describing the generation request.
        chatMessageRepository.save(ChatMessageEntity.builder()
                .id(UUID.randomUUID())
                .session(session)
                .sender(MessageSender.USER)
                .content(buildUserContent("quiz", request))
                .build());

        // 5. Generate via RAG.
        StudyMaterialClient.QuizResult result =
                studyMaterialClient.generateQuiz(request.getDocumentId(), request.getCount(), request.getFocus());
        List<QuizQuestionResponse> questions = result.questions() != null ? result.questions() : List.of();

        // 6. Persist the bot message — payload only when items exist; refusal → reason as content.
        chatMessageRepository.save(buildMaterialBotMessage(session, "QUIZ", questions,
                result.refused(), result.reason(), "quiz questions"));

        QuizGenerateResponse body = QuizGenerateResponse.builder()
                .quiz(questions)
                .remainingRequests(quota.remaining())
                .dailyLimit(quota.dailyLimit())
                .sessionId(session.getId())
                .build();
        return new Outcome<>(body, result.refused() ? result.reason() : null);
    }

    @Override
    @Transactional
    public Outcome<FlashcardGenerateResponse> generateFlashcard(StudyMaterialRequest request, UUID userId) {
        validate(request);
        AiQuotaService.QuotaInfo quota = aiQuotaService.checkAndIncrement(userId);
        DocumentEntity document = loadAccessibleDocument(request.getDocumentId(), userId);
        ChatSessionEntity session = resolveOrCreateSession(request.getSessionId(), userId,
                "Flashcards · " + truncate(document.getTitle(), TITLE_MAX_LENGTH));
        attachDocument(session, document);
        session = chatSessionRepository.save(session);

        chatMessageRepository.save(ChatMessageEntity.builder()
                .id(UUID.randomUUID())
                .session(session)
                .sender(MessageSender.USER)
                .content(buildUserContent("flashcards", request))
                .build());

        StudyMaterialClient.FlashcardResult result =
                studyMaterialClient.generateFlashcard(request.getDocumentId(), request.getCount(), request.getFocus());
        List<FlashcardItemResponse> items = result.items() != null ? result.items() : List.of();

        chatMessageRepository.save(buildMaterialBotMessage(session, "FLASHCARD", items,
                result.refused(), result.reason(), "flashcards"));

        FlashcardGenerateResponse body = FlashcardGenerateResponse.builder()
                .flashcards(items)
                .remainingRequests(quota.remaining())
                .dailyLimit(quota.dailyLimit())
                .sessionId(session.getId())
                .build();
        return new Outcome<>(body, result.refused() ? result.reason() : null);
    }

    private void validate(StudyMaterialRequest request) {
        if (request == null || request.getDocumentId() == null) {
            throw new AppException(HttpStatus.BAD_REQUEST, "documentId is required");
        }
    }

    /**
     * Access check duplicated from {@code ChatServiceImpl.loadAccessibleDocument} (kept private
     * there). Owner OR (public + completed), never deleted. Extracting to a shared component
     * is a follow-up; duplicating the small, stable rule avoids coupling this feature to chat.
     */
    private DocumentEntity loadAccessibleDocument(UUID documentId, UUID userId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Document not found"));

        if (document.getDeletedAt() != null || DocumentStatus.DELETED.equals(document.getStatus())) {
            throw new AppException(HttpStatus.NOT_FOUND, "Document not found");
        }

        boolean isOwner = document.getUploader() != null && document.getUploader().getId().equals(userId);
        boolean isPublicCompleted = DocumentVisibility.PUBLIC.equals(document.getVisibility())
                && DocumentStatus.COMPLETED.equals(document.getStatus());

        if (!(isOwner || isPublicCompleted)) {
            throw new AppException(HttpStatus.FORBIDDEN, "You do not have access to this document");
        }
        return document;
    }

    /**
     * Resolve an existing owned session, or create a new one. Mirrors
     * {@code ChatServiceImpl.resolveSession}: app-assigned UUID, user-scoped.
     */
    private ChatSessionEntity resolveOrCreateSession(UUID sessionId, UUID userId, String defaultTitle) {
        if (sessionId != null) {
            return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Chat session not found"));
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found with ID: " + userId));
        return ChatSessionEntity.builder()
                .id(UUID.randomUUID())
                .user(user)
                .title(defaultTitle)
                .build();
    }

    private void attachDocument(ChatSessionEntity session, DocumentEntity document) {
        if (document != null && !session.getDocuments().contains(document)) {
            session.getDocuments().add(document);
        }
    }

    private String buildUserContent(String typeLabel, StudyMaterialRequest request) {
        StringBuilder sb = new StringBuilder("Generate ");
        if (request.getCount() != null) {
            sb.append(request.getCount()).append(" ");
        }
        sb.append(typeLabel);
        String focus = request.getFocus();
        if (focus != null && !focus.isBlank()) {
            sb.append(" about \"").append(focus.trim()).append("\"");
        }
        return sb.append(".").toString();
    }

    /**
     * Builds the bot message for a generation result. On refusal (empty items), the reason
     * becomes the textual {@code content} and no structured payload is stored. On success the
     * content is a short human-readable summary and {@code materialPayload} holds the items.
     */
    private ChatMessageEntity buildMaterialBotMessage(ChatSessionEntity session, String type, List<?> items,
                                                     boolean refused, String reason, String typeLabel) {
        String content;
        String payload = null;
        if (refused || items.isEmpty()) {
            content = (reason != null && !reason.isBlank())
                    ? reason
                    : "Could not generate " + typeLabel + " from this document.";
        } else {
            content = "Generated " + items.size() + " " + typeLabel + ".";
            payload = writeMaterialPayload(type, items);
        }
        return ChatMessageEntity.builder()
                .id(UUID.randomUUID())
                .session(session)
                .sender(MessageSender.BOT)
                .content(content)
                .materialPayload(payload)
                .build();
    }

    private String writeMaterialPayload(String type, List<?> items) {
        try {
            return objectMapper.writeValueAsString(Map.of("type", type, "items", items));
        } catch (Exception e) {
            log.warn("Failed to serialize material payload: {}", e.getMessage());
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
