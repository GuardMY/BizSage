# Learning And Diagnosis Agent Guided Workflow Design
**Date:** 2026-07-11

## Goal

Add a guided workflow for both the Learning Agent and the Diagnosis Agent, including self-introduction, a right-side quick-question rail, context-aware next-step suggestions, a persistent top-question pool, and diagnosis completion control.

This design covers:
- Web UI interaction
- Backend API data structures and endpoints
- `services/ai-worker` prompts, context, and output shapes
- question-pool persistence and ranking logic

## Background And Gaps

The current implementation already has diagnosis, learning, mode switching, SSE streaming, RAG, memory, and report generation. What is still missing:
- Agents do not reliably introduce themselves and explain the interaction model first
- The right-side rail is not dynamically refreshed from the current conversation
- There is no shared high-frequency question pool with a stable ranking model
- Diagnosis does not expose an explicit plan, profile-building phase, continue-adding-info path, and completion signal

## Design Principles

1. The right-side suggestions must be highly usable and should minimize user effort.
2. Suggestions should be dynamic, but not depend only on live LLM output; historical usage must influence ranking.
3. Learning and diagnosis should share the same data skeleton while keeping different intent, pacing, and termination rules.
4. Diagnosis must actively drive the user toward a sufficient business profile instead of returning vague conclusions too early.
5. The user must always be able to keep asking, click a shortcut, or refresh suggestions.

## UX

### 1. Learning Agent

On first entry, the Learning Agent should emit a fixed-format intro:
- explain that it is an industry learning assistant
- explain that it can help with nodes, rules, metrics, risks, and opportunities
- explain that it will recommend the next best learning path from the current context
- explain that the right rail can be clicked and refreshed

The right rail should show three kinds of items:
- the next most likely node the user wants
- a finer-grained block within the current node
- an extension direction related to the current context

### 2. Diagnosis Agent

On first entry, the Diagnosis Agent should emit a fixed-format intro:
- explain that it is an operating diagnosis assistant
- explain that it will first build an operating baseline and then diagnose by layers
- explain that it will ask structured questions
- explain that the right rail will surface likely business problems and missing details

At the beginning of diagnosis, the agent should show a draft plan such as:
- which industry the user is in
- whether the business is offline / physical
- approximate investment size
- store / warehouse / team scale
- basic revenue, cost, profit, traffic, and channel facts

The right rail should show three kinds of items:
- the most likely business problems
- the missing profile fields for the current stage
- the details most likely to affect the diagnosis result

## Recommendation Rail Logic

### 1. Real-Time Candidate Generation

After every turn, the AI Worker should produce `recommendationCandidates`:
- Learning Agent candidates are learnable nodes, blocks, directions, and details
- Diagnosis Agent candidates are follow-up business issues, profile fields, and risk points

Candidate sources:
- the current user question
- the current conversation context
- matched knowledge, intelligence, and memory
- the current agent state

### 2. High-Frequency Question Pool

Each industry should have a persistent question pool with granular entries:
- node-level questions
- block-level questions
- detail-level questions
- follow-up questions

Each record should store:
- `topLevelScore`: priority judged by the LLM against industry structure
- `usageCount`: historical click/use count
- `ratingAvg`: average user rating
- `ratingCount`: number of ratings
- `lastUsedAt`: last usage time
- `status`: enabled, hidden, or archived

### 3. Ranking Rule

The final ranking should be a hybrid score:
- LLM judgment score
- top-level score
- usage frequency
- rating score
- freshness bonus or penalty

The ranking goal is to:
- stay relevant to the current context
- keep surfacing historically useful items
- avoid collapsing into a small set of always-hot suggestions

### 4. Refresh Behavior

If the user does not want the current suggestions, they can refresh:
- Learning Agent refreshes into “what should I learn next?”
- Diagnosis Agent refreshes into “what business info should I fill in next?”

Refreshing must preserve:
- current conversation context
- current industry
- current agent state
- a blacklist of recently shown items

## Diagnosis Workflow States

Diagnosis should advance through these states:

1. `INTRO`
- explain the function and interaction model
- show the current diagnosis plan

2. `PROFILE_GATHERING`
- collect baseline operating facts
- fill in missing fields first

3. `ISSUE_HYPOTHESIS`
- LLM summarizes the 1-3 most likely core business problems

4. `DETAIL_PROBING`
- ask about details that can change the diagnosis outcome

5. `READY_TO_CLOSE`
- LLM judges that enough information exists for a report
- the user can still choose to add more

6. `CLOSED`
- generate the diagnosis report
- persist the conclusion, evidence, and memory candidates

## Completion Rules

Diagnosis completion should be decided by both LLM and rules:
- the operating profile reaches a minimum completeness threshold
- the core issue set converges to 1-3 main problems
- key conflicts or gaps are explicitly marked
- the recommendation can be split into actionable priority items

Cases where the user may continue adding information:
- the user explicitly wants to continue
- the LLM marks the state as insufficient
- key indicators conflict
- the user wants an explanation before closing

## API Design

### 1. Conversation Metadata

