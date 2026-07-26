# Async Fetch Orchestrator

> Fetch from many sources at once — a crawler, or a quote fetcher pulling the current price from every venue an aggregator tracks — fast, but under a hard concurrency cap, with retries, and results in input order.

## The problem

You have a list of URLs (sources) and an async `fetch(url)`. Fetching is I/O-bound, so you want the
fetches to run **concurrently** rather than one-at-a-time — but you cannot fire them all at once. The
far side (and your own socket / file-descriptor budget) only tolerates so many in-flight requests, so
there is a hard **concurrency cap**. Flaky sources should be **retried** a bounded number of times.
And the caller wants results back **in input order**, no matter which fetch happened to finish first.

## Requirements

- `gather_capped(aws, limit)` awaits every awaitable in `aws` with at most `limit` running
  concurrently, and returns results in the **same order** as `aws`.
- If an awaitable raises, the exception propagates and the siblings are cancelled (the default
  `asyncio.gather` behaviour is fine).
- `fetch_all(fetch, urls, limit=, retries=)` calls `fetch(url)` for each url under the concurrency
  cap; on exception it retries up to `retries` more times (`1 + retries` attempts total); if the last
  attempt still fails, that exception propagates. Results come back in **url order**.
- Build `fetch_all` on top of `gather_capped`.
- `limit <= 0` raises `ValueError`.

## What you implement

- `gather_capped(aws: list[Awaitable[T]], limit: int) -> list[T]`
- `fetch_all(fetch, urls, *, limit: int, retries: int = 0) -> list[str]`

## The real challenge

- **asyncio is single-threaded cooperative concurrency.** One event loop runs one coroutine at a
  time; every `await` is a point where the coroutine yields the loop so another can run. While one
  fetch is parked awaiting a socket, the loop drives the others. No preemption, no thread — so no data
  races on plain Python state between awaits. This is why it fits I/O-bound fanout.
- **The cap is an `asyncio.Semaphore`.** Initialise it to `limit`; each task acquires before doing
  work and releases after (`async with sem:`). At most `limit` tasks hold it at once; the rest park on
  `acquire()` until a slot frees.
- **Order preservation is free.** `asyncio.gather` returns results *positionally* — the *i*-th result
  is the *i*-th awaitable — even though completion order is whatever the I/O decides. No sorting or
  index-tagging. (Do it with `as_completed` and you'd have to carry an index and re-sort.)
- **Cancellation / error propagation.** Plain `gather` (no `return_exceptions=True`) raises the first
  exception to the caller and cancels the siblings — one dead source aborts the batch, surfacing the
  failure instead of returning a half-filled list.
- **The GIL note.** asyncio wins by overlapping *waiting*, not by running bytecode in parallel:
  CPython's GIL lets only one thread execute Python bytecode at a time, so a coroutine doing heavy
  compute between awaits blocks the whole loop. asyncio is for **I/O-bound** work; for **CPU-bound**
  fanout reach for `multiprocessing` (separate processes sidestep the GIL); threads help only for
  blocking-I/O libraries and stay GIL-bound for pure-Python compute.

## Run

There are no tests here — writing them is part of the exercise. Add a `test_crawler.py` in this
directory. Test **without** `pytest-asyncio`: write plain `def test_...()` functions that call
`asyncio.run(...)` internally (no `async def` test functions). Cover: results in input order even
when later tasks finish first (drive completion with an `asyncio.Event`, not wall-clock sleeps); the
concurrency cap is respected (a fake `fetch` that bumps a shared counter on entry, tracks the max,
`await asyncio.sleep(0)` to interleave, then decrements — assert `max_seen <= limit`); retries recover
after N transient failures; a permanent failure with retries exhausted propagates; `limit <= 0`
raises `ValueError`. Keep all timing deterministic (`asyncio.sleep(0)` yields the loop without a real
delay).

```
cd python-katas && .venv/bin/pytest practice/crawler
```
Compare against the reference: `.venv/bin/pytest solution/crawler`.

## Reference

Worked solution: `solution/crawler/`.

Extension: add a per-task **timeout** with `asyncio.wait_for` (a slow source is cancelled and counts
as a failure to retry); or replace the eager task list with a bounded **producer/consumer** using
`asyncio.Queue` and a fixed pool of `limit` worker coroutines (backpressure instead of a semaphore).

Background: [asyncio — coroutines and tasks](https://docs.python.org/3/library/asyncio-task.html).
