package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoragePlanResponse {
    private Integer id;
    private String name;
    private BigDecimal price;
    private Long storageLimit;
    private Integer maxAiRequestsPerDay;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
