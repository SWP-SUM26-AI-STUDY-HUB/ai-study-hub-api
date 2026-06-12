package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.response.DocumentDetailResponse;

import java.util.List;
import java.util.UUID;

public interface DocumentEditService {
    DocumentDetailResponse tagDocument(UUID documentId, UUID userId, List<String> tags);
}


