package com.bizsage.api.messages;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

  public MessageController(DiagnosisService diagnosisService) {
    this.diagnosisService = diagnosisService;
  }

  @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  String streamDiagnosis(@PathVariable long conversationId, @Valid @RequestBody MessageRequest request) {
    String diagnosis = diagnosisService.diagnose(request.question());
    return "event: diagnosis\n" + "data: " + diagnosis + "\n\n";
  }

  record MessageRequest(@NotBlank String question) {
  }
}
