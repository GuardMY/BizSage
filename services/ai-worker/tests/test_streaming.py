from app.llm import LLMCallError
from app.model_routing.models import ModelConfig, ModelTier
from app.model_routing.providers import stream_provider
from app.model_routing.router import ModelRouter
from app.reasoning_checks.retry import run_with_retry_stream


def _config(model_name: str) -> ModelConfig:
    return ModelConfig(
        tier=ModelTier.BALANCED,
        provider="test",
        base_url="https://provider.test/v1",
        api_key="sk-test",
        model_name=model_name,
    )


def test_provider_parses_openai_compatible_deltas(monkeypatch):
    class Response:
        status_code = 200
        text = ""

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

        def raise_for_status(self):
            return None

        def iter_lines(self):
            yield 'data: {"choices":[{"delta":{"reasoning_content":"hidden"}}]}'
            yield 'data: {"choices":[{"delta":{"content":"经营"}}]}'
            yield 'data: {"choices":[{"delta":{"content":"诊断"}}]}'
            yield "data: [DONE]"

    monkeypatch.setattr(
        "app.model_routing.providers.httpx.stream",
        lambda *_args, **_kwargs: Response(),
    )

    chunks = list(stream_provider(_config("primary"), [{"role": "user", "content": "test"}]))
    assert chunks == ["经营", "诊断"]


def test_router_resets_partial_output_before_provider_failover(monkeypatch):
    router = ModelRouter()
    router._tiers = {ModelTier.BALANCED: [_config("primary"), _config("backup")]}

    def fake_stream(config, *_args, **_kwargs):
        if config.model_name == "primary":
            yield "discarded"
            raise LLMCallError("primary disconnected")
        yield "replacement"

    monkeypatch.setattr("app.model_routing.router.stream_provider", fake_stream)
    events = list(router.stream([{"role": "user", "content": "test"}]))

    assert events == [
        {"event": "delta", "text": "discarded"},
        {"event": "reset", "reason": "PROVIDER_FAILOVER"},
        {"event": "delta", "text": "replacement"},
    ]


def test_router_clears_partial_output_when_all_providers_fail(monkeypatch):
    router = ModelRouter()
    router._tiers = {ModelTier.BALANCED: [_config("only-model")]}

    def broken_stream(*_args, **_kwargs):
        yield "partial"
        raise LLMCallError("connection dropped")

    monkeypatch.setattr("app.model_routing.router.stream_provider", broken_stream)
    events = []
    try:
        events.extend(router.stream([{"role": "user", "content": "test"}]))
    except LLMCallError:
        pass

    assert events[-1] == {"event": "reset", "reason": "PROVIDER_FAILOVER"}


def test_streaming_retry_clears_failed_candidate_and_finishes_with_valid_result():
    calls = 0

    def stream_candidate(_messages):
        nonlocal calls
        calls += 1
        text = "保证盈利。" if calls == 1 else "经营建议：控制成本。免责声明：仅供参考。"
        yield {"event": "delta", "text": text}

    events = list(run_with_retry_stream(
        llm_stream_fn=stream_candidate,
        messages=[{"role": "user", "content": "test"}],
        evidence=[{"id": "k1", "content": "控制成本有助于改善经营。"}],
        max_retries=1,
    ))

    assert [event["event"] for event in events] == [
        "delta", "status", "reset", "status", "delta", "status", "result"
    ]
    assert events[2]["reason"] == "SELF_CHECK_RETRY"
    assert events[3]["state"] == "retrying"
    assert events[-1]["answer"].startswith("经营建议")
    assert events[-1]["selfCheckStatus"] == "PASSED"


def test_streaming_retry_exhaustion_replaces_candidate_with_uncertain_response():
    def always_bad(_messages):
        yield {"event": "delta", "text": "股票推荐，保证盈利。"}

    events = list(run_with_retry_stream(
        llm_stream_fn=always_bad,
        messages=[{"role": "user", "content": "test"}],
        evidence=[{"id": "k1", "content": "generic evidence"}],
        max_retries=0,
    ))

    assert [event["event"] for event in events] == [
        "delta", "status", "reset", "delta", "result"
    ]
    assert "信息存疑" in events[-2]["text"]
    assert events[-1]["selfCheckStatus"] == "SELF_CHECK_FAILED"
