# Exchange Session

> A move-only RAII handle to one venue connection — the object every strategy holds to send orders to an exchange.

## The problem

A trading strategy connects to a venue (LSE, XETRA, CME) and holds that connection as an
`ExchangeSession`. Constructing a session *connects*; destroying it *disconnects* — exactly once.
Sessions are unique, expensive resources: two live handles must never believe they own the same
connection, a connection must never leak (slot never freed), and it must never be closed twice (a
later order sent on a recycled slot is a real bug). Sessions are routinely *moved* — returned from a
factory, stored into a registry `vector`, reassigned — and each move must transfer ownership cleanly.

## Requirements

- Constructing an `ExchangeSession(venue)` opens a connection and makes `is_open()` true.
- Destroying an open session releases the connection **exactly once**.
- `send(order)` transmits while open and increments `sent()`; on a closed (or moved-from) session it
  throws `std::logic_error`.
- The session is **move-only**: copy construction and copy assignment are deleted.
- Moving transfers ownership — the moved-from session is left **closed** (`is_open() == false`,
  `send` throws) and its destruction is a no-op (no double-close).
- Move assignment releases the assignee's current connection **first**, then steals the source's.
- `live_count()` returns the number of sessions currently holding an open connection — it must return
  to zero once every session in a balanced set of scopes has been destroyed.

## What you implement

The public API of `ExchangeSession`:

- `explicit ExchangeSession(std::string venue)`
- `~ExchangeSession() noexcept`
- move constructor + move assignment (copy operations are deleted)
- `void send(const Order& order)`
- `bool is_open() const noexcept`, `const std::string& venue() const noexcept`, `std::size_t sent() const noexcept`
- `static int live_count() noexcept`

`Order` is provided verbatim. You design the fields and the connect/release logic.

## The real challenge

- **Rule of five.** You declare a destructor and custom move operations, so you must reason about all
  five special members. Copy is deleted (one owner); move must leave the source safe to destroy.
- **Move leaves the source inert.** After a move, the source must not release the connection again on
  destruction. Null/flag the moved-from state — the same discipline as `std::unique_ptr`.
- **Move assignment releases first.** `b = std::move(a)` must close `b`'s current connection before
  taking `a`'s, or you leak it. Guard against self-assignment.
- **`noexcept` destructor.** A resource-releasing destructor must not throw: a second exception during
  stack unwinding calls `std::terminate`. Keep the release path noexcept.
- **Money angle.** A leaked session exhausts the venue's connection slots; a double-closed session
  lets a later order ride a recycled connection — dropped or duplicated orders, real P&L.

## Run

There are no tests here — writing them is part of the exercise. Add your own
`exchange_session_test.cpp` in this directory (use `../../solution/common/harness.hpp`), wire it into
CMake, then:

```
cd cpp-katas && ctest --test-dir build -R exchange_session
```

## Reference

Worked solution: `cpp-katas/solution/exchange_session/`.

Extension: make the connection a real owned resource (an `int fd` you `dup`/`close`, or a
heap `Connection*`) so leaks show up under AddressSanitizer, and add a `swap` member + free `swap`
overload so `std::swap` of two sessions is noexcept and allocation-free.

Background: [cppreference — rule of five](https://en.cppreference.com/w/cpp/language/rule_of_three).
