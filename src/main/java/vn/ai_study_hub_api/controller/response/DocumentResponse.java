package vn.ai_study_hub_api.controller.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponse {
    private UUID id;
    private String title;
    private String description;
    private String fileUrl;
    private String fileType;
    private Long fileSizeBytes;
    private String status;
    private String visibility;
    private List<String> tags;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
