# BizSage Prompt Stage Archive

This document is the English counterpart of `prompt-stage-archive-zh-CN.md`. It archives the effective business prompts in the repository by module and explains which runtime stage each prompt belongs to, what it is used for, and how it is composed.

## 1. Scope and Archive Rules

This archive covers the prompts that are actually used in the BizSage runtime:

- layered system prompts in `services/ai-worker/app/prompt_library`
- diagnosis and learning execution prompts in `services/ai-worker/app`
- cross-Agent transition prompts in `services/ai-worker/app/agent_transition.py`
- memory-extraction prompts in `services/ai-worker/app/memory.py`
- legacy compatibility prompt wrappers in `services/ai-worker/app/llm.py`

This archive intentionally excludes:

- CSS class names such as `guidePrompt`
- test-only placeholder strings such as `"system prompt"` or `"test prompt"`
- keyword match rules used for intent classification or regex extraction, because they are routing logic rather than prompts sent to an LLM

## 2. Prompt Stage Summary

| Runtime stage | Main module | Prompt type | Main purpose |
|---------------|-------------|-------------|--------------|
| System constraint injection before generation | `prompt_library` | layered system prompt | Define role, industry, region, compliance, output structure, and sourcing/disclaimer boundaries |
| Final diagnosis generation | `agent.py` | diagnosis user prompt | Combine the user question with compressed evidence and memory-aware context |
| Final learning generation | `learning_agent.py` | learning user prompt | Turn intent, learning mode, chain node, memory context, and evidence into a teachable request |
| Cross-Agent mode switch | `agent_transition.py` | transition prompt | Reframe the same user goal when switching between learning and diagnosis |
| Post-answer memory extraction | `memory.py` | memory extraction system and user prompts | Extract durable user profile and progress memories from the just-finished turn |
| Legacy compatibility path | `llm.py` | thin wrapper prompt | Keep old direct-call interfaces working, but without the layered system prompt |

## 3. Module-by-Module Archive

### 3.1 `services/ai-worker/app/prompt_library`

This module owns the standardized system prompt stage. `PromptAssembler` composes six layers in a fixed order and injects the final result as the `system` message before diagnosis or learning generation.

| Prompt / fragment | File | Stage | Role |
|-------------------|------|-------|------|
| `ROLE_DIAGNOSIS` | `defaults.py` | system constraint injection | Defines the diagnosis Agent identity, evidence-first tone, and uncertainty behavior |
| `ROLE_LEARNING` | `defaults.py` | system constraint injection | Defines the learning Agent identity, teaching tone, and beginner-friendly explanation style |
| `INDUSTRY_MANUFACTURING` | `defaults.py` | system constraint injection | Adds manufacturing-specific business dimensions |
| `INDUSTRY_ECOMMERCE` | `defaults.py` | system constraint injection | Adds e-commerce-specific business dimensions |
| `INDUSTRY_LOCAL_SERVICES` | `defaults.py` | system constraint injection | Adds local-services-specific business dimensions |
| `REGION_CN_HONGKONG` | `defaults.py` | system constraint injection | Adds Hong Kong legal, tax, and policy-locality reminders |
| `REGION_CN_SHANGHAI` | `defaults.py` | system constraint injection | Adds Shanghai cost-structure reminders |
| `COMPLIANCE` | `defaults.py` | system constraint injection | Enforces no-investment/no-legal/no-financial-advice, evidence sufficiency, privacy, and compliance red-line rules |
| `OUTPUT_DIAGNOSIS` | `defaults.py` | system constraint injection | Forces diagnosis answers into finding, risk, action, and evidence sections |
| `OUTPUT_LEARNING` | `defaults.py` | system constraint injection | Forces learning answers into concept, example, metrics, and next-step sections |
| `SOURCE_DISCLAIMER` | `defaults.py` | system constraint injection | Requires source labels and a standard disclaimer |

Assembly rules:

- Order is fixed as `role -> industry -> region -> compliance -> output_format -> source_disclaimer`.
- Resolution priority is `PROMPT_*` environment override first, then increasingly generic defaults.
- `agent.py` assembles this prompt with `AgentMode.DIAGNOSIS`; `learning_agent.py` assembles it with `AgentMode.LEARNING`.

### 3.2 `services/ai-worker/app/agent.py`

This module owns the final diagnosis-generation prompt stage.

