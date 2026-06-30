package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import vn.ai_study_hub_api.controller.response.TagResponse;
import vn.ai_study_hub_api.controller.response.TrendingDocumentResponse;
import vn.ai_study_hub_api.controller.response.UploaderResponse;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.repository.TrendingDocumentRepository;
import vn.ai_study_hub_api.repository.projection.TrendingStatsProjection;
import vn.ai_study_hub_api.service.TrendingDocumentService;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.ai_study_hub_api.security.CustomUserDetails;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrendingDocumentServiceImpl implements TrendingDocumentService {

    private final TrendingDocumentRepository trendingDocumentRepository;

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) authentication.getPrincipal()).getId();
        }
        return null;
    }

    @Override
    public Page<TrendingDocumentResponse> getTrendingDocuments(Pageable pageable) {
        Page<DocumentEntity> documentsPage = trendingDocumentRepository.findTrendingDocuments(pageable);
        List<DocumentEntity> documents = documentsPage.getContent();

        if (documents.isEmpty()) {
            return Page.empty(pageable);
        }

        List<UUID> docIds = documents.stream().map(DocumentEntity::getId).collect(Collectors.toList());
        List<TrendingStatsProjection> statsList = trendingDocumentRepository.findStatsForDocuments(docIds);
        Map<UUID, TrendingStatsProjection> statsMap = statsList.stream()
                .collect(Collectors.toMap(TrendingStatsProjection::getDocumentId, stats -> stats));

        List<TrendingDocumentResponse> content = documents.stream().map(doc -> {
            TrendingStatsProjection stats = statsMap.get(doc.getId());
            Double avgRating = stats != null ? stats.getAverageRating() : 0.0;
            Long reviewCount = stats != null ? stats.getReviewCount() : 0L;

            UploaderResponse uploaderResponse = null;
            if (doc.getUploader() != null) {
                uploaderResponse = UploaderResponse.builder()
                        .id(doc.getUploader().getId())
                        .fullName(doc.getUploader().getFullName())
                        .avatarUrl(doc.getUploader().getAvatarUrl())
                        .build();
            }

            List<TagResponse> tagResponses = Collections.emptyList();
            if (doc.getTags() != null) {
                UUID currentUserId = getCurrentUserId();
                boolean isOwner = doc.getUploader() != null && doc.getUploader().getId().equals(currentUserId);
                tagResponses = doc.getTags().stream()
                        .filter(t -> isOwner 
                                || t.getVisibility() == null 
                                || vn.ai_study_hub_api.model.TagVisibility.PUBLIC.equals(t.getVisibility()))
                        .map(tag -> TagResponse.builder()
                                .id(tag.getId())
                                .label(tag.getLabel())
                                .visibility(tag.getVisibility() != null ? tag.getVisibility() : vn.ai_study_hub_api.model.TagVisibility.PUBLIC)
                                .build()
                        ).collect(Collectors.toList());
            }

            return TrendingDocumentResponse.builder()
                    .id(doc.getId())
                    .title(doc.getTitle())
                    .description(doc.getDescription())
                    .summary(doc.getSummary())
                    .fileUrl(doc.getFileUrl())
                    .fileType(doc.getFileType())
                    .fileSizeBytes(doc.getFileSizeBytes())
                    .createdAt(doc.getCreatedAt())
                    .uploader(uploaderResponse)
                    .tags(tagResponses)
                    .averageRating(avgRating)
                    .reviewCount(reviewCount)
                    .build();
        }).collect(Collectors.toList());

        return new PageImpl<>(content, pageable, documentsPage.getTotalElements());
    }
}
