package vn.ai_study_hub_api.controller.request;

import lombok.Getter;
import lombok.Setter;
import vn.ai_study_hub_api.model.DocumentVisibility;
import java.util.List;

@Getter
@Setter
public class DocumentUploadRequest {
    private String title;
    private List<Integer> tagIds;
    private String description;
    private DocumentVisibility visibility = DocumentVisibility.PRIVATE;
}
