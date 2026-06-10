package vn.ai_study_hub_api.controller.request;

import lombok.Data;
import java.util.List;

@Data
public class EditDocumentRequest {
    private String title;
    private String status;
    private List<String> tags;
}