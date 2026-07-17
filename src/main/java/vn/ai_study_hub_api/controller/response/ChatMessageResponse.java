package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private UUID id;
    private String sender;
    private String content;
    private List<CitationView> citations;
    /** "QUIZ" or "FLASHCARD" when this bot message carries a study-material payload; null otherwise. */
    private String materialType;
    private List<QuizQuestionResponse> quiz;
    private List<FlashcardItemResponse> flashcards;
    private LocalDateTime createdAt;
}
