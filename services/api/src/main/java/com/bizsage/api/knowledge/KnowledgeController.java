package com.bizsage.api.knowledge;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class KnowledgeController {
  private final KnowledgeStore store;

  public KnowledgeController(KnowledgeStore store) {
    this.store = store;
  }

  @PostMapping("/import")
  ApiResponse<KnowledgeItem> importItem(@Valid @RequestBody ImportKnowledgeRequest body, HttpServletRequest request) {
    return ApiResponse.ok(store.importItem(body), request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }
}
