package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationView {
    /** 1-based index matching the [N] source tag embedded in the answer text. */
    private Integer id;
    private UUID documentId;
    private String fileName;
    private Integer pageNumber;
    private String snippet;
}
