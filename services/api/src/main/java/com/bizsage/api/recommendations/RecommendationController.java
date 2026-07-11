package com.bizsage.api.recommendations;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.conversations.ConversationStore;
import jakarta.servlet.http.HttpServletRequest;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

  @GetMapping("/pool")
  ApiResponse<List<QuestionPoolItem>> pool(
      @RequestParam(required = false) String industryId,
      @RequestParam(required = false) String regionId,
      @RequestParam(required = false) String agentMode,
      HttpServletRequest request) {
    return ApiResponse.ok(
        recommendationService.list(industryId, regionId, agentMode),
        request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }

  @PostMapping("/pool")
  ApiResponse<QuestionPoolItem> upsert(
      @RequestBody RecommendationDtos.RecommendationUpsertRequest body,
      HttpServletRequest request) {
    QuestionPoolItem item = new QuestionPoolItem();
    item.setIndustryId(body.industryId());
    item.setRegionId(body.regionId());
    item.setAgentMode(body.agentMode());
    item.setQuestionKey(body.questionKey());
    item.setCategory(body.category());
    item.setQuestionText(body.questionText());
    item.setTopLevelScore(body.topLevelScore());
    item.setStatus(body.status());
    item.setSourceType(body.sourceType());
    item.setSourceRef(body.sourceRef());
    return ApiResponse.ok(
        recommendationService.upsert(item),
        request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }

  @PatchMapping("/pool/{id}/rating")
  ApiResponse<String> rate(
      @PathVariable long id,
      @RequestBody RecommendationDtos.RatingRequest body,
      HttpServletRequest request) {
    recommendationService.rate(id, body.rating());
    return ApiResponse.ok("ok", request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }

  @PostMapping("/pool/usage")
  ApiResponse<String> usage(@RequestBody RecommendationDtos.UsageRequest body, HttpServletRequest request) {
    recommendationService.recordUsage(body.ids());
    return ApiResponse.ok("ok", request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }

  @PostMapping("/pool/refresh")
  ApiResponse<List<QuestionPoolItem>> refresh(
      @RequestParam(required = false) String industryId,
      @RequestParam(required = false) String regionId,
      @RequestParam(required = false) String agentMode,
      HttpServletRequest request) {
    return ApiResponse.ok(
        recommendationService.refresh(industryId, regionId, agentMode),
        request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }

  @PostMapping("/usage")
  ApiResponse<String> usage(@RequestBody List<Long> ids, HttpServletRequest request) {
    recommendationService.recordUsage(ids);
    return ApiResponse.ok("ok", request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }
}
