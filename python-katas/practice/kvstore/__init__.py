"""Key-Value Store — an in-memory mini-Redis. Implement `KVStore`. You design the internals."""

from __future__ import annotations

import time
from collections.abc import Callable
from contextlib import AbstractContextManager


class KVStore:
    """An in-memory key-value store with per-key TTL and buffered transactions."""

    def __init__(self, clock: Callable[[], float] = time.monotonic) -> None:
        raise NotImplementedError

    def set(self, key: str, value: object, ttl: float | None = None) -> None:
        raise NotImplementedError

    def get(self, key: str) -> object | None:
        raise NotImplementedError

    def delete(self, key: str) -> bool:
        raise NotImplementedError

    def __contains__(self, key: str) -> bool:
        raise NotImplementedError

    def __len__(self) -> int:
        raise NotImplementedError

    def transaction(self) -> AbstractContextManager[None]:
        raise NotImplementedError
