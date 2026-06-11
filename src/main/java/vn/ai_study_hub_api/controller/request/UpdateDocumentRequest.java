package vn.ai_study_hub_api.controller.request;

import lombok.Data;
import lombok.Setter;
import lombok.AccessLevel;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.List;

@Data
public class UpdateDocumentRequest {
    private String title;
    private String description;
    private String visibility;

    @Setter(AccessLevel.NONE)
    private List<String> tags;

    @JsonSetter
    public void setTags(Object tags) {
        if (tags == null) {
            this.tags = null;
            return;
        }
        if (tags instanceof List) {
            this.tags = ((List<?>) tags).stream()
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        } else if (tags instanceof String) {
            String str = (String) tags;
            if (str.trim().isEmpty()) {
                this.tags = java.util.Collections.emptyList();
            } else {
                this.tags = java.util.Arrays.stream(str.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(java.util.stream.Collectors.toList());
            }
        }
    }
}
