package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.controller.response.TagResponse;
import vn.ai_study_hub_api.exception.AppException;
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

    @Override
    @Transactional
    public List<TagResponse> createTags(List<String> tags) {
        log.info("Creating tags: {}", tags);
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        List<TagEntity> savedEntities = new java.util.ArrayList<>();
        for (String tag : tags) {
            if (tag == null || tag.trim().isEmpty()) {
                continue;
            }
            String trimmedTag = tag.trim();
            if (trimmedTag.length() > 30) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Tag length cannot exceed 30 characters");
            }

            TagEntity tagEntity = tagRepository.findByLabel(trimmedTag)
                    .orElseGet(() -> tagRepository.save(TagEntity.builder().label(trimmedTag).build()));
            savedEntities.add(tagEntity);
        }

        return savedEntities.stream()
                .map(tag -> TagResponse.builder()
                        .id(tag.getId())
                        .label(tag.getLabel())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> getAllTags() {
        log.info("Retrieving all tags");
        List<TagEntity> tags = tagRepository.findAll();
        return tags.stream()
                .map(tag -> TagResponse.builder()
                        .id(tag.getId())
                        .label(tag.getLabel())
                        .build())
                .collect(Collectors.toList());
    }
}
