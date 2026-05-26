package vn.ai_study_hub_api.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Generic response wrapper for unified REST API responses.
 * @param <T> Type of response data
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Helper to build a successful response with data.
     * @param data Payload data
     * @param message Success message
     * @param <T> Data type
     * @return ApiResponse instance
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    /**
     * Helper to build a successful response without data.
     * @param message Success message
     * @param <T> Data type
     * @return ApiResponse instance
     */
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(null)
                .build();
    }

    /**
     * Helper to build an error response.
     * @param message Error message
     * @param <T> Data type
     * @return ApiResponse instance
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .data(null)
                .build();
    }
}
