package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One flashcard returned to the frontend (camelCase wire shape).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashcardItemResponse {

    private String term;
    private String definition;
}
