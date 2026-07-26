# Spreadsheet

> A mini spreadsheet / formula engine — cells hold numbers or formulas referencing other cells, and changing one cell updates everything that depends on it.

## The problem

Build the engine behind a spreadsheet. A cell is addressed like `A1` and holds either a literal
number or a **formula** — `"=A1+B2*3"` — that references other cells. Reading a cell computes its
current value; because formulas read other cells, editing one cell is reflected in everything
downstream. A formula that refers back to its own cell (directly or through a chain) is a **circular
reference** and must be rejected, not looped on forever.

## Requirements

- `sheet["A1"] = 5` stores a literal; `sheet["C1"] = "=A1+B2"` stores a formula.
- `sheet["C1"]` returns the **computed** value; an empty cell reads as `0.0`.
- Formulas support `+ - * /` with normal precedence (`*`/`/` before `+`/`-`) and cell references.
- Editing a cell is reflected in its dependents on the next read (`A2="=A1*2"`; change `A1`, and `A2`
  changes).
- A direct (`A1="=A1+1"`) or indirect (`A1="=B1"`, `B1="=A1"`) cycle raises `CycleError` on read.
- An invalid reference (e.g. lower-case `a1`) is rejected.

## What you implement

- `Sheet.__setitem__(ref, content)` and `Sheet.__getitem__(ref) -> float` (and `__contains__`).

`CycleError` is provided. You design the storage and evaluation.

## The real challenge

- **The Python data model.** Implement `__getitem__`/`__setitem__` so the sheet reads and writes like
  the grid it models (`sheet["A1"] = 5`) — that's the idiomatic API, not `get`/`set` methods.
- **Lazy recompute beats a dependency graph.** Compute values *on read*: reading a formula recursively
  reads the cells it names. "Dependents update on edit" then falls out for free — no write-time DAG to
  maintain. (The eager alternative — keep a dependency DAG, topologically sort on every write — is
  faster to read but more bookkeeping and must catch cycles at write time. Know the trade-off.)
- **Cycle detection is a DFS invariant.** Carry the set of cells currently being evaluated down the
  recursion; if you reach one already in it, raise `CycleError` instead of recursing forever.
- **A small recursive-descent evaluator** gives you `* /` before `+ -` cleanly (`expr → term → factor`).

## Run

There are no tests here — writing them is part of the exercise. Add a `test_spreadsheet.py` in this
directory (cover precedence, transitive updates, and both cycle shapes), then:

```
cd python-katas && .venv/bin/pytest practice/spreadsheet
```
Compare against the reference: `.venv/bin/pytest solution/spreadsheet`.

## Reference

Worked solution: `solution/spreadsheet/`.

Extension: add parentheses and functions (`=SUM(A1:A3)`); or switch to an **eager** engine with an
explicit dependency DAG + topological recompute, and detect cycles at write time.

Background: [Python data model — `__getitem__`/`__setitem__`](https://docs.python.org/3/reference/datamodel.html#emulating-container-types).
