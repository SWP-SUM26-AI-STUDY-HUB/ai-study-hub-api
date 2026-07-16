package vn.ai_study_hub_api.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import vn.ai_study_hub_api.controller.response.FlashcardItemResponse;
import vn.ai_study_hub_api.controller.response.QuizQuestionResponse;

import java.util.List;
import java.util.UUID;

/**
 * Blocking HTTP client for the RAG service's quiz / flashcard generation endpoints
 * (FastAPI {@code POST /api/v1/quiz/generate}, {@code POST /api/v1/flashcard/generate}).
 *
 * <p>Sibling of {@link ChatbotClient}: reuses the shared {@code WebClient} + {@code ObjectMapper}
 * beans. Returns the parsed items + a {@code refused} flag (document too short / fragmented),
 * leaving quota enforcement + document-access checks to {@code StudyMaterialService}.</p>
 */
public interface StudyMaterialClient {

    QuizResult generateQuiz(UUID documentId, Integer count, String focus);

    FlashcardResult generateFlashcard(UUID documentId, Integer count, String focus);

    /**
     * Wire request body (snake_case, mirrors RAG {@code StudyMaterialRequest}).
     * A record tolerates a null {@code focus}/{@code count} (Map.of would not).
     */
    record GenerateRequest(
            @JsonProperty("document_id") String documentId,
            @JsonProperty("count") Integer count,
            @JsonProperty("focus") String focus) {
    }

    /** Parsed quiz result. {@code refused=true} means the RAG service declined (empty questions). */
    record QuizResult(boolean refused, String reason, List<QuizQuestionResponse> questions) {
    }

    /** Parsed flashcard result. {@code refused=true} means the RAG service declined (empty items). */
    record FlashcardResult(boolean refused, String reason, List<FlashcardItemResponse> items) {
    }
}
