"""Async fetch orchestrator — a bounded-concurrency crawler / quote fetcher.

# The scenario

You need to fetch from many sources at once — say, pull the current quote from every venue an
aggregator tracks, or crawl a list of URLs — and you want it *fast*, so the fetches run
concurrently rather than one after another. But you cannot fire all of them at once: the far side
(or your own file-descriptor / socket budget) only tolerates so many in-flight requests, so there
is a hard **concurrency cap**. Flaky sources should be **retried** a bounded number of times. And
the caller wants results back **in input order**, regardless of which fetch happened to finish
first.

# The design: asyncio + a Semaphore gate

Fetching is I/O-bound — almost all of the wall-clock time is spent waiting on the network, not
burning CPU — so ``asyncio`` is the right tool. asyncio is *single-threaded cooperative
concurrency*: one event loop runs one coroutine at a time, and every ``await`` is a point where
that coroutine voluntarily yields the loop so another can run. While one fetch is parked awaiting a
socket, the loop drives the others. There is no preemption and no thread — so no data races on
plain Python state between awaits — which is what makes this model easy to reason about.

The concurrency cap is an [`asyncio.Semaphore`] initialised to ``limit``. Each task acquires it
before doing work and releases it after; at most ``limit`` tasks hold it at once, so at most
``limit`` fetches are ever in flight. The rest are parked on ``acquire()`` until a slot frees up.

**Order preservation is free.** [`asyncio.gather`] returns results positionally — the *i*-th result
corresponds to the *i*-th awaitable — even though the coroutines complete in whatever order their
I/O finishes. So we gather the wrapped tasks and hand back a list in the original order without
sorting or tagging anything. (If you built the same thing with ``as_completed`` you would have to
carry an index with each task and re-sort at the end.)

**Cancellation / error propagation.** The default ``gather`` (without ``return_exceptions=True``)
propagates the first exception to the caller and cancels the sibling tasks — one dead source aborts
the batch. That is the behaviour we want here: surface the failure rather than silently returning a
half-filled list. (Set ``return_exceptions=True`` only when you want per-item error objects
instead.)

# The GIL note

asyncio is for **I/O-bound** work: the win comes from overlapping *waiting*, not from running Python
bytecode in parallel. CPython's Global Interpreter Lock means only one thread executes Python
bytecode at a time, so neither asyncio nor ``threading`` gives you CPU parallelism — a coroutine
that does heavy computation between awaits blocks the whole loop. For **CPU-bound** fanout reach for
``multiprocessing`` (or a ``ProcessPoolExecutor``), which sidesteps the GIL with separate processes.
Threads sit in between: useful for I/O against blocking (non-async) libraries, but still GIL-bound
for pure-Python compute.
"""

from __future__ import annotations

import asyncio
from collections.abc import Awaitable, Callable
from typing import TypeVar

T = TypeVar("T")


async def gather_capped(aws: list[Awaitable[T]], limit: int) -> list[T]:
    """Await every awaitable in ``aws`` with at most ``limit`` running concurrently.

    Results come back in the **same order** as ``aws`` (``asyncio.gather`` preserves position even
    though completion order is whatever the I/O decides). If one awaitable raises, the exception
    propagates and the siblings are cancelled — the default ``gather`` behaviour.

    ``limit`` must be positive.
    """
    if limit <= 0:
        raise ValueError(f"limit must be positive, got {limit}")

    sem = asyncio.Semaphore(limit)

    async def run(aw: Awaitable[T]) -> T:
        async with sem:
            return await aw

    return list(await asyncio.gather(*(run(aw) for aw in aws)))


async def fetch_all(
    fetch: Callable[[str], Awaitable[str]],
    urls: list[str],
    *,
    limit: int,
    retries: int = 0,
) -> list[str]:
    """Fetch every url concurrently (capped at ``limit``), retrying transient failures.

    ``fetch(url)`` is called for each url. On exception it is retried up to ``retries`` more times
    (so ``1 + retries`` attempts total); if the last attempt still fails, that exception propagates.
    Results are returned in **url order**.

    Built on top of :func:`gather_capped`, which enforces the concurrency cap and the ordering.
    """
    if limit <= 0:
        raise ValueError(f"limit must be positive, got {limit}")

    async def fetch_with_retry(url: str) -> str:
        attempt = 0
        while True:
            try:
                return await fetch(url)
            except Exception:
                if attempt >= retries:
                    raise
                attempt += 1

    return await gather_capped([fetch_with_retry(url) for url in urls], limit)
