package pl.kathelan.user.api.dto;

public record ApiResponse<T>(
        T data,
        String errorCode,
        String message
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null, null);
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(null, errorCode, message);
    }
}
