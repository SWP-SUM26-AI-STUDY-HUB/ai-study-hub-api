package vn.ai_study_hub_api.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import vn.ai_study_hub_api.model.DocumentVisibility;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Yêu cầu chứa thông tin siêu dữ liệu của tài liệu (dùng cho cả tải lên và cập nhật)")
public class DocumentRequest {

    @Schema(description = "Tiêu đề của tài liệu", example = "Spring Boot Tutorial")
    private String title;

    @Schema(description = "Danh sách các nhãn thẻ liên kết với tài liệu", example = "[\"LinearAlgebra\", \"MidtermExam\"]")
    private List<String> tags;

    @Schema(description = "Mô tả chi tiết của tài liệu", example = "Hướng dẫn xây dựng REST APIs với Spring Boot")
    private String description;

    @Schema(description = "Chế độ hiển thị của tài liệu", example = "PRIVATE")
    private DocumentVisibility visibility;
}
