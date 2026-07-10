package com.bizsage.api;

import com.bizsage.api.worker.AiWorkerClient;
import com.bizsage.api.worker.DiagnoseRequest;
import com.bizsage.api.worker.DiagnoseResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiWorkerClientStreamTest {

  @Test
  void deliversDeltaBeforeWorkerResultCompletes() throws Exception {
    CountDownLatch firstDeltaSent = new CountDownLatch(1);
    CountDownLatch releaseResult = new CountDownLatch(1);
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/agent/diagnose/stream", exchange -> {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
      exchange.sendResponseHeaders(200, 0);
      try (var output = exchange.getResponseBody()) {
        output.write(("event: delta\n"
            + "data: {\"text\":\"first\",\"attempt\":1}\n\n")
            .getBytes(StandardCharsets.UTF_8));
        output.flush();
        firstDeltaSent.countDown();
        releaseResult.await(5, TimeUnit.SECONDS);
        output.write(("event: result\n"
            + "data: {\"answer\":\"first final\",\"sources\":[],"
            + "\"confidence\":\"MEDIUM\",\"timeliness\":\"now\","
            + "\"selfCheckStatus\":\"PASSED\",\"disclaimer\":\"test\"}\n\n")
            .getBytes(StandardCharsets.UTF_8));
        output.flush();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    });
    server.start();

    try {
      AiWorkerClient client = new AiWorkerClient(
          "http://localhost:" + server.getAddress().getPort(), new ObjectMapper());
      CountDownLatch listenerReceivedDelta = new CountDownLatch(1);
      CompletableFuture<DiagnoseResponse> future = CompletableFuture.supplyAsync(() ->
          client.streamDiagnose(
              DiagnoseRequest.builder().question("test").build(),
              event -> {
                if ("delta".equals(event.event())) listenerReceivedDelta.countDown();
              }));

      assertThat(firstDeltaSent.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(listenerReceivedDelta.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(future).isNotDone();

      releaseResult.countDown();
      DiagnoseResponse result = future.get(2, TimeUnit.SECONDS);
      assertThat(result.answer()).isEqualTo("first final");
    } finally {
      releaseResult.countDown();
      server.stop(0);
    }
  }
}