Add persistent workflow fields to learning and diagnosis conversations:
- `agentMode`
- `workflowStage`
- `profileCompleteness`
- `primaryIssueTags`
- `recommendedQuestionIds`
- `closedBy`
- `closedReason`

### 2. Recommendation API

Add a recommendation endpoint that returns:
- the current recommended question list
- each item’s source and ranking score
- whether refresh is available
- blacklist / dedupe info

### 3. Question Pool API

Add question-pool management endpoints for:
- querying the industry question pool
- creating or updating questions
- bulk usage-count writes
- rating writes
- refreshing the top-question cache

### 4. Diagnosis State API

Add a workflow-state endpoint for:
- reporting the current diagnosis stage
- reporting whether closing is allowed
- reporting whether the user chose to continue

## AI Worker Design

### 1. Learning Agent

The Learning Agent output should expand to include:
- `answer`
- `recommendationCandidates`
- `currentTopic`
- `nextBestTopics`
- `memoryCandidates`

### 2. Diagnosis Agent

The Diagnosis Agent output should expand to include:
- `answer`
- `recommendedQuestions`
- `workflowStage`
- `profileMissingFields`
- `completionSignal`
- `memoryCandidates`

### 3. Prompt Structure

The system prompt should add two instruction groups:
- self-introduction and interaction explanation
- guided recommendation and closure judgment

The diagnosis prompt should also add:
- active profile-field collection
- operating-baseline-first questioning
- missing-field priority
- user-can-continue behavior

The learning prompt should also add:
- recommend the next most useful thing to learn from the current context
- avoid dumping too much at once
- balance hot questions and long-tail questions

## Ranking And Persistence

The question pool should be persisted by industry, with optional layering by region and membership tier.

Update strategy:
- clicking a shortcut increments `usageCount`
- rating updates `ratingAvg` and `ratingCount`
- LLM re-judgment updates `topLevelScore`
- a scheduled job periodically reranks and writes back cache fields

## Compatibility

1. Existing single-turn diagnosis and learning endpoints must keep working.
2. The recommendation payload must be backward-compatible so old clients can ignore it.
3. The diagnosis completion signal should first be a soft state rather than a hard interruption.
4. If the right rail is empty, manual input must remain available.

## Acceptance Criteria

Learning Agent:
- introduces itself and its interaction model on first entry
- shows context-aware next-step learning questions on the right
- ranks shortcut questions from the industry pool
- supports refreshing suggestions

Diagnosis Agent:
- introduces itself and its interaction model on first entry
- shows a diagnosis plan and missing profile fields early on
- shows the most likely business problems on the right
- can emit a closure signal when the LLM judges the diagnosis is ready
- still allows the user to add more information before report generation

Backend And AI Worker:
- recommendation data can be persisted
- the question pool can be queried, updated, and ranked by industry
- streaming endpoints can carry recommendation and stage data
- diagnosis and learning outputs can be extended without breaking the current main path

## Implementation Review On 2026-07-11

### Overall Status

The current implementation has completed much of the schema and API scaffolding, but the guided workflow is not yet closed loop end to end.

Estimated completion by area:
- database and persistence scaffolding: about 75%
- backend endpoint and payload scaffolding: about 70%
- AI guided-workflow behavior actually taking effect: about 30%
- frontend guided-workflow experience: about 35%
- end-to-end closure for this design: about 30%

### Progress Matrix

