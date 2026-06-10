package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDetailResponse {
    private UUID id;
    private String title;
    private String description;
    private String visibility;
    private String status;
    private String fileType;
    private Long fileSizeBytes;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
