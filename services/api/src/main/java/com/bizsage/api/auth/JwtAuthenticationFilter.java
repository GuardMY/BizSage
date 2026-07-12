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

  /** StreamingResponseBody resumes through an ASYNC dispatch on another thread. */
  @Override
  protected boolean shouldNotFilterAsyncDispatch() {
    return false;
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

  /** Maps product roles to Spring Security authorities. */
  private List<SimpleGrantedAuthority> mapAuthorities(Role role) {
    var authorities = new ArrayList<SimpleGrantedAuthority>();
    switch (role) {
      case SEED_PAID:
      case INTERNAL:
      case FREE:
      case USER:
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        break;
      case LEGAL_FREEZE:
        break;
    }
    return authorities;
  }

  /** Prefer the httpOnly cookie and fall back to Authorization Bearer. */
  private String extractToken(HttpServletRequest request) {
    jakarta.servlet.http.Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (jakarta.servlet.http.Cookie cookie : cookies) {
        if ("bizsage_token".equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
          return cookie.getValue();
        }
      }
    }
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      return header.substring("Bearer ".length());
    }
    return null;
  }
}
