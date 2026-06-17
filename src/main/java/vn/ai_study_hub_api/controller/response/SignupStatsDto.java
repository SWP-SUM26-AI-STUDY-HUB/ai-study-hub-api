package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignupStatsDto {
    private LocalDate date;
    private long count;
}
