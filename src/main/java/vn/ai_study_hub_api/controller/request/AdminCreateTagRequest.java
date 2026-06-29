package vn.ai_study_hub_api.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCreateTagRequest {

    @NotBlank(message = "Tag label cannot be blank")
    @Size(max = 30, message = "Tag label cannot exceed 30 characters")
    private String label;
}
