package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.request.ReportRequest;
import vn.ai_study_hub_api.controller.response.ReportResponse;

import java.util.UUID;

public interface ReportService {
    ReportResponse submitReport(UUID documentId, UUID reporterId, ReportRequest request);
}
