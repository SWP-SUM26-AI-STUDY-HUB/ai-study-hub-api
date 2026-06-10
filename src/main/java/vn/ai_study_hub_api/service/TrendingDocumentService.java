package vn.ai_study_hub_api.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.ai_study_hub_api.controller.response.TrendingDocumentResponse;

public interface TrendingDocumentService {
    Page<TrendingDocumentResponse> getTrendingDocuments(Pageable pageable);
}
