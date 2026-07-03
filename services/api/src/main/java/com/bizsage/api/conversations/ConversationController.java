package com.bizsage.api.conversations;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.users.UserStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {
  private final ConversationStore conversationStore;
  private final UserStore userStore;

  public ConversationController(ConversationStore conversationStore, UserStore userStore) {
    this.conversationStore = conversationStore;
    this.userStore = userStore;
  }

  @PostMapping
  ApiResponse<Conversation> create(
      @Valid @RequestBody CreateConversationRequest body,
      Principal principal,
      HttpServletRequest request) {
    var user = userStore.findByUsername(principal.getName()).orElseThrow();
    Conversation created = conversationStore.create(user.username(), body.title(), user.regionId(), user.industryId());
    return ApiResponse.ok(created, requestId(request));
  }

  @GetMapping
  ApiResponse<List<Conversation>> list(Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(conversationStore.listFor(principal.getName()), requestId(request));
  }

  @PostMapping("/{id}/archive")
  ApiResponse<Conversation> archive(@PathVariable long id, Principal principal, HttpServletRequest request) {
    return ApiResponse.ok(conversationStore.archive(principal.getName(), id), requestId(request));
  }

  private String requestId(HttpServletRequest request) {
    return request.getAttribute(RequestIds.ATTRIBUTE).toString();
  }

  record CreateConversationRequest(@NotBlank String title) {
  }
}
