package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.request.UpdateDocumentRequest;
import vn.ai_study_hub_api.controller.response.DocumentDetailResponse;

import java.util.UUID;

public interface DocumentEditService {
    DocumentDetailResponse updateDocument(UUID documentId, UUID userId, UpdateDocumentRequest request);
}
