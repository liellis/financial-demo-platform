package com.eps.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Generic API response wrapper for all EPS REST endpoints.
 * Provides a consistent envelope with status code, message, payload,
 * and optional pagination metadata.
 *
 * @param <T> the type of the data payload
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /** HTTP status code or application-level result code */
    private int code;

    /** Human-readable result message */
    private String message;

    /** Response payload — null for error responses */
    private T data;

    /** Timestamp of the response generation */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /** Correlation ID for request tracing */
    private String correlationId;

    /** Pagination metadata (null for non-list endpoints) */
    private Pagination pagination;

    // -- Static factory methods for common patterns -- //

    /**
     * Create a success response with data.
     *
     * @param <T>  the payload type
     * @param data the response payload
     * @return an ApiResponse with code 200 and "Success" message
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("Success")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create a success response with a custom message.
     *
     * @param <T>     the payload type
     * @param data    the response payload
     * @param message the custom success message
     * @return an ApiResponse with code 200
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .code(200)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create an error response without data.
     *
     * @param code    the HTTP or application error code
     * @param message the error description
     * @return an ApiResponse with null data
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Create an error response with a correlation ID for log tracing.
     *
     * @param code          the HTTP or application error code
     * @param message       the error description
     * @param correlationId the correlation ID from the request
     * @return an ApiResponse with null data
     */
    public static <T> ApiResponse<T> error(int code, String message, String correlationId) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .correlationId(correlationId)
                .timestamp(LocalDateTime.now())
                .build();
    }

    // -- Inner class for pagination metadata -- //

    /**
     * Pagination metadata for paginated list responses.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pagination {

        /** Current page number (0-based or 1-based depending on convention) */
        private int page;

        /** Number of records per page */
        private int size;

        /** Total number of records across all pages */
        private long totalElements;

        /** Total number of pages */
        private int totalPages;

        /** Whether this is the first page */
        private boolean first;

        /** Whether this is the last page */
        private boolean last;
    }
}
