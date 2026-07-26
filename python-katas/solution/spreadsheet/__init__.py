"""Spreadsheet — a mini formula engine.

# The scenario

A spreadsheet holds cells addressed like ``A1``. A cell contains either a literal number or a
*formula* (``"=A1+B2*3"``) that references other cells. Reading a cell computes its current value;
because a formula reads other cells, changing one cell is transparently reflected in everything that
depends on it. A formula that (directly or indirectly) refers back to its own cell is a **circular
reference** and must be rejected rather than looping forever.

# The design: lazy recompute + DFS cycle detection

Values are computed **on read**, not pushed on write. ``sheet["C1"]`` evaluates C1's formula, which
recursively evaluates the cells it names, and so on. That means "setting a cell recomputes its
dependents" falls out for free — the dependents simply re-read the new value next time they are
evaluated, with no dependency graph to maintain on write.

The one thing lazy evaluation must guard is a cycle. Evaluation carries the set of cells currently
being computed down the recursion; if it reaches a cell already in that set, the formulas form a
loop and it raises [`CycleError`] instead of recursing forever. (An eager engine would instead keep
an explicit dependency DAG and topologically sort it on every write — faster reads, more
bookkeeping, and it must reject the cycle at write time. Lazy-with-DFS is the simpler model, and
the one to reach for first.)

``__getitem__``/``__setitem__`` make the sheet read and write like the grid it models
(``sheet["A1"] = 5``); the formula grammar is a small recursive-descent evaluator with the usual
``* /`` before ``+ -`` precedence.
"""

from __future__ import annotations

import re

_REF = re.compile(r"^[A-Z]+[0-9]+$")
_TOKEN = re.compile(r"\s*(?:(?P<num>\d+(?:\.\d+)?)|(?P<ref>[A-Z]+[0-9]+)|(?P<op>[+\-*/]))")


class CycleError(Exception):
    """Raised when a formula depends on itself, directly or transitively."""


class Sheet:
    """A grid of cells, each a literal number or a ``=formula`` string."""

    def __init__(self) -> None:
        self._cells: dict[str, float | str] = {}

    def __setitem__(self, ref: str, content: float | int | str) -> None:
        if not _REF.match(ref):
            raise ValueError(f"bad cell reference: {ref!r}")
        if isinstance(content, bool):  # bool is an int subclass; reject to avoid surprises
            raise TypeError("cell content must be a number or a formula string")
        if isinstance(content, (int, float)):
            self._cells[ref] = float(content)
        elif isinstance(content, str) and content.startswith("="):
            self._cells[ref] = content
        else:
            raise ValueError(f"cell content must be a number or a =formula, got {content!r}")

    def __getitem__(self, ref: str) -> float:
        return self._value(ref, frozenset())

    def __contains__(self, ref: str) -> bool:
        return ref in self._cells

    def _value(self, ref: str, visiting: frozenset[str]) -> float:
        if ref in visiting:
            raise CycleError(f"circular reference through {ref}")
        content = self._cells.get(ref)
        if content is None:
            return 0.0  # empty cell reads as zero
        if isinstance(content, float):
            return content
        return self._eval(content[1:], visiting | {ref})

    # formula grammar: expr := term (('+'|'-') term)* ; term := factor (('*'|'/') factor)*

    def _eval(self, formula: str, visiting: frozenset[str]) -> float:
        tokens = self._tokenize(formula)
        pos = 0

        def expr() -> float:
            nonlocal pos
            value = term()
            while pos < len(tokens) and tokens[pos] in ("+", "-"):
                op = tokens[pos]
                pos += 1
                rhs = term()
                value = value + rhs if op == "+" else value - rhs
            return value

        def term() -> float:
            nonlocal pos
            value = factor()
            while pos < len(tokens) and tokens[pos] in ("*", "/"):
                op = tokens[pos]
                pos += 1
                rhs = factor()
                value = value * rhs if op == "*" else value / rhs
            return value

        def factor() -> float:
            nonlocal pos
            if pos >= len(tokens):
                raise ValueError(f"unexpected end of formula: ={formula}")
            tok = tokens[pos]
            pos += 1
            if _REF.match(tok):
                return self._value(tok, visiting)
            return float(tok)

        result = expr()
        if pos != len(tokens):
            raise ValueError(f"trailing tokens in formula: ={formula}")
        return result

    @staticmethod
    def _tokenize(formula: str) -> list[str]:
        tokens: list[str] = []
        pos = 0
        while pos < len(formula):
            m = _TOKEN.match(formula, pos)
            if not m or m.end() == pos:
                raise ValueError(f"bad formula: ={formula}")
            tokens.append(m.group("num") or m.group("ref") or m.group("op"))
            pos = m.end()
        return tokens
