package com.bizsage.api.health;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
public class HealthController {
  @GetMapping
  ApiResponse<Map<String, String>> health(HttpServletRequest request) {
    return ApiResponse.ok(Map.of("status", "UP"), request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }
}
