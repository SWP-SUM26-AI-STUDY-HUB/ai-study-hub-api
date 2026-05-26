package vn.ai_study_hub_api.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO representing user login credentials.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request model for user login")
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "User's registered email address", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(description = "User's password", example = "Password123!", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;
}
