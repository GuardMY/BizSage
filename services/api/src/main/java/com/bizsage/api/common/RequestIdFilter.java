package com.bizsage.api.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // 优先沿用调用方传入的 X-Request-Id；没有时生成新的链路追踪 ID。
    String requestId = request.getHeader("X-Request-Id");
    if (requestId == null || requestId.isBlank()) {
      requestId = RequestIds.create();
    }
    request.setAttribute(RequestIds.ATTRIBUTE, requestId);
    response.setHeader("X-Request-Id", requestId);
    // 放入 MDC，后续日志可自动带上同一 requestId。
    MDC.put(RequestIds.ATTRIBUTE, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(RequestIds.ATTRIBUTE);
    }
  }
}
