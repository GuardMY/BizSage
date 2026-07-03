package com.bizsage.api.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiControllerAdvice {
  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiResponse<Void>> forbidden(AccessDeniedException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(ApiResponse.error("FORBIDDEN", "permission denied", requestId(request)));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error("VALIDATION_ERROR", "invalid request", requestId(request)));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(ApiResponse.error("BAD_REQUEST", exception.getMessage(), requestId(request)));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> serverError(Exception exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(ApiResponse.error("INTERNAL_ERROR", "internal server error", requestId(request)));
  }

  private String requestId(HttpServletRequest request) {
    Object value = request.getAttribute(RequestIds.ATTRIBUTE);
    return value == null ? RequestIds.create() : value.toString();
  }
}
