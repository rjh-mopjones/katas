"""Async fetch orchestrator — implement `gather_capped` and `fetch_all`."""

from __future__ import annotations

from collections.abc import Awaitable, Callable
from typing import TypeVar

T = TypeVar("T")


async def gather_capped(aws: list[Awaitable[T]], limit: int) -> list[T]:
    raise NotImplementedError


async def fetch_all(
    fetch: Callable[[str], Awaitable[str]],
    urls: list[str],
    *,
    limit: int,
    retries: int = 0,
) -> list[str]:
    raise NotImplementedError
