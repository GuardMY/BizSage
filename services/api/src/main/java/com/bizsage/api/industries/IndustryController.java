package com.bizsage.api.industries;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.users.UserStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/industries")
public class IndustryController {
  private final UserStore userStore;
  private final UserIndustryStore industryStore;

  public IndustryController(UserStore userStore, UserIndustryStore industryStore) {
    this.userStore = userStore;
    this.industryStore = industryStore;
  }

  @GetMapping
  ApiResponse<List<UserIndustry>> list(Principal principal, HttpServletRequest request) {
    var user = userStore.findByUsername(principal.getName()).orElseThrow();
    if (industryStore.list(user.id()).isEmpty() && user.industryId() != null) {
      String name = IndustryCatalog.PRESET.stream()
          .filter(item -> item.id().equals(user.industryId()))
          .map(IndustryCatalog.Definition::name)
          .findFirst().orElse(user.industryId());
      industryStore.add(user.id(), user.industryId(), name, "PRESET");
    }
    return ApiResponse.ok(industryStore.list(user.id()), requestId(request));
  }

  @PostMapping
  ApiResponse<UserIndustry> add(@Valid @RequestBody CreateIndustryRequest body,
      Principal principal, HttpServletRequest request) {
    var user = userStore.findByUsername(principal.getName()).orElseThrow();
    String id = body.industryId() == null || body.industryId().isBlank()
        ? "custom-" + Integer.toUnsignedString(body.name().trim().hashCode())
        : body.industryId();
    return ApiResponse.ok(industryStore.add(user.id(), id, body.name().trim(),
        IndustryCatalog.isPreset(id) ? "PRESET" : "CUSTOM"), requestId(request));
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }

  record CreateIndustryRequest(@NotBlank String name, String industryId) {}
}
