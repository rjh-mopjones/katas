"""Grade each kata: run the learner's practice query and the reference solution
against the same data (in a rolled-back transaction) and compare result sets.

Blank practice files therefore fail — that is the intended RED starting state.
"""

from __future__ import annotations

import pytest

import grader
from conftest import discover_katas, read_sql

KATAS = discover_katas()


@pytest.mark.parametrize("kata", KATAS)
def test_kata(kata, cur):
    solution_sql = read_sql(kata, "solution")
    practice_sql = read_sql(kata, "practice")
    directives = grader.parse_directives(solution_sql)

    if directives.get("mode") == "concurrency":
        pytest.skip("graded by test_skip_locked_concurrency")

    if grader._is_blank(practice_sql):
        pytest.fail(f"{kata}: practice/query.sql is empty — write your query.")

    expected = grader.run_script(cur, solution_sql)
    cur.connection.rollback()  # discard any mutation from the solution script

    try:
        actual = grader.run_script(cur, practice_sql)
    except Exception as e:  # noqa: BLE001 - surface the SQL error to the learner
        cur.connection.rollback()
        pytest.fail(f"{kata}: your query raised an error:\n  {e}")

    ordered = directives.get("grade") == "ordered"
    ok, msg = grader.compare(actual, expected, ordered)
    assert ok, f"{kata}: {msg}"

    asserts = directives.get("asserts", [])
    if asserts:
        ok, msg = grader.check_asserts(cur, practice_sql, asserts)
        assert ok, f"{kata}: {msg}"


# --- Bespoke concurrency grader for the FOR UPDATE / SKIP LOCKED queue kata ---

CONCURRENCY_KATAS = [
    k
    for k in KATAS
    if grader.parse_directives(read_sql(k, "solution")).get("mode") == "concurrency"
]


@pytest.mark.parametrize("kata", CONCURRENCY_KATAS)
def test_skip_locked_concurrency(kata, conn):
    """Two workers run the claim query in overlapping transactions; SKIP LOCKED
    must hand each a disjoint set of rows (no job claimed twice)."""
    import psycopg
    from conftest import CONN_INFO

    practice_sql = read_sql(kata, "practice")
    if grader._is_blank(practice_sql):
        pytest.fail(f"{kata}: practice/query.sql is empty — write your query.")
    claim = grader.split_statements(practice_sql)[-1]

    w1 = psycopg.connect(**CONN_INFO)
    w2 = psycopg.connect(**CONN_INFO)
    try:
        w1.autocommit = False
        w2.autocommit = False
        c1, c2 = w1.cursor(), w2.cursor()
        c1.execute(claim)
        rows1 = c1.fetchall()
        c2.execute(claim)  # must not block: SKIP LOCKED steps over w1's locked rows
        rows2 = c2.fetchall()

        ids1 = {r[0] for r in rows1}
        ids2 = {r[0] for r in rows2}
        assert rows1, f"{kata}: worker 1 claimed no rows"
        assert rows2, f"{kata}: worker 2 claimed no rows"
        assert ids1.isdisjoint(ids2), (
            f"{kata}: workers claimed overlapping rows {ids1 & ids2} — "
            f"SKIP LOCKED not working"
        )
    finally:
        w1.rollback()
        w1.close()
        w2.rollback()
        w2.close()
