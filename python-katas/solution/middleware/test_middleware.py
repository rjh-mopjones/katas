from . import Handler, Middleware, Request, Response, compose


def ok_handler(request: Request) -> Response:
    return Response(200, "ok")


def test_empty_pipeline_calls_handler_directly():
    handler = compose([], ok_handler)
    assert handler(Request("/")) == Response(200, "ok")


def test_single_middleware_wraps_handler():
    def stamp(next_handler: Handler) -> Handler:
        def wrapped(request: Request) -> Response:
            response = next_handler(request)
            return Response(response.status, response.body + "!")

        return wrapped

    handler = compose([stamp], ok_handler)
    assert handler(Request("/")) == Response(200, "ok!")


def test_onion_order_outer_in_inner_out():
    log: list[str] = []

    def logger(name: str) -> Middleware:
        def middleware(next_handler: Handler) -> Handler:
            def wrapped(request: Request) -> Response:
                log.append(f"{name}:in")
                response = next_handler(request)
                log.append(f"{name}:out")
                return response

            return wrapped

        return middleware

    handler = compose([logger("m1"), logger("m2"), logger("m3")], ok_handler)
    handler(Request("/"))

    # request flows outer->inner, response flows inner->outer
    assert log == ["m1:in", "m2:in", "m3:in", "m3:out", "m2:out", "m1:out"]


def test_auth_short_circuits_and_handler_never_runs():
    calls: list[Request] = []

    def counting_handler(request: Request) -> Response:
        calls.append(request)
        return Response(200, "ok")

    def require_auth(next_handler: Handler) -> Handler:
        def wrapped(request: Request) -> Response:
            if "authorization" not in request.headers:
                return Response(401)
            return next_handler(request)

        return wrapped

    handler = compose([require_auth], counting_handler)

    blocked = handler(Request("/private"))
    assert blocked == Response(401)
    assert calls == []  # short-circuited: the base handler never ran

    allowed = handler(Request("/private", {"authorization": "token"}))
    assert allowed == Response(200, "ok")
    assert len(calls) == 1


def test_middleware_mutates_request_in_and_response_out():
    def rewrite(next_handler: Handler) -> Handler:
        def wrapped(request: Request) -> Response:
            forwarded = Request(request.path + "/v2", request.headers)
            response = next_handler(forwarded)
            return Response(response.status, f"[{response.body}]")

        return wrapped

    def echo_path(request: Request) -> Response:
        return Response(200, request.path)

    handler = compose([rewrite], echo_path)
    assert handler(Request("/users")) == Response(200, "[/users/v2]")


def test_outer_middleware_can_short_circuit_before_inner_runs():
    reached: list[str] = []

    def gate(next_handler: Handler) -> Handler:
        def wrapped(request: Request) -> Response:
            return Response(503)  # never calls next

        return wrapped

    def inner(next_handler: Handler) -> Handler:
        def wrapped(request: Request) -> Response:
            reached.append(request.path)
            return next_handler(request)

        return wrapped

    handler = compose([gate, inner], ok_handler)
    assert handler(Request("/")) == Response(503)
    assert reached == []  # inner middleware and handler both skipped
