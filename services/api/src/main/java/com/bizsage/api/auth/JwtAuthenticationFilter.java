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
        JwtPrincipal principal = jwtService.verify(token);
        var authorities = mapAuthorities(principal.role());
        var auth = new UsernamePasswordAuthenticationToken(principal.username(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
      } catch (IllegalArgumentException exception) {
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

  /** V2: Map expanded roles to Spring Security authorities.
   *  Admin roles get their explicit ROLE_*; end-user roles get ROLE_USER
   *  as a base + their role-specific authority for data-permission checks. */
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
        // No authorities — effectively blocked from all endpoints
        break;
    }
    return authorities;
  }

  /** Extract JWT from httpOnly cookie first, then fall back to Bearer header. */
  private String extractToken(HttpServletRequest request) {
    // Primary: httpOnly cookie (XSS-safe)
    jakarta.servlet.http.Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (jakarta.servlet.http.Cookie cookie : cookies) {
        if ("bizsage_token".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
          return cookie.getValue();
        }
      }
    }
    // Fallback: Authorization header (backward-compatible, e.g. for mobile / API clients)
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      return header.substring("Bearer ".length());
    }
    return null;
  }
}
