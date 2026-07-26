# Tick Parser

> Decode a market-data quote line off the wire, millions per second, **without allocating** — the parsed quote borrows straight out of the input buffer.

## The problem

A feed handler reads pipe-delimited quote lines: `"LIV-MUN|1.95|2.05|1234"` — symbol, bid, ask, and a
sequence number. Turn one line into a `Quote`. The catch: on the hot path you must not copy the
symbol into a `String` — the `Quote` should **borrow** the symbol out of the input line.

## Requirements

- `parse(line)` returns `Ok(Quote)` for a well-formed `symbol|bid|ask|seq` line.
- `Quote::symbol` **borrows** the input (`&str`), it does not own a `String`.
- Exactly four `|`-separated fields, else `Err(WrongFieldCount { expected, got })`.
- Empty symbol → `Err(EmptySymbol)`; unparseable bid/ask/seq → the matching `Invalid*` variant
  (`seq` is an unsigned integer, so `-1` is invalid).

## What you implement

- `fn parse(line: &str) -> Result<Quote<'_>, ParseError>`

`Quote<'a>` and `ParseError` are provided verbatim. You write `parse`.

## The real challenge

- **Lifetimes are the point.** `Quote<'a>` borrows the line; the signature ties the quote's lifetime
  to the input so the borrow checker *guarantees at compile time* a `Quote` can never outlive its
  buffer. No runtime cost, no dangling reference — the trade a C++ `string_view` won't give you.
- **`&str` vs `String`.** Borrowing is free; a `String` symbol heap-allocates and copies every line.
  At feed volume that allocator traffic is the bottleneck. Reach for the owning type only when you
  must keep the data past the buffer.
- **Error mapping.** `str::parse` yields `std`'s `ParseFloatError`/`ParseIntError`; map them into your
  domain `ParseError` with `map_err` + `?` rather than leaking `std`'s types.
- **Money angle.** A mis-parsed price is a wrong quote acted on; a per-field `String` alloc at
  millions of lines/sec is the latency killer this design avoids.

## Run

There are no tests here — writing them is part of the exercise. Add a `#[cfg(test)] mod tests` in this
file (include an assertion that the symbol really borrows the input, e.g. via
`std::ptr::eq(q.symbol.as_ptr(), line.as_ptr())`), then:

```
cd rust-katas && cargo test -p practice tickparser
```

## Reference

Worked solution: `rust-katas/solution/src/tickparser/`.

Extension: parse from `&[u8]` instead of `&str` (raw wire bytes) and return `Quote<'a>` borrowing the
byte slice; then benchmark against a `String`-owning version to see the allocation cost you avoided.

Background: [The Rust Book — Validating References with Lifetimes](https://doc.rust-lang.org/book/ch10-03-lifetime-syntax.html).
