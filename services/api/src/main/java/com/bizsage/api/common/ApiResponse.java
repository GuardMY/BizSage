package com.bizsage.api.common;

public record ApiResponse<T>(String code, String message, T data, String requestId) {
  public static <T> ApiResponse<T> ok(T data, String requestId) {
    return new ApiResponse<>("OK", "success", data, requestId);
  }

  public static <T> ApiResponse<T> error(String code, String message, String requestId) {
    return new ApiResponse<>(code, message, null, requestId);
  }
}
