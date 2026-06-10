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
public class TrendingDocumentResponse {
    private UUID id;
    private String title;
    private String description;
    private String summary;
    private String fileUrl;
    private String fileType;
    private Long fileSizeBytes;
    private LocalDateTime createdAt;
    private UploaderResponse uploader;
    private List<TagResponse> tags;
    private Double averageRating;
    private Long reviewCount;
}
