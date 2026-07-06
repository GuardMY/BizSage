# Conversation List Archive/Delete Design

**Date:** 2026-07-06

## Goal

Align the conversation sidebar with the current workspace section and let users manage conversations directly from the list:
- show a diagnosis-specific list title in the diagnosis workspace;
- show an archive-specific list title in the archive workspace;
- archive active conversations directly from the diagnosis conversation list;
- soft-delete archived conversations directly from the archive conversation list.

## Current Context

- The web app already separates visible conversations by section through `apps/web/lib/conversation-workspace.ts`.
- The diagnosis workspace already supports archiving the currently selected conversation from the main panel.
- The API already supports `ACTIVE` and `ARCHIVED` conversation statuses, but it does not yet expose a soft-delete action.

## Chosen Approach

1. Keep conversation lifecycle status-driven.
   - Reuse the existing `status` field and add a new soft-delete terminal state: `DELETED`.
   - Keep the database schema unchanged because the status column is already stored as a plain string.
2. Filter deleted conversations out of normal list responses.
   - `GET /api/conversations` should keep returning active and archived records only.
   - Soft-deleted conversations should no longer appear in the diagnosis or archive workspace.
3. Expose deletion as an authenticated conversation action.
   - Add a dedicated API endpoint for soft delete.
   - Only archived conversations should be deletable from the web UI.
4. Keep the web interaction local to the sidebar.
   - Diagnosis list items get a direct archive action.
   - Archive list items get a direct delete action.
   - The existing panel-level archive action stays available.

## UX Details

### Sidebar Titles

- `diagnosis` section title: `诊断会话列表` / `Diagnosis conversation list`
- `archive` section title: `归档会话列表` / `Archive conversation list`
- The eyebrow text should no longer say `当前上下文` / `Current context` for these lists.

### Sidebar Item Actions

- In diagnosis:
  - each active conversation row shows an archive action;
  - activating the archive action must not also trigger row selection.
- In archive:
  - each archived conversation row shows a delete action;
  - deletion is soft delete only.

### Selection Rules

- After archiving from the diagnosis list, the next visible active conversation becomes selected.
- After deleting from the archive list, the next visible archived conversation becomes selected.
- If no visible conversation remains in the current section, selection becomes `null`.

## Error Handling

- Archive and delete failures should surface through the existing page status/error flow.
- Unauthorized/expired sessions should continue to use the existing auth-expiry handling path.

## Testing

- API integration test:
  - archive a conversation;
  - soft-delete it;
  - confirm it no longer appears in `GET /api/conversations`.
- Web regression tests:
  - deleted conversations are excluded from active/archive partitions;
  - section-based sidebar titles and action labels map correctly.
