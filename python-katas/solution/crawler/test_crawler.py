import asyncio

import pytest

from . import fetch_all, gather_capped


def test_results_returned_in_input_order_even_when_later_tasks_finish_first():
    # Task i waits on a gate that is only released in reverse order, so url N finishes first and
    # url 0 finishes last — yet the result list must still be in url order. No wall-clock sleeps:
    # a per-url event drives completion deterministically.
    urls = ["u0", "u1", "u2", "u3"]
    gates = {url: asyncio.Event() for url in urls}

    async def fetch(url: str) -> str:
        await gates[url].wait()
        return f"body:{url}"

    async def main() -> list[str]:
        task = asyncio.ensure_future(fetch_all(fetch, urls, limit=4))
        await asyncio.sleep(0)  # let all fetches park on their gate
        for url in reversed(urls):  # release in reverse order
            gates[url].set()
            await asyncio.sleep(0)
        return await task

    assert asyncio.run(main()) == ["body:u0", "body:u1", "body:u2", "body:u3"]


def test_concurrency_cap_is_respected():
    limit = 2
    urls = [f"u{i}" for i in range(8)]
    state = {"current": 0, "max_seen": 0}

    async def fetch(url: str) -> str:
        state["current"] += 1
        state["max_seen"] = max(state["max_seen"], state["current"])
        for _ in range(3):  # force interleaving without real delays
            await asyncio.sleep(0)
        state["current"] -= 1
        return url

    results = asyncio.run(fetch_all(fetch, urls, limit=limit))
    assert results == urls
    assert state["max_seen"] <= limit
    assert state["max_seen"] == limit  # with 8 urls and a cap of 2, we should saturate it


def test_gather_capped_preserves_order_and_cap():
    limit = 3
    state = {"current": 0, "max_seen": 0}

    async def work(n: int) -> int:
        state["current"] += 1
        state["max_seen"] = max(state["max_seen"], state["current"])
        for _ in range(2):
            await asyncio.sleep(0)
        state["current"] -= 1
        return n * n

    async def main() -> list[int]:
        return await gather_capped([work(n) for n in range(6)], limit)

    assert asyncio.run(main()) == [0, 1, 4, 9, 16, 25]
    assert state["max_seen"] <= limit


def test_retry_recovers_after_transient_failures():
    fail_first = 2
    calls = {"u0": 0}

    async def fetch(url: str) -> str:
        calls[url] += 1
        if calls[url] <= fail_first:
            raise RuntimeError("transient")
        return f"ok:{url}"

    result = asyncio.run(fetch_all(fetch, ["u0"], limit=1, retries=2))
    assert result == ["ok:u0"]
    assert calls["u0"] == 3  # 2 failures + 1 success


def test_permanent_failure_with_retries_exhausted_propagates():
    async def fetch(url: str) -> str:
        raise RuntimeError("always down")

    with pytest.raises(RuntimeError, match="always down"):
        asyncio.run(fetch_all(fetch, ["u0"], limit=1, retries=2))


def test_no_retries_by_default_propagates_first_failure():
    calls = {"u0": 0}

    async def fetch(url: str) -> str:
        calls[url] += 1
        raise RuntimeError("boom")

    with pytest.raises(RuntimeError, match="boom"):
        asyncio.run(fetch_all(fetch, ["u0"], limit=1))
    assert calls["u0"] == 1  # no retry


def test_limit_zero_or_negative_raises_value_error():
    async def fetch(url: str) -> str:
        return url

    with pytest.raises(ValueError):
        asyncio.run(fetch_all(fetch, ["u0"], limit=0))
    with pytest.raises(ValueError):
        asyncio.run(fetch_all(fetch, ["u0"], limit=-1))
    with pytest.raises(ValueError):
        asyncio.run(gather_capped([], 0))


def test_empty_url_list_returns_empty():
    async def fetch(url: str) -> str:
        return url

    assert asyncio.run(fetch_all(fetch, [], limit=4)) == []
