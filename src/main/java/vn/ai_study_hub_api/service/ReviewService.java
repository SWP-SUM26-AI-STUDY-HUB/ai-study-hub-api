package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.request.ReviewRequest;
import vn.ai_study_hub_api.controller.response.ReviewResponse;

import java.util.UUID;

public interface ReviewService {
    ReviewResponse submitReview(UUID documentId, UUID userId, ReviewRequest request);
}
