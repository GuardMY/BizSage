package com.bizsage.api.worker;

/**
 * Thrown when the AI worker is unreachable, returns a non-200 status,
 * or the LLM is not configured.
 *
 * <p>This exception signals an explicit infrastructure failure — callers
 * must NOT fall back to local template answers.
 */
public class AiWorkerException extends RuntimeException {

  private final int statusCode;

  public AiWorkerException(String message) {
    super(message);
    this.statusCode = 0;
  }

  public AiWorkerException(String message, int statusCode) {
    super(message);
    this.statusCode = statusCode;
  }

  public AiWorkerException(String message, Throwable cause) {
    super(message, cause);
    this.statusCode = 0;
  }

  public int statusCode() {
    return statusCode;
  }
}
