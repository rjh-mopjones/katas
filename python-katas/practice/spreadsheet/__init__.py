"""Spreadsheet — a mini formula engine. Implement `Sheet`; `CycleError` is provided."""

from __future__ import annotations


class CycleError(Exception):
    """Raised when a formula depends on itself, directly or transitively."""


class Sheet:
    """A grid of cells, each a literal number or a ``=formula`` string. You design the internals."""

    def __init__(self) -> None:
        raise NotImplementedError

    def __setitem__(self, ref: str, content: float | int | str) -> None:
        raise NotImplementedError

    def __getitem__(self, ref: str) -> float:
        raise NotImplementedError

    def __contains__(self, ref: str) -> bool:
        raise NotImplementedError
