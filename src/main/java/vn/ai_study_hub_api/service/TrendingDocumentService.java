package vn.ai_study_hub_api.service;

import java.util.List;
import vn.ai_study_hub_api.controller.response.TrendingDocumentResponse;

public interface TrendingDocumentService {
    List<TrendingDocumentResponse> getTrendingDocuments();
}
