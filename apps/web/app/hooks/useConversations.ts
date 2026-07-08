"use client";

import { useCallback, useEffect, useState } from "react";
import {
  archiveConversation,
  AuthExpiredError,
  createConversation,
  deleteConversation,
  fetchConversations,
  type Conversation,
  type LoginProfile
} from "../../lib/api-client";
import { nextSelectionAfterArchive } from "../../lib/conversation-workspace";

export type ConversationsState = {
  conversations: Conversation[];
  selectedConversationId: number | null;
  selectedConversation: Conversation | null;
  selectConversation: (id: number) => void;
  createNewConversation: (title: string) => Promise<number | null>;
  archiveCurrentConversation: () => Promise<void>;
  archiveConversationById: (id: number) => Promise<void>;
  deleteConversationById: (id: number) => Promise<void>;
  resetSelection: () => void;
};

export function useConversations(
  profile: LoginProfile | null,
  onSessionExpired: () => void,
  onNotice: (msg: string) => void
): ConversationsState {
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [selectedConversationId, setSelectedConversationId] = useState<number | null>(null);

  // Fetch conversations when profile changes
  useEffect(() => {
    if (!profile) {
      setConversations([]);
      setSelectedConversationId(null);
      return;
    }

    let cancelled = false;
    fetchConversations()
      .then((result) => {
        if (!cancelled) setConversations(result.items);
      })
      .catch((error) => {
        if (error instanceof AuthExpiredError) {
          onSessionExpired();
        }
      });

    return () => { cancelled = true; };
  }, [profile, onSessionExpired]);

  const selectedConversation = conversations.find((c) => c.id === selectedConversationId) ?? null;

  const selectConversation = useCallback((id: number) => {
    setSelectedConversationId(id);
  }, []);

  const createNewConversation = useCallback(async (title: string): Promise<number | null> => {
    if (!profile) return null;
    try {
      const created = await createConversation(title);
      setConversations((prev) => [created, ...prev]);
      setSelectedConversationId(created.id);
      return created.id;
    } catch (error) {
      if (error instanceof AuthExpiredError) { onSessionExpired(); return null; }
      onNotice(error instanceof Error ? error.message : "Create conversation failed");
      return null;
    }
  }, [profile, onSessionExpired, onNotice]);

  const archiveCurrentConversation = useCallback(async () => {
    if (!profile || selectedConversationId === null) return;
    try {
      const archived = await archiveConversation(selectedConversationId);
      setConversations((prev) => prev.map((c) => c.id === archived.id ? archived : c));
      const nextId = nextSelectionAfterArchive({
        selectedConversationId,
        conversations: conversations.map((c) => c.id === selectedConversationId ? archived : c)
      });
      setSelectedConversationId(nextId);
      onNotice("Conversation archived.");
    } catch (error) {
      if (error instanceof AuthExpiredError) { onSessionExpired(); return; }
      onNotice(error instanceof Error ? error.message : "Archive failed");
    }
  }, [profile, selectedConversationId, conversations, onSessionExpired, onNotice]);

  const archiveConversationById = useCallback(async (id: number) => {
    if (!profile) return;
    try {
      const archived = await archiveConversation(id);
      const updated = conversations.map((c) => c.id === archived.id ? archived : c);
      setConversations(updated);
      setSelectedConversationId(nextSelectionAfterArchive({ selectedConversationId: id, conversations: updated }));
      onNotice("Conversation archived.");
    } catch (error) {
      if (error instanceof AuthExpiredError) { onSessionExpired(); return; }
      onNotice(error instanceof Error ? error.message : "Archive failed");
    }
  }, [profile, conversations, onSessionExpired, onNotice]);

  const deleteConversationById = useCallback(async (id: number) => {
    if (!profile) return;
    try {
      await deleteConversation(id);
      setConversations((prev) => prev.filter((c) => c.id !== id));
      if (selectedConversationId === id) setSelectedConversationId(null);
      onNotice("Conversation deleted.");
    } catch (error) {
      if (error instanceof AuthExpiredError) { onSessionExpired(); return; }
      onNotice(error instanceof Error ? error.message : "Delete failed");
    }
  }, [profile, selectedConversationId, onSessionExpired, onNotice]);

  const resetSelection = useCallback(() => {
    setSelectedConversationId(null);
  }, []);

  return {
    conversations, selectedConversationId, selectedConversation,
    selectConversation, createNewConversation,
    archiveCurrentConversation, archiveConversationById, deleteConversationById,
    resetSelection
  };
}
