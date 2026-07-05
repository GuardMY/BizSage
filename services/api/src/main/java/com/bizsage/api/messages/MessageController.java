package com.bizsage.api.messages;

import com.bizsage.api.conversations.ConversationStore;
import com.bizsage.api.users.UserStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.security.Principal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {
  private final DiagnosisService diagnosisService;
  private final ConversationStore conversationStore;
  private final UserStore userStore;

  public MessageController(
      DiagnosisService diagnosisService,
      ConversationStore conversationStore,
      UserStore userStore) {
    this.diagnosisService = diagnosisService;
    this.conversationStore = conversationStore;
    this.userStore = userStore;
  }

  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  String streamDiagnosis(
      @PathVariable long conversationId,
      @Valid @RequestBody MessageRequest request,
      Principal principal) {
    var user = userStore.findByUsername(principal.getName()).orElseThrow();
    var conversation = conversationStore.getForOwner(principal.getName(), conversationId);
    String diagnosis = diagnosisService.diagnose(conversation, user, request.question());
    return "event: diagnosis\n" + "data: " + diagnosis + "\n\n";
  }

  record MessageRequest(@NotBlank String question) {
  }
}
