package com.bizsage.api.intelligence;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/intelligence")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class IntelligenceController {
  private final IntelligenceStore store;

  public IntelligenceController(IntelligenceStore store) {
    this.store = store;
  }

  @PostMapping
  ApiResponse<IntelligenceItem> create(@Valid @RequestBody CreateIntelligenceRequest body, HttpServletRequest request) {
    return ApiResponse.ok(store.create(body), requestId(request));
  }

  @PostMapping("/{id}/approve")
  ApiResponse<IntelligenceItem> approve(@PathVariable long id, HttpServletRequest request) {
    return ApiResponse.ok(store.approve(id), requestId(request));
  }

  @GetMapping
  ApiResponse<List<IntelligenceItem>> list(HttpServletRequest request) {
    return ApiResponse.ok(store.list(), requestId(request));
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }
}
