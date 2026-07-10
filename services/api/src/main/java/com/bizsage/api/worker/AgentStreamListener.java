package com.bizsage.api.worker;

import java.io.IOException;

@FunctionalInterface
public interface AgentStreamListener {
  void onEvent(AgentStreamEvent event) throws IOException;
}
