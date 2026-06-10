package vn.ai_study_hub_api.controller.response;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class DocumentSharedPreviewResponse {
    private UUID id;
    private String title;
    private String description;
    private String summary;
    private String fileType;
    private Long fileSizeBytes;
    private String uploaderName;
    private List<String> tags;
    private String previewUrl;
}
