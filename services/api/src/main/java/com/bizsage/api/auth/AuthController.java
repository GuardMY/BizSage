package com.bizsage.api.auth;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.users.UserStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final UserStore userStore;
  private final JwtService jwtService;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

  public AuthController(UserStore userStore, JwtService jwtService) {
    this.userStore = userStore;
    this.jwtService = jwtService;
  }

  @PostMapping("/login")
  ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    var user = userStore.findByUsername(request.username())
        .filter(candidate -> passwordMatches(request.password(), candidate.password()))
        .orElseThrow(() -> new IllegalArgumentException("invalid username or password"));
    return ApiResponse.ok(
        new LoginResponse(
            jwtService.issue(user),
            user.username(),
            user.role(),
            user.regionId(),
            user.industryId(),
            user.membershipLevel(),
            user.consultationPreferences()),
        requestId(httpRequest));
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }

  private boolean passwordMatches(String rawPassword, String storedPassword) {
    if (storedPassword != null && storedPassword.startsWith("$2")) {
      return passwordEncoder.matches(rawPassword, storedPassword);
    }
    return storedPassword != null && storedPassword.equals(rawPassword);
  }

  record LoginRequest(@NotBlank String username, @NotBlank String password) {
  }

  record LoginResponse(
      String token,
      String username,
      Role role,
      String regionId,
      String industryId,
      String membershipLevel,
      String consultationPreferences) {
  }
}
