package com.bizsage.api.conversations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class ConversationStore {
  private final AtomicLong ids = new AtomicLong(1);
  private final List<Conversation> conversations = new ArrayList<>();

  public synchronized Conversation create(String ownerUsername, String title, String regionId, String industryId) {
    Conversation conversation = new Conversation(
        ids.getAndIncrement(),
        ownerUsername,
        title,
        "ACTIVE",
        regionId,
        industryId,
        "user",
        1.0);
    conversations.add(conversation);
    return conversation;
  }

  public synchronized List<Conversation> listFor(String ownerUsername) {
    return conversations.stream()
        .filter(conversation -> conversation.ownerUsername().equals(ownerUsername))
        .toList();
  }

  public synchronized Conversation archive(String ownerUsername, long id) {
    for (int i = 0; i < conversations.size(); i++) {
      Conversation conversation = conversations.get(i);
      if (conversation.id() == id && conversation.ownerUsername().equals(ownerUsername)) {
        Conversation archived = conversation.archive();
        conversations.set(i, archived);
        return archived;
      }
    }
    throw new IllegalArgumentException("conversation not found");
  }
}
