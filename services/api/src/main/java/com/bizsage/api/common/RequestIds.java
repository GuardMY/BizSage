package com.bizsage.api.common;

import java.util.UUID;

public final class RequestIds {
  public static final String ATTRIBUTE = "requestId";

  private RequestIds() {
  }

  public static String create() {
    return UUID.randomUUID().toString();
  }
}
