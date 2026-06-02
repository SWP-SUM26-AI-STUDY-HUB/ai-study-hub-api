package vn.ai_study_hub_api.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request model for token refresh")
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    @Schema(description = "The active refresh token", example = "eyJhbGciOi...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
