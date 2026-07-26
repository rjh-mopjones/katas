"""Middleware pipeline — composing cross-cutting layers around a request handler.

# The scenario

A web framework routes a request to a handler that returns a response. Around that handler you want
cross-cutting layers — authentication, logging, rate-limiting — that run on every request without
each handler re-implementing them. Each layer sits *around* the next: it can inspect or modify the
request on the way in, call the rest of the pipeline, and inspect or modify the response on the way
out — or **short-circuit**, returning a response itself and never calling the layers beneath it (an
auth layer rejecting a request with ``401`` is the canonical case; the handler never runs).

# The design: middleware as higher-order functions

A ``Handler`` is ``Request -> Response``. A ``Middleware`` is one level up: ``Handler -> Handler`` —
it takes *the next handler* and returns a *new* handler that wraps it. That closure captures
``next_handler`` and decides what to do with it:

    def logging(next_handler: Handler) -> Handler:
        def wrapped(request: Request) -> Response:
            log.append(("in", request.path))
            response = next_handler(request)      # call through
            log.append(("out", response.status))
            return response
        return wrapped

    def require_auth(next_handler: Handler) -> Handler:
        def wrapped(request: Request) -> Response:
            if "authorization" not in request.headers:
                return Response(401)               # short-circuit — next_handler never runs
            return next_handler(request)
        return wrapped

This is exactly what a Python **decorator** is: ``@logging`` above a function replaces it with
``logging(function)``. A middleware stack is decorators applied at runtime to a value rather than at
definition time to a name.

# Composing them: fold the list around the base handler

``compose([m1, m2, m3], handler)`` must make ``m1`` the *outermost* layer, so a request flows
``m1 -> m2 -> m3 -> handler`` and the response flows back ``handler -> m3 -> m2 -> m1`` — the
**onion model**. Building that is a right-to-left fold: start with ``handler``, wrap it in ``m3``,
wrap that in ``m2``, wrap that in ``m1``:

    functools.reduce(lambda nxt, mw: mw(nxt), reversed(middlewares), handler)

``reduce`` walks ``m3, m2, m1`` (the reversed list) and at each step calls ``mw(nxt)``, threading
the accumulated handler inward. ``compose([], handler)`` folds nothing and returns ``handler``
unchanged.

# Alternatives

A **class-based** stack (each middleware an object with a ``__call__(self, request, next)`` method,
assembled by an explicit loop) trades the closures for methods and mutable state — more ceremony,
easier to attach configuration and introspection. **ASGI** is the async form of this same idea: a
middleware is ``async def(scope, receive, send)`` wrapping the next app, awaited rather than called.
The higher-order-function fold shown here is the smallest expression of the pattern, and the one to
reach for first.
"""

from __future__ import annotations

import functools
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
    """Wrap ``handler`` in ``middlewares``, the first being the outermost layer.

    Returns a ``Handler`` in which a request flows through the middlewares left-to-right and the
    response flows back out right-to-left (the onion model). ``compose([], handler)`` returns a
    handler equivalent to ``handler``.
    """
    return functools.reduce(
        lambda nxt, middleware: middleware(nxt),
        reversed(middlewares),
        handler,
    )
