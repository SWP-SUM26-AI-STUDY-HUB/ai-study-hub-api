package vn.ai_study_hub_api.controller.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One aggregated row from a Langfuse Metrics API v2 group: a dimension label
 * (e.g. trace name, model name, route) paired with a single metric value
 * (e.g. {@code count_count}, {@code p95_latency}, {@code sum_totalTokens}).
 *
 * <p>The frontend renders these directly into bar/donut/pie/table widgets.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetricRow {

    /** Dimension value — e.g. {@code "chat"}, {@code "gemini-2.5-flash-lite"}, {@code "qa"}. */
    private String label;

    /** Metric value — Latency in ms, tokens/cost as raw numbers, counts as long-as-double. */
    private double value;
}
