"""Middleware pipeline — implement `compose`; the fixtures are provided."""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Request:
    """An inbound request: a path plus header map."""

    path: str
    headers: dict[str, str] = field(default_factory=dict)


@dataclass(frozen=True)
class Response:
    """An outbound response: a status code plus optional body."""

    status: int
    body: str = ""


# A handler turns a request into a response.
Handler = Callable[[Request], Response]
# A middleware takes the next handler and returns a wrapped handler (Handler -> Handler).
Middleware = Callable[[Handler], Handler]


def compose(middlewares: list[Middleware], handler: Handler) -> Handler:
    raise NotImplementedError
