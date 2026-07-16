package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.request.StudyMaterialRequest;
import vn.ai_study_hub_api.controller.response.FlashcardGenerateResponse;
import vn.ai_study_hub_api.controller.response.QuizGenerateResponse;

import java.util.UUID;

/**
 * Quiz & flashcard generation: enforces the daily AI quota, validates document access,
 * and delegates content generation to the RAG service.
 *
 * <p>Quota is enforced the same way as {@link ChatService#chat} (incremented before the
 * RAG call, against the shared {@code user:ai_limit:*} Redis counter). Document access
 * mirrors {@code ChatServiceImpl.loadAccessibleDocument}: owner OR (public + completed),
 * never deleted.</p>
 */
public interface StudyMaterialService {

    Outcome<QuizGenerateResponse> generateQuiz(StudyMaterialRequest request, UUID userId);

    Outcome<FlashcardGenerateResponse> generateFlashcard(StudyMaterialRequest request, UUID userId);

    /**
     * Service result: {@code body} is always present; {@code message} is null on success and
     * holds the RAG refusal reason when the document was too short / fragmented (items empty).
     */
    record Outcome<T>(T body, String message) {
        public boolean refused() {
            return message != null;
        }
    }
}
