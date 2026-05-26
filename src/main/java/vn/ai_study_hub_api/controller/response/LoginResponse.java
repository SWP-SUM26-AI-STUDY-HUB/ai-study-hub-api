package vn.ai_study_hub_api.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * Response DTO returning credentials and user profile information after successful authentication.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Response payload after successful login")
public class LoginResponse {

    @Schema(description = "JWT Access Token (TTL: 15 minutes)", example = "eyJhbGciOi...")
    private String accessToken;

    @Schema(description = "JWT Refresh Token (TTL: 7 days)", example = "eyJhbGciOi...")
    private String refreshToken;

    @Schema(description = "User's internal unique identifier (UUID)", example = "123e4567-e89b-12d3-a456-426614174000")
    private UUID id;

    @Schema(description = "User email address", example = "user@example.com")
    private String email;

    @Schema(description = "User's display full name", example = "John Doe")
    private String fullName;

    @Schema(description = "User role in the system", example = "user")
    private String role;
}
