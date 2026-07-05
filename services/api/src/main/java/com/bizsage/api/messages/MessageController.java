package com.bizsage.api.messages;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.conversations.ConversationStore;
import com.bizsage.api.users.UserStore;
import com.bizsage.api.worker.AiWorkerException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

  private static final Logger log = LoggerFactory.getLogger(MessageController.class);

  private final DiagnosisService diagnosisService;
  private final ConversationStore conversationStore;
  private final ConversationMessageStore messageStore;
  private final UserStore userStore;

  public MessageController(
      DiagnosisService diagnosisService,
      ConversationStore conversationStore,
      ConversationMessageStore messageStore,
      UserStore userStore) {
    this.diagnosisService = diagnosisService;
    this.conversationStore = conversationStore;
    this.messageStore = messageStore;
    this.userStore = userStore;
  }

  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  String streamDiagnosis(
      @PathVariable long conversationId,
      @Valid @RequestBody MessageRequest request,
      Principal principal) {
    var user = userStore.findByUsername(principal.getName()).orElseThrow();
    var conversation = conversationStore.getForOwner(principal.getName(), conversationId);

    try {
      String diagnosis = diagnosisService.diagnose(conversation, user, request.question());
      return "event: diagnosis\n" + "data: " + diagnosis + "\n\n";
    } catch (AiWorkerException ex) {
      log.error("Diagnosis failed — worker error (conversation={}): {}", conversationId, ex.getMessage());
      String errorPayload = toErrorPayload(ex);
      return "event: error\n" + "data: " + errorPayload + "\n\n";
    }
  }

  @GetMapping
  ApiResponse<List<ConversationMessage>> listMessages(
      @PathVariable long conversationId,
      Principal principal,
      HttpServletRequest request) {
    conversationStore.getForOwner(principal.getName(), conversationId);
    return ApiResponse.ok(
        messageStore.activeMessages(conversationId),
        request.getAttribute(RequestIds.ATTRIBUTE).toString());
  }

  private String toErrorPayload(AiWorkerException ex) {
    String message = ex.getMessage() != null ? ex.getMessage() : "AI worker unavailable";
    // Determine a machine-readable error code for the frontend
    String errorCode = "WORKER_ERROR";
    if (message.contains("not reachable") || message.contains("connection failed")) {
      errorCode = "WORKER_UNREACHABLE";
    } else if (message.contains("not configured") || message.contains("LLM_NOT_CONFIGURED")) {
      errorCode = "LLM_NOT_CONFIGURED";
    } else if (message.contains("timed out")) {
      errorCode = "WORKER_TIMEOUT";
    }

    return "{\"error\":\"" + errorCode + "\",\"message\":\"" + escapeJson(message) + "\"}";
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  record MessageRequest(@NotBlank String question) {
  }
}
