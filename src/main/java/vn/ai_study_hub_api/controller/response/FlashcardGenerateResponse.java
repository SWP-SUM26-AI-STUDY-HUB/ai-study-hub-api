package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Flashcard generation result returned to the frontend. See {@link QuizGenerateResponse}
 * for the quota/refusal semantics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashcardGenerateResponse {

    private List<FlashcardItemResponse> flashcards;
    private int remainingRequests;
    private int dailyLimit;
}
