# Python Katas

Senior-Python katas framed as **real systems** (LLD-style scenarios, like the Java module) — a
spreadsheet engine, a mini-Redis, a middleware pipeline, a file system — **not** "implement a language
feature from scratch" drills. Each is a small system with behavioural rules, and a distinctive Python
idiom (decorators, context managers, generators, asyncio, the data model, Protocols, `match`) is the
elegant way to build it — the idiom lives in *The real challenge*, not the headline.

> **Write your own tests.** The `practice/` side ships *without* tests on purpose — designing them is
> part of the exercise. The `solution/` twin carries a pytest reference suite to compare against.

## Layout

Two mirrored trees; each kata is a package at the same path in both.

```
python-katas/
├── solution/   full reference implementations + pytest suites  (always GREEN)
└── practice/   the same systems as `raise NotImplementedError` skeletons + per-kata READMEs;  NO tests
```

Testing is **pytest** in a `.venv` (matching the `postgres-katas` precedent); async katas are tested
with `asyncio.run(...)` in plain test functions, so there's **no `pytest-asyncio` dependency**. Lint
is `ruff`. Standard library only.

## Katas

| # | Kata | The scenario (a real system) | Python idiom (the real challenge) |
|---|------|------------------------------|-----------------------------------|
| 1 | [`spreadsheet`](practice/spreadsheet/) | A formula engine: cells hold numbers or `=A1+B2` formulas; edits update dependents; cycles rejected. | Data model (`__getitem__`/`__setitem__`), lazy recompute, DFS cycle detection |
| 2 | [`kvstore`](practice/kvstore/) | An in-memory key-value store (mini-Redis): TTL expiry + `transaction()` (commit/rollback). | Context managers (unit-of-work), dunder container, injected clock |
| 3 | [`feedstats`](practice/feedstats/) | A live market-data aggregator: tick stream → OHLC+VWAP bars, lazily. *(trading)* | Generators / lazy streaming, `yield`, O(1) memory |
| 4 | [`middleware`](practice/middleware/) | A web middleware pipeline: compose auth/logging/rate-limit around a handler, short-circuiting. | Decorators & closures, `functools.reduce`, the onion model |
| 5 | [`crawler`](practice/crawler/) | An async fetch orchestrator: fetch N sources with a concurrency cap, retries, ordered results. | asyncio: `Semaphore`, `gather`, cancellation; the GIL note |
| 6 | [`workflow`](practice/workflow/) | An order-lifecycle state machine with guarded transitions. *(trading)* | Enums + `match`/`case` structural pattern matching, frozen dataclasses |
| 7 | [`notifier`](practice/notifier/) | A notification service: topic subscriptions + filters + pluggable channels. | Protocols (structural typing) + the Observer pattern |
| 8 | [`filesystem`](practice/filesystem/) | An in-memory file system: `mkdir -p`, `write`, `read`, `ls`, `find`, `mv`. | Recursion over a tree + dunder (`__contains__`) + dataclasses |

Each `practice/<kata>/README.md` is the prompt: scenario → problem → requirements (the system's rules)
→ what you implement → the real challenge → run → reference + extension.

## Commands

Requires Python 3.11+ and `ruff`. A `Makefile` wraps these.

```bash
cd python-katas
make venv         # create .venv + pip install pytest  (once)
make solution     # pytest solution -q — reference suite, GREEN
make practice     # compileall + import — skeletons parse/import (NotImplementedError only when called)
make lint         # ruff check solution practice
.venv/bin/pytest solution/spreadsheet -q     # one kata
```
