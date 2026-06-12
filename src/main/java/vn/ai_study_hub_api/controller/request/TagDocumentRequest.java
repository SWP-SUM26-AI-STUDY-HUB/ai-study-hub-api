package vn.ai_study_hub_api.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class TagDocumentRequest {
    @Schema(description = "List of tag names/labels to associate with the document", example = "[\"LinearAlgebra\", \"MidtermExam\"]")
    private List<String> tags;
}
