package vn.ai_study_hub_api.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.ai_study_hub_api.config.CacheConfig;
import vn.ai_study_hub_api.controller.response.TagResponse;
import vn.ai_study_hub_api.controller.response.TrendingDocumentResponse;
import vn.ai_study_hub_api.controller.response.TrendingPage;
import vn.ai_study_hub_api.controller.response.UploaderResponse;
import vn.ai_study_hub_api.model.DocumentEntity;
import vn.ai_study_hub_api.model.TagVisibility;
import vn.ai_study_hub_api.repository.TrendingDocumentRepository;
import vn.ai_study_hub_api.repository.projection.TrendingStatsProjection;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User-agnostic, Redis-cached builder of the trending-documents page.
 *
 * <p>Split out of {@link TrendingDocumentServiceImpl} into its own Spring bean so that the
 * {@link Cacheable} advice actually engages: caching annotations are proxy-based and are silently
 * bypassed on self-invocation (a method in the same class calling another), so the cacheable read
 * must live in a collaborator bean invoked through its proxy.
 *
 * <p>The cached payload deliberately carries only PUBLIC tags — the view shared by every viewer.
 * Owner-specific private-tag enrichment is applied per request by the service after the cache hit,
 * keeping the cache globally shareable (one entry per page) without leaking any user's private tags.
 * Mapping happens inside the transaction so LAZY {@code uploader}/{@code tags} are materialized
 * before the DTO is serialized to Redis (no {@code LazyInitializationException} on cache write).
 */
@Component
@RequiredArgsConstructor
public class TrendingDocumentCacheLoader {

    private final TrendingDocumentRepository trendingDocumentRepository;

    @Cacheable(cacheNames = CacheConfig.CACHE_TRENDING_DOCUMENTS,
            key = "#pageable.pageNumber + ':' + #pageable.pageSize")
    @Transactional(readOnly = true)
    public TrendingPage loadTrendingPage(Pageable pageable) {
        Page<DocumentEntity> documentsPage = trendingDocumentRepository.findTrendingDocuments(pageable);
        List<DocumentEntity> documents = documentsPage.getContent();

        if (documents.isEmpty()) {
            return TrendingPage.builder()
                    .content(Collections.emptyList())
                    .totalElements(documentsPage.getTotalElements())
                    .pageNumber(pageable.getPageNumber())
                    .pageSize(pageable.getPageSize())
                    .build();
        }

        List<UUID> docIds = documents.stream().map(DocumentEntity::getId).collect(Collectors.toList());
        Map<UUID, TrendingStatsProjection> statsMap = trendingDocumentRepository.findStatsForDocuments(docIds)
                .stream()
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

            // PUBLIC-only tags: the shared, user-agnostic view. Owner private tags are merged later
            // by the service so the cache stays identical for every viewer.
            List<TagResponse> tagResponses = Collections.emptyList();
            if (doc.getTags() != null) {
                tagResponses = doc.getTags().stream()
                        .filter(t -> t.getVisibility() == null
                                || TagVisibility.PUBLIC.equals(t.getVisibility()))
                        .map(tag -> TagResponse.builder()
                                .id(tag.getId())
                                .label(tag.getLabel())
                                .visibility(tag.getVisibility() != null
                                        ? tag.getVisibility() : TagVisibility.PUBLIC)
                                .build())
                        .collect(Collectors.toList());
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

        return TrendingPage.builder()
                .content(content)
                .totalElements(documentsPage.getTotalElements())
                .pageNumber(pageable.getPageNumber())
                .pageSize(pageable.getPageSize())
                .build();
    }
}