| Prompt / fragment | Stage | Role | Main inputs |
|-------------------|-------|------|-------------|
| assembled diagnosis `system_prompt` | pre-generation system stage | Applies the six-layer diagnosis constraints before the model sees the user question | mode, `industry_id`, `region_id` |
| diagnosis `user` message: `question + evidence` | final diagnosis generation | Sends the actual diagnosis task to the model with compressed evidence and memory-aware context | raw question, compressed RAG evidence, memory context |

Execution characteristics:

- The `user` prompt is intentionally short; most structural and compliance constraints live in the system prompt.
- If there is conflict metadata or no usable evidence, this module returns controlled outputs without sending a generation prompt to the model.

### 3.3 `services/ai-worker/app/learning_agent.py`

This module owns the final learning-generation prompt stage.

| Prompt / fragment | Stage | Role | Main inputs |
|-------------------|-------|------|-------------|
| assembled learning `system_prompt` | pre-generation system stage | Applies the six-layer learning constraints before generation | mode, `industry_id`, `region_id` |
| `_build_learning_prompt(...)` result | final learning generation | Converts the learning task into a structured teaching request | user question, intent type, learning mode, chain node, memory context, compressed evidence |
| `mode_hint` fragment | final learning generation | Tells the model whether the user wants fast onboarding, full-chain learning, or deep dive | `learning_mode`, chain node |
| `intent_hint` fragment | final learning generation | Tells the model whether the user is asking for overview, node learning, metrics, risk, hidden rules, or policy | `intent_type`, chain node |

Execution characteristics:

- The learning prompt is richer than the diagnosis prompt because it also carries teaching mode and intent interpretation.
- Memory context is injected into the `user` prompt rather than the system prompt so it stays turn-specific.

### 3.4 `services/ai-worker/app/agent_transition.py`

This module owns the mode-switch prompt stage.

| Prompt / fragment | Stage | Role | Main inputs |
|-------------------|-------|------|-------------|
| `_learn_to_diagnose_prompt(...)` | cross-Agent mode switch | Rewrites a learning conversation into a diagnosis-oriented question | current user question, chain node, learning summary |
| `_diagnose_to_learn_prompt(...)` | cross-Agent mode switch | Rewrites a diagnosis conversation into a learning-oriented question | current user question, chain node, diagnosis summary |
| `build_transition_prompt(...)` | cross-Agent mode switch | Chooses the correct transition prompt builder or returns the question unchanged for same-mode calls | source mode, target mode |

Execution characteristics:

- Transition prompts are not standalone system prompts; they are question rewriters that feed the target Agent.
- After rewriting, the target Agent still adds its own layered system prompt and normal generation prompt.

### 3.5 `services/ai-worker/app/memory.py`

This module owns the post-answer memory-extraction prompt stage.

| Prompt / fragment | Stage | Role | Main inputs |
|-------------------|-------|------|-------------|
| `_MEMORY_EXTRACTION_SYSTEM_PROMPT` | post-answer memory extraction | Defines extraction categories, confidence rules, JSON schema, and no-fabrication requirements | none; static system instruction |
| `_extract_memories_via_llm()` `user_prompt` | post-answer memory extraction | Supplies the just-finished question, answer, optional chain node, and existing-memory keys for deduplicated extraction | user question, assistant answer, chain node, existing memories |

Execution characteristics:

- This prompt runs after diagnosis, learning, or transition output is already produced.
- If the extraction model fails or returns invalid JSON, the code falls back to regex-based extraction instead of blocking the main Agent result.

### 3.6 `services/ai-worker/app/llm.py`

This module contains the legacy compatibility prompt path.

| Prompt / fragment | Stage | Role | Current status |
|-------------------|-------|------|----------------|
| `generate_answer()` single `user` message | legacy direct generation | Sends `question + evidence` without a system prompt | deprecated; bypasses `PromptAssembler` |
| `generate_answer_learning()` single `user` message | legacy direct generation | Sends the old learning context without layered constraints | deprecated; bypasses `PromptAssembler` |

Operational note:

- These wrappers still represent prompt entrypoints in the codebase, but they are explicitly marked as deprecated and should not be used for new work.

## 4. Recommended Archive View by Stage

For day-to-day maintenance, the repository prompts can be read as four operational layers:

1. `prompt_library`: stable system-governance layer
2. `agent.py` and `learning_agent.py`: task-execution layer
3. `agent_transition.py`: mode-switch adaptation layer
4. `memory.py`: post-turn extraction layer

This stage split matches the actual runtime call order more closely than grouping prompts only by file name or by Agent type.
