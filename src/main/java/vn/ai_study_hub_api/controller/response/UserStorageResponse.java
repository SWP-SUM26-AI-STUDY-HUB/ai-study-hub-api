package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStorageResponse {
    private Integer planId;        // ID of the plan
    private String planName;       // Name of the plan
    private Long storageUsed;      // in bytes
    private Long storageLimit;     // in bytes
}
