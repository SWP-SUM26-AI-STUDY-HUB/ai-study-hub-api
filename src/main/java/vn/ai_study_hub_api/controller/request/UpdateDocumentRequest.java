package vn.ai_study_hub_api.controller.request;

import lombok.Data;

import java.util.List;

@Data
public class UpdateDocumentRequest {
    private String title;
    private String description;
    private String visibility;
    private List<Integer> tagIds;
}
