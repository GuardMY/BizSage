package com.bizsage.api.auth;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        JwtPrincipal principal = jwtService.verify(header.substring("Bearer ".length()));
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
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
}
