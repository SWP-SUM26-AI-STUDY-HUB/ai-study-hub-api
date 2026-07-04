package vn.ai_study_hub_api.controller.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavePreferredTagsRequest {

    @NotEmpty(message = "You must select at least 1 tag.")
    @Size(max = 3, message = "You can select at most 3 tags.")
    private List<Integer> tagIds;
}
