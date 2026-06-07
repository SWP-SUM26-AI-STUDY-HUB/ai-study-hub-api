package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {
    private UUID id;
    private String title;
    private String fileName;
    private String fileUrl;
    private Long fileSize;
    private String status; // private, pending, public, rejected
    private LocalDateTime createdAt;
}