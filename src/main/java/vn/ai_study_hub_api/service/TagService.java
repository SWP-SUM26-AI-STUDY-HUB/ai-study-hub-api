package vn.ai_study_hub_api.service;

import vn.ai_study_hub_api.controller.response.TagResponse;
import java.util.List;

public interface TagService {
    List<TagResponse> searchTags(String keyword);
}
