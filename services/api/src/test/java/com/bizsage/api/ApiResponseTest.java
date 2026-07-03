package com.bizsage.api;

import com.bizsage.api.common.ApiResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {
  @Test
  void successEnvelopeUsesStableFields() {
    ApiResponse<String> response = ApiResponse.ok("payload", "req-123");

    assertThat(response.code()).isEqualTo("OK");
    assertThat(response.message()).isEqualTo("success");
    assertThat(response.data()).isEqualTo("payload");
    assertThat(response.requestId()).isEqualTo("req-123");
  }
}
