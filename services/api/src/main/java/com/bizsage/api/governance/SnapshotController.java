package com.bizsage.api.governance;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.governance.SnapshotDtos.SnapshotCompareResult;
import com.bizsage.api.governance.SnapshotDtos.SnapshotDetail;
import com.bizsage.api.governance.SnapshotDtos.SnapshotSummary;
import com.bizsage.api.governance.SnapshotDtos.SnapshotType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/snapshots")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
public class SnapshotController {

  private final SnapshotStore store;
  private final SnapshotService service;

  public SnapshotController(SnapshotStore store, SnapshotService service) {
    this.store = store;
    this.service = service;
  }

  @GetMapping
  ApiResponse<List<SnapshotSummary>> list(
      @RequestParam(defaultValue = "DAILY") String type,
      @RequestParam(defaultValue = "cn-default") String region,
      @RequestParam(defaultValue = "general") String industry,
      @RequestParam(defaultValue = "50") int limit,
      HttpServletRequest request) {
    SnapshotType snapshotType = SnapshotType.valueOf(type.toUpperCase());
    return ApiResponse.ok(store.listByScope(snapshotType, region, industry, limit),
        requestId(request));
  }

  @GetMapping("/{id}")
  ApiResponse<SnapshotDetail> get(@PathVariable long id, HttpServletRequest request) {
    return ApiResponse.ok(store.find(id), requestId(request));
  }

  @GetMapping("/{id}/diff")
  ApiResponse<SnapshotCompareResult> diff(
      @PathVariable long id, @RequestParam long compare, HttpServletRequest request) {
    return ApiResponse.ok(store.compare(id, compare), requestId(request));
  }

  @PostMapping("/generate")
  ApiResponse<SnapshotDetail> generate(
      @RequestParam(defaultValue = "DAILY") String type,
      @RequestParam(defaultValue = "cn-default") String region,
      @RequestParam(defaultValue = "general") String industry,
      HttpServletRequest request) {
    SnapshotType snapshotType = SnapshotType.valueOf(type.toUpperCase());
    return ApiResponse.ok(service.generateSnapshot(snapshotType, region, industry),
        requestId(request));
  }

  @PostMapping("/cleanup")
  ApiResponse<Integer> cleanup(HttpServletRequest request) {
    int deleted = store.deleteExpired();
    return ApiResponse.ok(deleted, requestId(request));
  }

  private String requestId(HttpServletRequest request) {
    Object attr = request.getAttribute(RequestIds.ATTRIBUTE);
    return attr == null ? RequestIds.create() : attr.toString();
  }
}
