package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.request.ReportRequest;
import vn.ai_study_hub_api.controller.response.ReportResponse;
import vn.ai_study_hub_api.controller.response.ReportedDocumentResponse;
import vn.ai_study_hub_api.controller.response.ReportDetailResponse;

import java.util.List;
import java.util.UUID;

public interface ReportService {
    ReportResponse submitReport(UUID documentId, UUID reporterId, ReportRequest request);
    void resolveReport(UUID reportId, String customReason);
    void rejectReport(UUID reportId);
    List<ReportedDocumentResponse> getReportedDocuments();
    List<ReportDetailResponse> getReportDetailsForDocument(UUID documentId);
}
