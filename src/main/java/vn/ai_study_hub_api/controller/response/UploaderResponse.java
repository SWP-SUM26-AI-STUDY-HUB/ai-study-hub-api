package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploaderResponse {
    private UUID id;
    private String fullName;
    private String avatarUrl;
}
