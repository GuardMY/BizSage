package com.bizsage.api.worker;

import com.fasterxml.jackson.databind.JsonNode;

/** Incremental event emitted by an AI Worker streaming endpoint. */
public record AgentStreamEvent(String event, JsonNode data) {
}
