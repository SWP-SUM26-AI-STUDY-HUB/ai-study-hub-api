package vn.ai_study_hub_api.repository.projection;

import java.util.UUID;

public interface TrendingStatsProjection {
    UUID getDocumentId();
    Double getAverageRating();
    Long getReviewCount();
}
