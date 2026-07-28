package vn.ai_study_hub_api.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoragePlanRequest {

    @NotBlank(message = "Plan name cannot be blank")
    @Size(max = 50, message = "Plan name cannot exceed 50 characters")
    private String name;

    @NotNull(message = "Price cannot be null")
    @PositiveOrZero(message = "Price must be zero or positive")
    private BigDecimal price;

    @NotNull(message = "Storage limit cannot be null")
    @Positive(message = "Storage limit must be positive")
    private Long storageLimit;

    @NotNull(message = "Max AI requests per day cannot be null")
    @PositiveOrZero(message = "Max AI requests per day must be zero or positive")
    private Integer maxAiRequestsPerDay;
}
