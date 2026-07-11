package com.bizsage.api.recommendations;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.conversations.ConversationStore;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations/{conversationId}/recommendations")
public class RecommendationController {
  private final ConversationStore conversationStore;
  private final RecommendationService recommendationService;

  public RecommendationController(ConversationStore conversationStore, RecommendationService recommendationService) {
    this.conversationStore = conversationStore;
    this.recommendationService = recommendationService;
  }

  @GetMapping
  ApiResponse<RecommendationDtos.RecommendationResponse> list(
      @PathVariable long conversationId,
      Principal principal,
      HttpServletRequest request) {
    var conversation = conversationStore.getForOwner(principal.getName(), conversationId);
    return ApiResponse.ok(
        recommendationService.build(conversation, conversation.agentMode(), conversation.workflowStage(), List.of()),
        request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }

  @PostMapping("/usage")
  ApiResponse<String> usage(@RequestBody List<Long> ids, HttpServletRequest request) {
    recommendationService.recordUsage(ids);
    return ApiResponse.ok("ok", request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }
}
