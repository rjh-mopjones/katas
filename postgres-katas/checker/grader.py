"""Grading helpers for postgres-katas.

A kata's solution/practice file is a SQL *script* (usually one SELECT, but mutation
katas run several statements then a final SELECT). We execute every statement and
compare the rows of the last result-returning statement against the reference.

Directives live in leading ``-- key: value`` comments of the SOLUTION file:
    -- grade: ordered          row order matters (default: unordered multiset)
    -- mode: mutation          script mutates then SELECTs final state (rolled back)
    -- mode: concurrency       handled by a bespoke test, skipped by the generic one
    -- assert: no-seq-scan      EXPLAIN of the final SELECT must contain no Seq Scan
    -- assert: index=<name>     EXPLAIN must use an Index/Bitmap scan on <name>
"""

from __future__ import annotations

import json
import re
from decimal import Decimal

# ---------------------------------------------------------------------------
# Directives
# ---------------------------------------------------------------------------


def parse_directives(sql: str) -> dict:
    """Read leading ``-- key: value`` comment lines into a dict.

    ``assert`` may appear multiple times, so it is collected into a list.
    """
    directives: dict = {"asserts": []}
    for line in sql.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        if not stripped.startswith("--"):
            break  # directives must be in the leading comment block
        m = re.match(r"--\s*(\w+)\s*:\s*(.+?)\s*$", stripped)
        if not m:
            continue
        key, value = m.group(1).lower(), m.group(2).strip()
        if key == "assert":
            directives["asserts"].append(value)
        else:
            directives[key] = value.lower()
    return directives


# ---------------------------------------------------------------------------
# Statement splitting (handles ' strings, $$ dollar-quotes, -- and /* */ comments)
# ---------------------------------------------------------------------------


def split_statements(sql: str) -> list[str]:
    out, buf, i, n = [], [], 0, len(sql)
    while i < n:
        c = sql[i]
        two = sql[i : i + 2]
        if two == "--":  # line comment
            j = sql.find("\n", i)
            j = n if j == -1 else j
            buf.append(sql[i:j])
            i = j
        elif two == "/*":  # block comment
            j = sql.find("*/", i + 2)
            j = n if j == -1 else j + 2
            buf.append(sql[i:j])
            i = j
        elif c == "'":  # single-quoted string
            j = i + 1
            while j < n:
                if sql[j] == "'" and sql[j : j + 2] != "''":
                    break
                j += 2 if sql[j : j + 2] == "''" else 1
            buf.append(sql[i : j + 1])
            i = j + 1
        elif c == "$":  # dollar-quoted string ($tag$...$tag$)
            m = re.match(r"\$[A-Za-z0-9_]*\$", sql[i:])
            if m:
                tag = m.group(0)
                j = sql.find(tag, i + len(tag))
                j = n if j == -1 else j + len(tag)
                buf.append(sql[i:j])
                i = j
            else:
                buf.append(c)
                i += 1
        elif c == ";":  # statement boundary
            stmt = "".join(buf).strip()
            if stmt:
                out.append(stmt)
            buf = []
            i += 1
        else:
            buf.append(c)
            i += 1
    tail = "".join(buf).strip()
    if tail:
        out.append(tail)
    return out


def _is_blank(sql: str) -> bool:
    """True if the file has no executable SQL (only comments / TODO placeholder)."""
    return not split_statements(sql)


# ---------------------------------------------------------------------------
# Execution + result normalization
# ---------------------------------------------------------------------------


def run_script(cur, sql: str):
    """Execute every statement; return rows of the last one that returns rows."""
    rows = None
    for stmt in split_statements(sql):
        cur.execute(stmt)
        if cur.description is not None:
            rows = cur.fetchall()
    return rows


def _norm(v):
    if v is None:
        return "\x00NULL"
    if isinstance(v, Decimal):
        return ("num", round(v, 6))
    if isinstance(v, float):
        return ("num", round(Decimal(repr(v)), 6))
    if isinstance(v, (dict, list)):
        # jsonb comes back as dict/list: canonicalize (sorted keys) into a hashable
        # string. Object key order is irrelevant; array element order is preserved.
        return ("json", json.dumps(v, sort_keys=True, default=str))
    if isinstance(v, (bytes, bytearray, memoryview)):
        return bytes(v)
    return v


def normalize(rows) -> list[tuple]:
    return [tuple(_norm(v) for v in row) for row in (rows or [])]


def compare(actual, expected, ordered: bool):
    """Return (ok, message). Unordered uses multiset semantics."""
    a, e = normalize(actual), normalize(expected)
    if ordered:
        if a == e:
            return True, ""
        return False, _diff_msg(a, e, "ordered list")
    from collections import Counter

    ca, ce = Counter(a), Counter(e)
    if ca == ce:
        return True, ""
    return False, _diff_msg(a, e, "row multiset", ca, ce)


def _diff_msg(a, e, kind, ca=None, ce=None):
    lines = [
        f"result {kind} differs:",
        f"  rows returned: {len(a)}   expected: {len(e)}",
    ]
    if ca is not None:
        missing = list((ce - ca).elements())[:5]
        extra = list((ca - ce).elements())[:5]
        if missing:
            lines.append(f"  missing (in expected, not yours): {missing}")
        if extra:
            lines.append(f"  unexpected (in yours, not expected): {extra}")
    else:
        for idx, (x, y) in enumerate(zip(a, e)):
            if x != y:
                lines.append(f"  first mismatch at row {idx}: got {x} expected {y}")
                break
        if not any("mismatch" in l for l in lines):
            lines.append(f"  yours[:3]={a[:3]}  expected[:3]={e[:3]}")
    return "\n".join(lines)


# ---------------------------------------------------------------------------
# EXPLAIN plan assertions (performance / index katas)
# ---------------------------------------------------------------------------


def _final_select(sql: str) -> str:
    stmts = split_statements(sql)
    for stmt in reversed(stmts):
        head = stmt.lstrip().lower()
        if head.startswith(("select", "with", "table")):
            return stmt
    return stmts[-1]


def _walk(node):
    yield node
    for child in node.get("Plans", []) or []:
        yield from _walk(child)


def check_asserts(cur, sql: str, asserts: list[str]):
    """Run EXPLAIN (FORMAT JSON) on the final SELECT; return (ok, message)."""
    cur.execute("EXPLAIN (FORMAT JSON) " + _final_select(sql))
    plan = cur.fetchone()[0][0]["Plan"]
    nodes = list(_walk(plan))
    node_types = {n.get("Node Type", "") for n in nodes}
    for a in asserts:
        if a == "no-seq-scan":
            if "Seq Scan" in node_types:
                return (
                    False,
                    f"expected no Seq Scan, but plan has one. Node types: {sorted(node_types)}",
                )
        elif a.startswith("index="):
            want = a.split("=", 1)[1]
            used = {n.get("Index Name") for n in nodes if "Index Name" in n}
            if want not in used:
                return (
                    False,
                    f"expected an index scan on '{want}', but plan used indexes {used or '{none}'}",
                )
    return True, ""
