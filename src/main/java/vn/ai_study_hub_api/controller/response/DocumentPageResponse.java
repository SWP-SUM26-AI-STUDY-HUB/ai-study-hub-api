package vn.ai_study_hub_api.controller.response;

import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;
import vn.ai_study_hub_api.common.ApiResponse;

@NoArgsConstructor
public class DocumentPageResponse extends ApiResponse<Page<DocumentResponse>> {
}
