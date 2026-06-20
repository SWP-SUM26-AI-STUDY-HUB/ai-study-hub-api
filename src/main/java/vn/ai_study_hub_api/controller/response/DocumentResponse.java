package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import java.util.Map;

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
    private String fileType;
    private String status;
    private String description;
    private Map<Integer, String> tags;
    private UploaderResponse uploader;
    private String visibility;
    private LocalDateTime createdAt;
}