"""Key-Value Store — an in-memory mini-Redis.

# The scenario

A process-local key-value store: ``set``/``get``/``delete`` over string keys, with two features that
make it more than a plain ``dict``. Keys can carry a **TTL** — after which they vanish — and a block
of writes can be grouped into a **transaction** that either commits as a whole or leaves the store
untouched. Think of it as the engine behind a cache sidecar or a feature-flag service: the API is
tiny, but expiry and atomicity are where the interesting decisions live.

# The design: a unit of work (buffer-then-commit)

``transaction()`` is a **context manager** implementing the unit-of-work pattern. Writes issued
inside the ``with`` block (``set``/``delete``) are not applied to the live store — they are recorded
in a pending buffer. On a clean exit the buffer is replayed against the store atomically; if the
block raises, the buffer is dropped and the store is exactly as it was (rollback), and the exception
propagates. This mirrors Redis ``MULTI``/``EXEC``: commands are *queued* while the transaction is
open and only take effect on ``EXEC``. Consequently reads inside the block see the committed
(pre-transaction) state, not the not-yet-applied buffer.

Python offers two idioms for writing a context manager, and both would serve here:

* a **class** with ``__enter__``/``__exit__`` — ``__enter__`` opens the buffer, ``__exit__`` reads
  its ``exc_type`` argument (``None`` ⇒ commit, otherwise discard) and returns ``False`` so any
  exception continues to propagate rather than being swallowed;
* [`contextlib.contextmanager`] on a generator — the code before ``yield`` is the setup, a
  ``try/except/finally`` around the ``yield`` is the teardown, and *not* re-raising is the analog of
  returning ``False``.

This module uses the ``@contextmanager`` generator form: the commit/rollback logic is a linear
``try/else`` around a single ``yield``, which reads more directly than spreading setup and teardown
across two methods. The class form is the one to reach for when the manager must carry state or be
re-entered, e.g. nested/savepoint transactions.

# Expiry: lazy, not swept

TTLs are enforced **lazily** — a key's expiry is checked when it is *accessed* (``get``,
``__contains__``, ``__len__``), and expired entries are pruned on the way past. There is no
background thread scanning for dead keys. The alternative is **active** expiry: a sweeper (a timer
or Redis-style random sampling) that reclaims memory proactively. Lazy expiry is simpler and costs
nothing when idle, but a large TTL'd key that is never read again lingers in memory until touched —
which is why real Redis combines both. For an in-process store, lazy alone is the sane default.

Time comes from an injected ``clock`` (default [`time.monotonic`]) so tests advance a fake clock
rather than sleeping — deterministic, and monotonic time can't jump backwards under an NTP step the
way wall-clock can.
"""

from __future__ import annotations

import time
from collections.abc import Callable, Iterator
from contextlib import contextmanager


class KVStore:
    """An in-memory key-value store with per-key TTL and buffered transactions."""

    def __init__(self, clock: Callable[[], float] = time.monotonic) -> None:
        self._clock = clock
        # value + optional absolute expiry deadline (in the clock's units)
        self._data: dict[str, tuple[object, float | None]] = {}
        # when a transaction is open, writes land here as key -> ("set", value, ttl) | ("del",)
        self._pending: dict[str, tuple[object, ...]] | None = None

    def set(self, key: str, value: object, ttl: float | None = None) -> None:
        if self._pending is not None:
            self._pending[key] = ("set", value, ttl)
            return
        self._store(key, value, ttl)

    def get(self, key: str) -> object | None:
        entry = self._live_entry(key)
        return None if entry is None else entry[0]

    def delete(self, key: str) -> bool:
        if self._pending is not None:
            existed = self._live_entry(key) is not None
            self._pending[key] = ("del",)
            return existed
        if self._live_entry(key) is None:
            return False
        del self._data[key]
        return True

    def __contains__(self, key: str) -> bool:
        return self._live_entry(key) is not None

    def __len__(self) -> int:
        # touch every key so expired ones are pruned, then count what survives
        return sum(1 for key in list(self._data) if self._live_entry(key) is not None)

    @contextmanager
    def transaction(self) -> Iterator[None]:
        """A unit of work: buffer writes, commit atomically on clean exit, roll back on error.

        Nested transactions are rejected — a savepoint model would be the extension.
        """
        if self._pending is not None:
            raise RuntimeError("nested transactions are not supported")
        self._pending = {}
        try:
            yield
        except BaseException:
            # rollback: drop the buffer and let the exception propagate (the @contextmanager
            # analog of __exit__ returning False)
            self._pending = None
            raise
        else:
            buffered, self._pending = self._pending, None
            self._commit(buffered)

    def _commit(self, buffered: dict[str, tuple[object, ...]]) -> None:
        for key, op in buffered.items():
            if op[0] == "set":
                self._store(key, op[1], op[2])  # type: ignore[misc]
            else:
                self._data.pop(key, None)

    def _store(self, key: str, value: object, ttl: float | None) -> None:
        deadline = None if ttl is None else self._clock() + ttl
        self._data[key] = (value, deadline)

    def _live_entry(self, key: str) -> tuple[object, float | None] | None:
        """Return the entry for ``key`` if present and unexpired, pruning it if it has expired."""
        entry = self._data.get(key)
        if entry is None:
            return None
        _, deadline = entry
        if deadline is not None and self._clock() >= deadline:
            del self._data[key]
            return None
        return entry
