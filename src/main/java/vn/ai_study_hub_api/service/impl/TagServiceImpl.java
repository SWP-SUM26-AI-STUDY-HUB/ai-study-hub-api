package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.controller.response.TagResponse;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.repository.TagRepository;
import vn.ai_study_hub_api.service.TagService;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> searchTags(String keyword) {
        log.info("Searching tags by keyword: '{}'", keyword);
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }

        String trimmedKeyword = keyword.trim();
        List<TagEntity> tags = tagRepository.findByLabelContainingIgnoreCase(trimmedKeyword);

        return tags.stream()
                .map(tag -> TagResponse.builder()
                        .id(tag.getId())
                        .label(tag.getLabel())
                        .build())
                .collect(Collectors.toList());
    }
}