| Spec item | Status | Current implementation | Main gaps |
|---|---|---|---|
| Learning Agent fixed intro on first entry | Partially implemented | Learning responses already support guided fields such as `recommendationCandidates`, `currentTopic`, and `nextBestTopics`. | There is no dedicated Learning workspace in the current Web UI, so first-entry intro behavior is not surfaced or verified end to end. |
| Learning right rail with next node / block / extension direction | Partially implemented | Backend and worker payloads already reserve learning recommendation fields. | The frontend does not render a Learning-side rail, and recommendation generation is not yet mixed with live conversation state. |
| Diagnosis Agent fixed intro on first entry | Partially implemented | The diagnosis prompt explicitly tells the model to introduce itself and explain that it will build the operating baseline first. | This is still prompt-driven rather than a stable first-entry template, and it is not separated from later turns. |
| Diagnosis draft plan at the beginning | Not implemented | Workflow fields exist in payloads and conversation metadata. | No actual draft plan is generated or shown for industry, business type, investment, scale, revenue, cost, traffic, and channels. |
| Diagnosis right rail with business problems / missing profile fields / high-impact details | Partially implemented | A diagnosis recommendation rail exists in the Web UI. | It currently shows a flat question list rather than the three intended grouped recommendation types. |
| AI Worker emits recommendation candidates after each turn | Partially implemented | Diagnosis returns `recommendedQuestions`; learning returns `recommendationCandidates`. | Diagnosis recommendations are currently derived from top RAG matches rather than a true synthesis of user question, conversation context, memory, and agent state. |
| Persistent high-frequency question pool | Implemented | `question_pools` schema, entity, store, controller, and seed data are in place. | No major structural gap at the persistence layer. |
| Question-pool fields: score / usage / rating / last-used / status | Implemented | The data model already includes `topLevelScore`, `usageCount`, `ratingAvg`, `ratingCount`, `lastUsedAt`, and `status`. | No major structural gap at the schema layer. |
| Hybrid ranking with LLM score, top-level score, usage, rating, and freshness | Partially implemented | The recommendation service combines top-level score, usage, and rating. | There is no live LLM relevance score, no freshness bonus or penalty, and no anti-collapse logic for always-hot items. |
| Refresh preserving context, industry, state, and recent blacklist | Partially implemented | Refresh endpoints and frontend refresh actions already exist. | Refresh currently re-reads the pool rather than recomputing from conversation context, and blacklist/dedupe behavior is not truly enforced. |
| Diagnosis workflow states `INTRO` to `CLOSED` | Not implemented | `workflowStage` exists in Java, TypeScript, and the database schema. | The current service path still sends `INTRO` and does not advance through profile gathering, hypothesis, probing, ready-to-close, and closed states. |
| Completion rules and close readiness | Not implemented | `completionSignal` and `diagnosisClosable` are already reserved in payload contracts. | The Java service currently sends `diagnosisClosable=false`, and there is no rule-based completeness or convergence check. |
| User can continue adding information before close | Not implemented | `completionSignal` is already returned. | There is no UI or persistence flow for “continue adding info” versus “ready to close”. |
| Persistent conversation workflow metadata | Partially implemented | Conversation schema and store methods already support `agentMode`, `workflowStage`, `profileCompleteness`, `primaryIssueTags`, `recommendedQuestionIds`, `closedBy`, and `closedReason`. | The current diagnosis and learning service flows do not write these fields back through `ConversationStore.updateWorkflow(...)`. |
| Recommendation API | Implemented | `/api/conversations/{conversationId}/recommendations` exists and returns recommendation rows. | The payload is still closer to a static pool read than the dynamic guided recommendation contract in this design. |
| Question-pool management API | Implemented | Query, upsert, rating, usage, and refresh endpoints are already available. | The API is present, but higher-level operational reranking and cache-refresh orchestration are still missing. |
| Diagnosis state API | Not implemented | No separate workflow-state endpoint exists yet. | The system cannot independently query current stage, closability, or continue intent. |
| Learning output expansion | Partially implemented | The Java response payload already includes `recommendationCandidates`, `currentTopic`, `nextBestTopics`, `workflowStage`, `profileMissingFields`, and `completionSignal`. | The worker request models do not accept the corresponding workflow control fields, and the frontend does not expose the learning workflow. |
| Diagnosis output expansion | Partially implemented | Diagnosis responses already include `recommendedQuestions`, `workflowStage`, `profileMissingFields`, and `completionSignal`. | Most values remain static or shallow, and they are not persisted or rendered as a real workflow experience. |
| Prompt expansion for introduction, recommendation, and closure judgment | Partially implemented | The diagnosis prompt already includes a baseline-first introduction instruction. | The full two-group guided-workflow prompt structure from this design is not yet consistently implemented for both agents. |
| Active profile gathering and missing-field priority | Not implemented | Only partial prompt intent exists. | There is no structured missing-field identification or priority-driven follow-up questioning. |
| Recommendation usage writes on shortcut use | Partially implemented | The backend can record usage counts and service flows already call `recordUsage(...)`. | The frontend click on a recommendation only fills the input box and does not explicitly confirm usage as a user action before send. |
| Rating writes | Implemented in backend only | Rating APIs already exist. | The current frontend does not expose a rating interaction. |
| LLM re-judgment of `topLevelScore` | Not implemented | Manual upsert can set the field. | There is no automated re-scoring loop. |
| Scheduled reranking and cache write-back | Not implemented | No scheduler-based reranking flow is present. | Periodic rerank and cached-score refresh are still missing. |
| Persistence by industry with optional region and membership layering | Partially implemented | Current persistence already layers by `industryId`, `regionId`, and `agentMode`. | Membership-tier layering is still missing. |
| Backward compatibility of existing diagnosis and learning paths | Implemented | Existing endpoints and payload structure remain compatible with additive fields. | No major gap found in the compatibility layer. |
| Soft completion signal rather than hard interruption | Implemented | `completionSignal` is currently a soft payload field. | No major gap at the contract level. |
| Manual input still available when rail is empty | Implemented | The diagnosis input box remains available regardless of recommendation state. | No major gap found. |

### Highest-Priority Missing Pieces

1. The Java API passes workflow-control fields, but the AI Worker request models do not accept most of them yet, so those values are effectively ignored.
2. Conversation workflow metadata exists in schema and store code, but the diagnosis and learning paths do not persist real stage, completeness, issue-tag, recommendation-id, or closure updates.
3. The diagnosis workflow state machine is not actually running; `workflowStage` effectively remains `INTRO`.
4. Recommendation refresh is not truly dynamic yet; it mostly re-reads the static question pool instead of recomputing from the active conversation.
5. The frontend does not yet expose the intended guided workflow for learning, diagnosis planning, missing-profile prompts, closure control, and continue-adding-info actions.
