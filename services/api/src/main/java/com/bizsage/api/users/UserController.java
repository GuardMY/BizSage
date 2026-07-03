package com.bizsage.api.users;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserStore userStore;

  public UserController(UserStore userStore) {
    this.userStore = userStore;
  }

  @GetMapping
  @PreAuthorize("hasAnyRole('SUPER_ADMIN','OPERATOR')")
  ApiResponse<List<UserView>> listUsers(HttpServletRequest request) {
    return ApiResponse.ok(userStore.listViews(), request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }
}
