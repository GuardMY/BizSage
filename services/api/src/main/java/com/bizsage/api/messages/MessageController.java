package com.bizsage.api.messages;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.conversations.ConversationStore;
import com.bizsage.api.users.UserStore;
import com.bizsage.api.worker.AiWorkerException;
import com.bizsage.api.worker.AgentStreamEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

  private static final Logger log = LoggerFactory.getLogger(MessageController.class);

  private final DiagnosisService diagnosisService;
  private final LearningService learningService;
  private final ConversationStore conversationStore;
  private final ConversationMessageStore messageStore;
  private final UserStore userStore;
  private final ObjectMapper objectMapper;

  public MessageController(
      DiagnosisService diagnosisService,
      LearningService learningService,
      ConversationStore conversationStore,
      ConversationMessageStore messageStore,
      UserStore userStore,
      ObjectMapper objectMapper) {
    this.diagnosisService = diagnosisService;
    this.learningService = learningService;
    this.conversationStore = conversationStore;
    this.messageStore = messageStore;
    this.userStore = userStore;
    this.objectMapper = objectMapper;
  }

  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  ResponseEntity<StreamingResponseBody> streamDiagnosis(
      @PathVariable long conversationId,
      @Valid @RequestBody MessageRequest request,
      Principal principal) {
    // 先校验会话归属，再返回 StreamingResponseBody，避免流式响应开始后才发现越权。
    var user = userStore.findByUsername(principal.getName()).orElseThrow();
    var conversation = conversationStore.getForOwner(principal.getName(), conversationId);
    return streamResponse(outputStream -> {
      writeEvent(outputStream, "status", startedPayload("DIAGNOSIS"));
      try {
        String diagnosis = diagnosisService.diagnoseStream(
            conversation, user, request.question(),
            event -> forwardWorkerEvent(outputStream, event));
        writeEvent(outputStream, "diagnosis", diagnosis);
      } catch (AiWorkerException ex) {
        log.error("Diagnosis failed — worker error (conversation={}): {}", conversationId, ex.getMessage());
        writeEvent(outputStream, "error", toErrorPayload(ex));
      }
    });
  }

  // 学习 Agent SSE 端点。

  /**
   * 行业学习 Agent 的 SSE 流式端点。
   *
   * <p>支持可选 chainNodeId 和 learningMode，用于前端引导式学习。
   */
  @PostMapping(value = "/learn/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  ResponseEntity<StreamingResponseBody> streamLearning(
      @PathVariable long conversationId,
      @Valid @RequestBody LearnMessageRequest request,
      Principal principal) {
    // 学习流复用诊断帧格式，前端只需按统一 diagnosis 事件解析 payload。
    var user = userStore.findByUsername(principal.getName()).orElseThrow();
    var conversation = conversationStore.getForOwner(principal.getName(), conversationId);
    return streamResponse(outputStream -> {
      writeEvent(outputStream, "status", startedPayload("LEARNING"));
      try {
        String result = learningService.learnStream(
            conversation, user, request.question(),
            request.chainNodeId(), request.learningMode(),
            event -> forwardWorkerEvent(outputStream, event));
        writeEvent(outputStream, "diagnosis", result);
      } catch (AiWorkerException ex) {
        log.error("Learning failed — worker error (conversation={}): {}", conversationId, ex.getMessage());
        writeEvent(outputStream, "error", toErrorPayload(ex));
      }
    });
  }

  /**
   * 双 Agent 模式切换的 SSE 流式端点。
   *
   * <p>在 LEARNING 与 DIAGNOSIS 之间切换时保留会话上下文。
   */
  @PostMapping(value = "/transition/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  ResponseEntity<StreamingResponseBody> streamTransition(
      @PathVariable long conversationId,
      @Valid @RequestBody TransitionMessageRequest request,
      Principal principal) {
    // 切换请求仍绑定当前会话和当前用户，防止跨会话复用上下文。
    var user = userStore.findByUsername(principal.getName()).orElseThrow();
    var conversation = conversationStore.getForOwner(principal.getName(), conversationId);
    return streamResponse(outputStream -> {
      writeEvent(outputStream, "status", startedPayload(request.toMode()));
      try {
        String result = learningService.transitionStream(
            conversation, user,
            request.fromMode(), request.toMode(),
            request.question(), request.chainNodeId(),
            event -> forwardWorkerEvent(outputStream, event));
        writeEvent(outputStream, "diagnosis", result);
      } catch (AiWorkerException ex) {
        log.error("Transition failed — worker error (conversation={}): {}", conversationId, ex.getMessage());
        writeEvent(outputStream, "error", toErrorPayload(ex));
      }
    });
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

  @PostMapping("/follow-up")
  ApiResponse<ConversationMessage> addFollowUp(
      @PathVariable long conversationId,
      @Valid @RequestBody FollowUpRequest request,
      Principal principal,
      HttpServletRequest httpRequest) {
    var conversation = conversationStore.getForOwner(principal.getName(), conversationId);
    long id = messageStore.append(
        conversationId, "ASSISTANT", "FOLLOW_UP_QUESTION", request.question(),
        null, null, null, null, conversation.regionId(), conversation.industryId());
    return ApiResponse.ok(messageStore.activeMessages(conversationId).stream()
        .filter(message -> message.id() == id).findFirst().orElseThrow(),
        httpRequest.getAttribute(RequestIds.ATTRIBUTE).toString());
  }

  private String toErrorPayload(AiWorkerException ex) {
    String message = ex.getMessage() != null ? ex.getMessage() : "AI worker unavailable";
    // 将 Worker 异常文本映射为前端可识别的机器错误码。
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

  private ResponseEntity<StreamingResponseBody> streamResponse(StreamingResponseBody body) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-cache")
        .header("X-Accel-Buffering", "no")
        .body(body);
  }

  private String startedPayload(String mode) throws IOException {
    return objectMapper.writeValueAsString(java.util.Map.of(
        "state", "started",
        "mode", mode,
        "attempt", 1));
  }

  private void forwardWorkerEvent(OutputStream outputStream, AgentStreamEvent event)
      throws IOException {
    writeEvent(outputStream, event.event(), objectMapper.writeValueAsString(event.data()));
  }

  private static void writeEvent(OutputStream outputStream, String eventName, String payload) throws IOException {
    // SSE 格式要求 event/data 后跟空行；每帧 flush 保证浏览器及时收到。
    String event = "event: " + eventName + "\n" + "data: " + payload + "\n\n";
    outputStream.write(event.getBytes(StandardCharsets.UTF_8));
    outputStream.flush();
  }

  record MessageRequest(@NotBlank String question) {
  }

  record FollowUpRequest(@NotBlank String question) {
  }

  record LearnMessageRequest(
      @NotBlank String question,
      String chainNodeId,
      String learningMode) {
  }

  record TransitionMessageRequest(
      @NotBlank String fromMode,
      @NotBlank String toMode,
      @NotBlank String question,
      String chainNodeId) {
  }
}
