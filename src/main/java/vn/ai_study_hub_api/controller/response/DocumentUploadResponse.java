package vn.ai_study_hub_api.controller.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentUploadResponse {
    @JsonProperty("document_id")
    private String documentId;
    
    private String status;
}
