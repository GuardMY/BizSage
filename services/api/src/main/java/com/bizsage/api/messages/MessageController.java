package com.bizsage.api.messages;

import com.bizsage.api.common.ApiResponse;
import com.bizsage.api.common.RequestIds;
import com.bizsage.api.conversations.ConversationStore;
import com.bizsage.api.users.UserStore;
import com.bizsage.api.worker.AiWorkerException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
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
  StreamingResponseBody streamDiagnosis(
      @PathVariable long conversationId,
      @Valid @RequestBody MessageRequest request,
      Principal principal) {
    var user = userStore.findByUsername(principal.getName()).orElseThrow();
    var conversation = conversationStore.getForOwner(principal.getName(), conversationId);
    return outputStream -> {
      writeEvent(outputStream, "status", "{\"state\":\"started\"}");
      try {
        String diagnosis = diagnosisService.diagnose(conversation, user, request.question());
        writeDiagnosisFrames(outputStream, diagnosis);
      } catch (AiWorkerException ex) {
        log.error("Diagnosis failed — worker error (conversation={}): {}", conversationId, ex.getMessage());
        writeEvent(outputStream, "error", toErrorPayload(ex));
      }
    };
  }

  // ── Learning Agent endpoints ────────────────────────────────────

  /**
   * Industry Learning Agent — SSE streaming endpoint.
   * Accepts an optional chainNodeId and learningMode for guided learning.
   */
  @PostMapping(value = "/learn/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  StreamingResponseBody streamLearning(
      @PathVariable long conversationId,
      @Valid @RequestBody LearnMessageRequest request,
      Principal principal) {
    var user = userStore.findByUsername(principal.getName()).orElseThrow();
    var conversation = conversationStore.getForOwner(principal.getName(), conversationId);
    return outputStream -> {
      writeEvent(outputStream, "status", "{\"state\":\"started\",\"mode\":\"LEARNING\"}");
      try {
        String result = learningService.learn(
            conversation, user, request.question(),
            request.chainNodeId(), request.learningMode());
        writeDiagnosisFrames(outputStream, result);
      } catch (AiWorkerException ex) {
        log.error("Learning failed — worker error (conversation={}): {}", conversationId, ex.getMessage());
        writeEvent(outputStream, "error", toErrorPayload(ex));
      }
    };
  }

  /**
   * Dual-Agent mode transition — SSE streaming endpoint.
   * Switches between LEARNING and DIAGNOSIS modes with preserved context.
   */
  @PostMapping(value = "/transition/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  StreamingResponseBody streamTransition(
      @PathVariable long conversationId,
      @Valid @RequestBody TransitionMessageRequest request,
      Principal principal) {
    var user = userStore.findByUsername(principal.getName()).orElseThrow();
    var conversation = conversationStore.getForOwner(principal.getName(), conversationId);
    return outputStream -> {
      writeEvent(outputStream, "status",
          "{\"state\":\"started\",\"mode\":\"" + request.toMode() + "\"}");
      try {
        String result = learningService.transition(
            conversation, user,
            request.fromMode(), request.toMode(),
            request.question(), request.chainNodeId());
        writeDiagnosisFrames(outputStream, result);
      } catch (AiWorkerException ex) {
        log.error("Transition failed — worker error (conversation={}): {}", conversationId, ex.getMessage());
        writeEvent(outputStream, "error", toErrorPayload(ex));
      }
    };
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

  private void writeDiagnosisFrames(OutputStream outputStream, String diagnosis) throws IOException {
    JsonNode payload = objectMapper.readTree(diagnosis);
    String answer = payload.path("answer").asText("");
    List<String> snapshots = progressiveAnswers(answer);

    if (payload instanceof ObjectNode payloadObject) {
      for (String snapshot : snapshots) {
        ObjectNode partialPayload = payloadObject.deepCopy();
        partialPayload.put("answer", snapshot);
        writeEvent(outputStream, "diagnosis", objectMapper.writeValueAsString(partialPayload));
      }
    } else {
      writeEvent(outputStream, "diagnosis", diagnosis);
      return;
    }

    if (snapshots.isEmpty() || !answer.equals(snapshots.getLast())) {
      writeEvent(outputStream, "diagnosis", diagnosis);
    }
  }

  private static List<String> progressiveAnswers(String answer) {
    if (answer == null || answer.isEmpty()) {
      return List.of();
    }
    if (answer.length() == 1) {
      return List.of(answer);
    }

    List<String> snapshots = new ArrayList<>();
    int chunkSize = 24;
    int end = Math.min(answer.length() - 1, chunkSize);
    while (end < answer.length()) {
      snapshots.add(answer.substring(0, end));
      end += chunkSize;
    }
    snapshots.add(answer);
    return snapshots;
  }

  private static void writeEvent(OutputStream outputStream, String eventName, String payload) throws IOException {
    String event = "event: " + eventName + "\n" + "data: " + payload + "\n\n";
    outputStream.write(event.getBytes(StandardCharsets.UTF_8));
    outputStream.flush();
  }

  record MessageRequest(@NotBlank String question) {
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
