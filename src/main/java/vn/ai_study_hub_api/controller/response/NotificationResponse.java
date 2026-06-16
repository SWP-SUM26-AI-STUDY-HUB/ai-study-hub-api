package vn.ai_study_hub_api.controller.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private String title;
    private String content;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
