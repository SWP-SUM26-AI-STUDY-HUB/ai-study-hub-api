package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.controller.request.AdminCreateTagRequest;
import vn.ai_study_hub_api.controller.response.TagResponse;
import vn.ai_study_hub_api.exception.AppException;
import vn.ai_study_hub_api.model.TagEntity;
import vn.ai_study_hub_api.model.TagVisibility;
import vn.ai_study_hub_api.model.UserEntity;
import vn.ai_study_hub_api.repository.TagRepository;
import vn.ai_study_hub_api.repository.UserRepository;
import vn.ai_study_hub_api.service.TagService;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TagResponse> searchTags(String keyword, UUID userId) {
        log.info("Searching tags by keyword: '{}', userId: {}", keyword, userId);
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }

        String trimmedKeyword = keyword.trim();
        List<TagEntity> tags;
        if (userId != null) {
            tags = tagRepository.searchTagsForUser(trimmedKeyword, userId);
        } else {
            tags = tagRepository.searchPublicTags(trimmedKeyword);
        }

        return tags.stream()
                .map(tag -> TagResponse.builder()
                        .id(tag.getId())
                        .label(tag.getLabel())
                        .visibility(tag.getVisibility() != null ? tag.getVisibility() : TagVisibility.PUBLIC)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<TagResponse> createUserTags(List<String> tags, UUID userId) {
        log.info("Creating tags: {} for userId: {}", tags, userId);
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        if (userId == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Unauthorized: User must be logged in to create tags");
        }

        UserEntity userRef = userRepository.getReferenceById(userId);
        List<TagEntity> savedEntities = new java.util.ArrayList<>();

        for (String tag : tags) {
            if (tag == null || tag.trim().isEmpty()) {
                continue;
            }
            String trimmedTag = tag.trim();
            if (trimmedTag.length() > 30) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Tag length cannot exceed 30 characters");
            }

            // 1. kiểm tra có tồn tại tag public không
            TagEntity tagEntity = tagRepository.findByLabelIgnoreCaseAndVisibility(trimmedTag, TagVisibility.PUBLIC)
                    .orElseGet(() -> tagRepository.findByLabel(trimmedTag).orElse(null));

            // 2. kiểm tra tag private của người đó có tồn tại không
            if (tagEntity == null) {
                tagEntity = tagRepository.findByLabelIgnoreCaseAndVisibilityAndCreatedBy_Id(trimmedTag, TagVisibility.PRIVATE, userId)
                        .orElse(null);
            }

            // 3. tạo mới tag private
            if (tagEntity == null) {
                tagEntity = tagRepository.save(TagEntity.builder()
                        .label(trimmedTag)
                        .visibility(TagVisibility.PRIVATE)
                        .createdBy(userRef)
                        .build());
            }

            savedEntities.add(tagEntity);
        }

        return savedEntities.stream()
                .map(tag -> TagResponse.builder()
                        .id(tag.getId())
                        .label(tag.getLabel())
                        .visibility(tag.getVisibility() != null ? tag.getVisibility() : TagVisibility.PUBLIC)
                        .build())
                .collect(Collectors.toList());
    }

}

