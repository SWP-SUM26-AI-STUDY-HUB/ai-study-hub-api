package vn.ai_study_hub_api.controller.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentAccessResponse {
    @JsonProperty("document_id")
    private UUID documentId;

    private String title;

    @JsonProperty("file_type")
    private String fileType;

    @JsonProperty("file_size_bytes")
    private Long fileSizeBytes;

    @JsonProperty("presigned_url")
    private String presignedUrl;
}
