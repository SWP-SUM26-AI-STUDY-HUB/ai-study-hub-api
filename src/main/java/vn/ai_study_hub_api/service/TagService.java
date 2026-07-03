package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.request.AdminCreateTagRequest;
import vn.ai_study_hub_api.controller.response.TagResponse;
import java.util.List;
import java.util.UUID;

public interface TagService {
    List<TagResponse> searchTags(String keyword, UUID userId);
    List<TagResponse> createUserTags(List<String> tags, UUID userId);
    TagResponse createPublicTag(AdminCreateTagRequest request);
    List<TagResponse> getAllPublicTags();
}

