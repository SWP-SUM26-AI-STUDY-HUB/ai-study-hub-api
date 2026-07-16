package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One multiple-choice question returned to the frontend (camelCase wire shape).
 *
 * <p>Built programmatically from the RAG service's snake_case JSON ({@code correct_index}),
 * so no {@code @JsonProperty} is needed here — the mapping lives in the RAG client.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestionResponse {

    private String question;
    private List<String> options;
    private Integer correctIndex;
    private String explanation;
}
