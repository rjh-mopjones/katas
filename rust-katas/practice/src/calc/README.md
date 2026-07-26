# Calculator

> Turn a string like `"1 + 2 * (3 - 4) / 5"` into the number it means — a hand-written recursive-descent evaluator, no parser crates.

## The problem

Write `eval(input)` that parses and evaluates an arithmetic expression over `f64`. Support `+ - * /`,
parentheses, unary minus, integer and decimal literals, and arbitrary whitespace. The catch is
**precedence**: `1 + 2 * 3` is `7`, not `9` — `* /` bind tighter than `+ -` — and every operator is
left-associative, so `10-2-3` is `(10-2)-3 == 5`. You cannot get that right in a single left-to-right
pass; you have to build structure first.

## Requirements

- `eval(input)` returns `Ok(f64)` for a well-formed expression.
- `* /` bind tighter than `+ -`; all operators are **left-associative** (`10-2-3 == 5`, `8/2/2 == 2`).
- Parentheses override precedence: `(1+2)*3 == 9`.
- Unary minus works on a literal (`-5`) and after an operator (`2 + -3 == -1`).
- Integer and decimal literals; whitespace anywhere is ignored (`  1  +  2  == 3`).
- Errors, not panics: division by zero → `DivideByZero`; a stray character → `UnexpectedChar(c)`;
  input ending early (`1 +`) → `UnexpectedEof`; leftover input (`1+2)`, `1 2`) → `TrailingInput`;
  empty input → an error.

## What you implement

- `fn eval(input: &str) -> Result<f64, CalcError>`

`Expr`, `Op`, and `CalcError` (with `Display` + `Error` impls) are provided verbatim. You write
`eval` — and the tokenizer/parser it needs internally.

## The real challenge

- **Enums + `match` model the grammar.** `Op` is the operator; `Expr` is the parsed tree. Evaluating
  is a `match` over `Expr` that recurses into children — the shape *is* the meaning.
- **`Box` for the recursive AST.** `Expr::Bin` holds two child `Expr`s, so a plain `enum Expr { Bin {
  lhs: Expr, rhs: Expr } }` has infinite size and won't compile. `Box<Expr>` puts the child on the
  heap and stores a fixed-size pointer — the idiomatic way to build a recursive type in Rust.
- **Recursive descent + precedence.** Write one function per precedence level (`expr` for `+ -`,
  `term` for `* /`, `factor` for numbers / parens / unary minus). Each parses the level above it and
  loops while it sees *its* operators — the loop is what makes them left-associative, and precedence
  falls out of which function calls which. Tokenize first so you match on tokens, not chars.
- **`?` error flow.** Every step returns `Result<_, CalcError>` and uses `?` to bubble the first
  failure out. Leftover tokens after the top-level parse mean `TrailingInput`; running out mid-parse
  means `UnexpectedEof`.

## Run

There are no tests here — writing them is part of the exercise. Add a `#[cfg(test)] mod tests` in this
file (cover precedence, associativity, parens, unary minus, and each error variant), then:

```
cd rust-katas && cargo test -p practice calc
```

## Reference

Worked solution: `rust-katas/solution/src/calc/`.

Extension: add a `^` power operator (right-associative — note how that changes the loop vs. the
recursion); support variables via an environment map; or rewrite the parser as a **Pratt** (top-down
operator-precedence) parser and compare it to the function-per-level approach.

Background: [The Rust Book — Enums and Pattern Matching](https://doc.rust-lang.org/book/ch06-00-enums.html).
