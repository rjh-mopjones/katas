# Order Handle

> An intrusive reference-counted handle to a pooled `Order` — the reference every matching-engine subsystem holds, that returns the order to its pool when the last one drops.

## The problem

A matching engine keeps live orders in a fixed `OrderPool` and hands the *same* `Order` to several
subsystems at once: it rests in a **price level**'s queue, it is looked up by client id in the
**id-index**, and the **risk engine** holds it to track exposure. Each is a separate owner. The order
must stay alive while *any* owner still references it, and return to the pool the instant the *last*
owner lets go. An `OrderHandle` is that owning reference — an `intrusive_ptr` written by hand. The
reference count lives *inside* the `Order` (`ref_count`); a count of 0 means the slot is free and a
future `acquire()` may reuse it.

## Requirements

- A default `OrderHandle` is null: `!h`, `h.use_count() == 0`, `h.get() == nullptr`.
- `pool.acquire(id, price, qty)` checks out a free slot (its `ref_count` becomes 1) and returns a
  handle; `pool.live()` counts checked-out slots. `acquire()` throws `std::runtime_error` when the
  pool is exhausted.
- **Copy** shares ownership: both handles point at the same `Order` and `use_count()` rises. Dropping
  one copy lowers the count; dropping the *last* handle reclaims the slot (`live()` falls).
- **Move** transfers ownership: the source becomes null (`use_count() == 0`), the destination's count
  is unchanged.
- **Copy-assignment** must be correct under self-assignment (`h = h;`) and when both sides already
  share the *same* order — neither may free the order still in use. Assigning a *different* order
  releases the old one (its slot reclaimed) and shares the new.
- Reclaim happens **exactly once**: no leaked slot, no double-free.

## What you implement

The public API of `OrderHandle`:

- `OrderHandle() noexcept` and `~OrderHandle()`
- copy constructor + copy assignment (share ownership)
- move constructor + move assignment (transfer ownership)
- `Order* get() const noexcept`, `Order& operator*() const noexcept`, `Order* operator->() const noexcept`
- `explicit operator bool() const noexcept`, `long use_count() const noexcept`, `void reset() noexcept`
- the private friend constructor `OrderHandle(Order*)` that `OrderPool::acquire` uses to adopt a slot

`Order` and the whole `OrderPool` (a working fixture — a linear scan over a `std::vector<Order>`) are
provided verbatim. You design only the handle's ownership mechanics.

## The real challenge

- **Rule of five on a shared resource.** You declare a destructor and custom copy/move, so reason
  about all five special members. Copy bumps the count; move steals and nulls the source.
- **Self-assignment safety.** `h = h;` — and assigning between two handles to the *same* order — must
  not free the order you keep pointing at. Bump the incoming count *before* releasing your own (or
  guard `this == &other`), never the other way round: releasing first is a use-after-free.
- **Intrusive, not external, count.** The count lives in the `Order`, touched *only* by the handle.
  No control block, no allocation — but the discipline is yours: keep the count exactly balanced.
- **Reclaim exactly once.** Exactly one 0-transition per order. A missed decrement leaks a pool slot;
  a double decrement hands the slot to a future `acquire()` while an owner still holds it.
- **Money angle.** If an order is recycled while it still rests in the book, a later `acquire()`
  overwrites that slot with a new client's order — the next match fills a *recycled* slot: phantom
  fills, wrong price, exposure booked to the wrong account. A leaked slot exhausts the pool and
  `acquire()` starts throwing mid-session.
- **Non-atomic caveat.** `ref_count` is a plain `int` — this kata is single-threaded (one matching
  thread owns the book). A pool shared across threads would need `std::atomic<int>` (acquire/release
  on the decrement-to-zero) and a lock-free free-list instead of the linear slot scan.

## Run

There are no tests here — writing them is part of the exercise. Add your own
`order_handle_test.cpp` in this directory (use `../../solution/common/harness.hpp`), wire it into
CMake, then:

```
cd cpp-katas && ctest --test-dir build -R order_handle
```

## Reference

Worked solution: `cpp-katas/solution/order_handle/`.

Extension: make `ref_count` a `std::atomic<int>` and the pool a lock-free free-list so handles are
safe to share across threads; or add an `intrusive_ptr`-style aliasing constructor that promotes a
raw `Order*` back to an owning handle.

Background: [cppreference — rule of five](https://en.cppreference.com/w/cpp/language/rule_of_three)
and [Boost.SmartPtr — `intrusive_ptr`](https://www.boost.org/doc/libs/release/libs/smart_ptr/doc/html/smart_ptr.html#intrusive_ptr).
