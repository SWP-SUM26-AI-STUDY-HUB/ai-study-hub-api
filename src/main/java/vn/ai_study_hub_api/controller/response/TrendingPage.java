package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Serializable snapshot of a trending-documents page, used purely as the Redis cache value.
 *
 * <p>{@code PageImpl} is not JSON-friendly (it drags in {@code Pageable}/{@code Sort} internals and
 * does not round-trip cleanly), so the cached {@code @Cacheable} loader returns this flat holder
 * instead; the service rewraps it into a {@link org.springframework.data.domain.Page} per request.
 *
 * <p>The cached {@code content} always carries the user-agnostic (PUBLIC-only) tag view; per-viewer
 * private-tag enrichment for documents the caller owns is applied by the service after the cache hit.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendingPage {
    private List<TrendingDocumentResponse> content;
    private long totalElements;
    private int pageNumber;
    private int pageSize;
}
