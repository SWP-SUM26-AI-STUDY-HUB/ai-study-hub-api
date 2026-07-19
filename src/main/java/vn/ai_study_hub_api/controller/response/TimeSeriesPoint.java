package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One bucket of a Langfuse time-dimension series (e.g. daily token usage).
 * {@code date} is the Langfuse {@code time_dimension} value (ISO date string,
 * e.g. {@code "2026-07-17"}); {@code value} is the aggregated metric for that bucket.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeSeriesPoint {

    private String date;
    private double value;
}
