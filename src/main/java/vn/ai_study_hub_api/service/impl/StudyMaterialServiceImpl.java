package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.ai_study_hub_api.controller.request.StudyMaterialRequest;
import vn.ai_study_hub_api.controller.response.FlashcardGenerateResponse;
import vn.ai_study_hub_api.controller.response.QuizGenerateResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.DocumentStatus;
import vn.ai_study_hub_api.model.DocumentVisibility;
import vn.ai_study_hub_api.repository.DocumentRepository;
import vn.ai_study_hub_api.service.AiQuotaService;
import vn.ai_study_hub_api.service.StudyMaterialClient;
import vn.ai_study_hub_api.service.StudyMaterialService;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StudyMaterialServiceImpl implements StudyMaterialService {

    private final AiQuotaService aiQuotaService;
    private final DocumentRepository documentRepository;
    private final StudyMaterialClient studyMaterialClient;

    @Override
    public Outcome<QuizGenerateResponse> generateQuiz(StudyMaterialRequest request, UUID userId) {
        validate(request);
        // 1. Quota — enforced before the RAG call (matches ChatService.chat). A refusal still
        //    consumes the quota; see AGENTS "smalltalk/greetings" gotcha for the same trade-off.
        AiQuotaService.QuotaInfo quota = aiQuotaService.checkAndIncrement(userId);
        // 2. Document access (owner OR public+completed, never deleted).
        loadAccessibleDocument(request.getDocumentId(), userId);
        // 3. Generate via RAG.
        StudyMaterialClient.QuizResult result =
                studyMaterialClient.generateQuiz(request.getDocumentId(), request.getCount(), request.getFocus());

        QuizGenerateResponse body = QuizGenerateResponse.builder()
                .quiz(result.questions())
                .remainingRequests(quota.remaining())
                .dailyLimit(quota.dailyLimit())
                .build();
        // refusal reason (null on success) is threaded to the controller's ApiResponse.message.
        return new Outcome<>(body, result.refused() ? result.reason() : null);
    }

    @Override
    public Outcome<FlashcardGenerateResponse> generateFlashcard(StudyMaterialRequest request, UUID userId) {
        validate(request);
        AiQuotaService.QuotaInfo quota = aiQuotaService.checkAndIncrement(userId);
        loadAccessibleDocument(request.getDocumentId(), userId);
        StudyMaterialClient.FlashcardResult result =
                studyMaterialClient.generateFlashcard(request.getDocumentId(), request.getCount(), request.getFocus());

        FlashcardGenerateResponse body = FlashcardGenerateResponse.builder()
                .flashcards(result.items())
                .remainingRequests(quota.remaining())
                .dailyLimit(quota.dailyLimit())
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
}
