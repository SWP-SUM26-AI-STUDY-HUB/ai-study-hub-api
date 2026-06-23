package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private UUID sessionId;
    private String sessionTitle;
    private String answer;
    private List<CitationView> citations;
    private int remainingRequests;
    private int dailyLimit;
}
