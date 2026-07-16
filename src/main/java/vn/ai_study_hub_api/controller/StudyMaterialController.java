package vn.ai_study_hub_api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.ai_study_hub_api.common.ApiResponse;
import vn.ai_study_hub_api.controller.request.StudyMaterialRequest;
import vn.ai_study_hub_api.controller.response.FlashcardGenerateResponse;
import vn.ai_study_hub_api.controller.response.QuizGenerateResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.security.CustomUserDetails;
import vn.ai_study_hub_api.service.StudyMaterialService;

import java.util.UUID;

/**
 * Quiz & flashcard generation endpoints.
 *
 * <p>Both endpoints enforce the daily AI quota (shared with {@code /chat}), validate document
 * access, and delegate to the RAG service. A refusal (document too short / fragmented) returns
 * HTTP 200 with an empty item list and the refusal reason as the {@code message} — the frontend
 * detects it via the empty list.</p>
 */
@RestController
@RequestMapping("/api/v1/study-materials")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Study Materials", description = "Quiz & flashcard generation from documents")
public class StudyMaterialController {

    private final StudyMaterialService studyMaterialService;

    @PostMapping("/quiz")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Generate a multiple-choice quiz from a document",
            description = "Generates up to `count` questions (default 10, clamped 5-20) scoped to one document. "
                    + "Enforces the daily AI quota. A refusal (document too short / fragmented) returns an empty list.")
    public ApiResponse<QuizGenerateResponse> generateQuiz(@RequestBody StudyMaterialRequest request) {
        UUID userId = currentUserId();
        log.info("Quiz generation request from user {} (documentId={}, count={})",
                userId, request != null ? request.getDocumentId() : null, request != null ? request.getCount() : null);
        StudyMaterialService.Outcome<QuizGenerateResponse> outcome =
                studyMaterialService.generateQuiz(request, userId);
        return ApiResponse.success(outcome.body(),
                outcome.refused() ? outcome.message() : "Quiz generated successfully");
    }

    @PostMapping("/flashcard")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Generate flashcards from a document",
            description = "Generates up to `count` flashcards (default 15, clamped 5-30) scoped to one document. "
                    + "Enforces the daily AI quota. A refusal returns an empty list.")
    public ApiResponse<FlashcardGenerateResponse> generateFlashcard(@RequestBody StudyMaterialRequest request) {
        UUID userId = currentUserId();
        log.info("Flashcard generation request from user {} (documentId={}, count={})",
                userId, request != null ? request.getDocumentId() : null, request != null ? request.getCount() : null);
        StudyMaterialService.Outcome<FlashcardGenerateResponse> outcome =
                studyMaterialService.generateFlashcard(request, userId);
        return ApiResponse.success(outcome.body(),
                outcome.refused() ? outcome.message() : "Flashcards generated successfully");
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails userDetails)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: Access denied.");
        }
        return userDetails.getId();
    }
}
