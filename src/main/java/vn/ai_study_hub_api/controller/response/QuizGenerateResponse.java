package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Quiz generation result returned to the frontend.
 *
 * <p>Mirrors {@link ChatResponse}: carries {@code remainingRequests}/{@code dailyLimit} so the
 * frontend can update the daily AI quota badge (generation counts against the same quota as chat).
 * When the RAG service refuses (document too short / fragmented), {@code quiz} is empty and
 * the controller returns the refusal {@code message} for the frontend to display.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizGenerateResponse {

    private List<QuizQuestionResponse> quiz;
    private int remainingRequests;
    private int dailyLimit;
}
