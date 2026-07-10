"""Low-level httpx call to any OpenAI-compatible chat completions endpoint."""

from __future__ import annotations

import json
import os
from collections.abc import Iterator

import httpx

from app.llm import LLMCallError, LLMNotConfiguredError
from app.model_routing.models import ModelConfig


def call_provider(
    config: ModelConfig,
    messages: list[dict],
    temperature: float | None = None,
    max_tokens: int | None = None,
) -> str:
    """Send a chat completion request to the configured provider.

    Supports any OpenAI-compatible ``/chat/completions`` endpoint.

    Returns the stripped response text.

    Raises:
        LLMNotConfiguredError: if ``api_key`` is empty or missing.
        LLMCallError: on HTTP failure or empty response.
    """
    api_key = config.api_key or os.getenv("OPENAI_COMPATIBLE_API_KEY", "").strip()
    if not api_key:
        raise LLMNotConfiguredError(
            f"API key not configured for tier {config.tier.value}, "
            f"provider {config.provider}"
        )

    url = f"{config.base_url.rstrip('/')}/chat/completions"
    payload = {
        "model": config.model_name,
        "messages": messages,
        "temperature": temperature if temperature is not None else config.temperature,
        "max_tokens": max_tokens if max_tokens is not None else config.max_tokens,
    }

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    try:
        response = httpx.post(url, json=payload, headers=headers, timeout=config.timeout)
        response.raise_for_status()
        body = response.json()
        content = body["choices"][0]["message"]["content"]
        if not content:
            raise LLMCallError(
                f"Empty response (tier={config.tier.value}, model={config.model_name})"
            )
        return content.strip()

    except (LLMNotConfiguredError, LLMCallError):
        raise

    except httpx.HTTPStatusError as exc:
        raise LLMCallError(
            f"HTTP {exc.response.status_code} (tier={config.tier.value}, "
            f"model={config.model_name}): {exc.response.text[:500]}",
            status_code=exc.response.status_code,
        ) from exc

    except Exception as exc:
        raise LLMCallError(
            f"Provider call failed (tier={config.tier.value}, "
            f"model={config.model_name}): {exc}"
        ) from exc


def stream_provider(
    config: ModelConfig,
    messages: list[dict],
    temperature: float | None = None,
    max_tokens: int | None = None,
) -> Iterator[str]:
    """Yield answer deltas from an OpenAI-compatible SSE response."""
    api_key = config.api_key or os.getenv("OPENAI_COMPATIBLE_API_KEY", "").strip()
    if not api_key:
        raise LLMNotConfiguredError(
            f"API key not configured for tier {config.tier.value}, "
            f"provider {config.provider}"
        )

    url = f"{config.base_url.rstrip('/')}/chat/completions"
    payload = {
        "model": config.model_name,
        "messages": messages,
        "temperature": temperature if temperature is not None else config.temperature,
        "max_tokens": max_tokens if max_tokens is not None else config.max_tokens,
        "stream": True,
    }
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
    }

    received_content = False
    try:
        with httpx.stream(
            "POST", url, json=payload, headers=headers, timeout=config.timeout
        ) as response:
            response.raise_for_status()
            for line in response.iter_lines():
                if not line or line.startswith(":") or not line.startswith("data:"):
                    continue
                data = line[len("data:"):].strip()
                if data == "[DONE]":
                    break
                try:
                    body = json.loads(data)
                    content = body["choices"][0].get("delta", {}).get("content")
                except (json.JSONDecodeError, KeyError, IndexError, TypeError) as exc:
                    raise LLMCallError(
                        f"Invalid streaming response (tier={config.tier.value}, "
                        f"model={config.model_name})"
                    ) from exc
                if isinstance(content, str) and content:
                    received_content = True
                    yield content

        if not received_content:
            raise LLMCallError(
                f"Empty streaming response (tier={config.tier.value}, "
                f"model={config.model_name})"
            )
    except (LLMNotConfiguredError, LLMCallError):
        raise
    except httpx.HTTPStatusError as exc:
        raise LLMCallError(
            f"HTTP {exc.response.status_code} (tier={config.tier.value}, "
            f"model={config.model_name}): {exc.response.text[:500]}",
            status_code=exc.response.status_code,
        ) from exc
    except Exception as exc:
        raise LLMCallError(
            f"Provider stream failed (tier={config.tier.value}, "
            f"model={config.model_name}): {exc}"
        ) from exc
