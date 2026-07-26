# Middleware Pipeline

> A web-framework middleware pipeline — compose cross-cutting layers (auth, logging, rate-limit) around a request handler, each able to short-circuit.

## The problem

A framework routes a `Request` to a `Handler` (`Request -> Response`). Around that handler you want
cross-cutting layers — authentication, logging, rate-limiting — that run on every request. Each layer
sits *around* the next: it can modify the request on the way in, call the rest of the pipeline, and
modify the response on the way out — or **short-circuit**, returning a response itself without ever
calling the layers beneath it (auth rejecting with `401` is the canonical case; the handler never
runs).

Your job: build `compose`, which assembles a list of middlewares around a base handler into a single
handler.

## Requirements

- `compose([m1, m2, m3], handler)` returns a `Handler` where `m1` is the **outermost** layer: a
  request flows `m1 -> m2 -> m3 -> handler` and the response flows back out `handler -> m3 -> m2 -> m1`
  (the onion model).
- A `Middleware` receives `next_handler` and returns a new handler. It may call
  `next_handler(request)` — optionally modifying the request first and/or the response after — **or**
  short-circuit by returning a `Response` without calling `next_handler` (the layers beneath it never
  run).
- `compose([], handler)` returns a handler equivalent to `handler`.

## What you implement

- `compose(middlewares: list[Middleware], handler: Handler) -> Handler`.

`Request`, `Response`, `Handler`, and `Middleware` are provided as fixtures. You write only `compose`.

## The real challenge

- **Middleware is a higher-order function — `Handler -> Handler`.** Each middleware is a closure that
  captures `next_handler` and returns a *new* handler wrapping it. This is exactly what a Python
  **decorator** is (`@logging` replaces `f` with `logging(f)`); a middleware stack is decorators
  applied at runtime to a value instead of at definition time to a name.
- **Compose by folding the list around the base handler.** Build the onion with a right-to-left fold:
  start with `handler`, wrap it in the last middleware, then the next, ... , finally the first — so the
  first ends up outermost. `functools.reduce` over `reversed(middlewares)` does this in one line.
- **The onion model.** Request flows in through the layers, response flows back out through them in
  reverse — each middleware sees both sides of `next_handler(request)`.
- **Short-circuiting is just not calling `next_handler`.** A layer that returns its own `Response`
  ends the pipeline there; everything inside it is skipped.

## Run

There are no tests here — writing them is part of the exercise. Add a `test_middleware.py` in this
directory (cover onion order, short-circuit with the handler *not* called, an empty pipeline, and a
middleware that mutates the request in and the response out), then:

```
cd python-katas && .venv/bin/pytest practice/middleware
```
Compare against the reference: `.venv/bin/pytest solution/middleware`.

## Reference

Worked solution: `solution/middleware/`.

Extension: add a `@route` decorator registry (map paths to handlers, then wrap each in the pipeline);
or write the **async ASGI** form (`async def middleware(scope, receive, send)` wrapping the next app,
awaited rather than called).

Background: [`functools.reduce`](https://docs.python.org/3/library/functools.html#functools.reduce)
and [primer on decorators](https://docs.python.org/3/glossary.html#term-decorator).
