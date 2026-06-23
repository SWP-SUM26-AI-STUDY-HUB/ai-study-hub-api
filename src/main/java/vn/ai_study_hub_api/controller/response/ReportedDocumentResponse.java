package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportedDocumentResponse {
    private UUID documentId;
    private String title;
    private String uploaderName;
    private Long reportCount;
    private LocalDateTime latestReportAt;
}
