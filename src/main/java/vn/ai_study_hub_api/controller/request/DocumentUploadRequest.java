package vn.ai_study_hub_api.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import vn.ai_study_hub_api.model.DocumentVisibility;
import java.util.List;

@Getter
@Setter
@Schema(description = "Request body for uploading a new study document")
public class DocumentUploadRequest {

    @Schema(description = "The title of the document", example = "Spring Boot Tutorial", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "List of tag names/labels associated with the document", example = "[\"LinearAlgebra\", \"MidtermExam\"]", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private List<String> tags;

    @Schema(description = "Detailed description of the document", example = "An introductory guide to building REST APIs with Spring Boot", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String description;

    @Schema(description = "Visibility configuration for the document", example = "PRIVATE", defaultValue = "PRIVATE", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private DocumentVisibility visibility = DocumentVisibility.PRIVATE;
}

