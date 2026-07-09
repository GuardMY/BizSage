package com.bizsage.api.auth;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwtService;
  private final ObjectMapper objectMapper;

  public JwtAuthenticationFilter(JwtService jwtService, ObjectMapper objectMapper) {
    this.jwtService = jwtService;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String token = extractToken(request);
    if (token != null) {
      try {
        // 校验成功后只把 username 和 authority 放入 SecurityContext，避免在上下文中携带敏感 token。
        JwtPrincipal principal = jwtService.verify(token);
        var authorities = mapAuthorities(principal.role());
        var auth = new UsernamePasswordAuthenticationToken(principal.username(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (IllegalArgumentException exception) {
        // token 存在但无效时立即返回 401，避免继续进入业务控制器。
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String requestId = String.valueOf(request.getAttribute(RequestIds.ATTRIBUTE));
        objectMapper.writeValue(response.getWriter(),
            ApiResponse.error("UNAUTHORIZED", "authentication required", requestId));
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  /**
   * 将业务角色映射为 Spring Security authority。
   *
   * <p>管理角色获得显式 ROLE_*；普通用户获得 ROLE_USER。冻结角色不给任何 authority，
   * 等价于认证通过但无法访问受保护资源。
   */
  private List<SimpleGrantedAuthority> mapAuthorities(Role role) {
    var authorities = new ArrayList<SimpleGrantedAuthority>();
    switch (role) {
      case SUPER_ADMIN:
        authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        authorities.add(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        break;
      case OPERATOR:
        authorities.add(new SimpleGrantedAuthority("ROLE_OPERATOR"));
        break;
      case SEED_PAID:
      case INTERNAL:
      case FREE:
      case USER:
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        break;
      case LEGAL_FREEZE:
        // 法务冻结账号不给权限，后续授权阶段会拒绝访问。
        break;
    }
    return authorities;
  }

  /** 优先从 httpOnly cookie 提取 JWT，缺失时兼容 Authorization Bearer 头。 */
  private String extractToken(HttpServletRequest request) {
    // 浏览器场景优先使用 httpOnly cookie，减少 token 暴露给 JavaScript 的机会。
    jakarta.servlet.http.Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (jakarta.servlet.http.Cookie cookie : cookies) {
        if ("bizsage_token".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
          return cookie.getValue();
        }
      }
    }
    // 移动端和脚本客户端仍可使用 Bearer header。
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      return header.substring("Bearer ".length());
    }
    return null;
  }
}
