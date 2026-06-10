package vn.ai_study_hub_api.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportRequest {

    @NotBlank(message = "Report reason is required.")
    @Size(max = 1000, message = "Report reason must not exceed 1000 characters.")
    private String reason;
}
