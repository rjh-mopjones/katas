"""Pytest fixtures: a single DB connection, and a per-test transaction that is
always rolled back so katas (including mutation ones) never affect each other or
the seed data."""

from __future__ import annotations

import os
import pathlib

import psycopg
import pytest

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOLUTION_DIR = ROOT / "solution"
PRACTICE_DIR = ROOT / "practice"

CONN_INFO = dict(
    host=os.environ.get("KATAS_PGHOST", "localhost"),
    port=int(os.environ.get("KATAS_PGPORT", "5433")),
    user=os.environ.get("KATAS_PGUSER", "kata"),
    password=os.environ.get("KATAS_PGPASSWORD", "kata"),
    dbname=os.environ.get("KATAS_PGDATABASE", "katas"),
)


def discover_katas() -> list[str]:
    """Kata directory names like '04_ranking', sorted."""
    if not SOLUTION_DIR.exists():
        return []
    return sorted(
        d.name
        for d in SOLUTION_DIR.iterdir()
        if d.is_dir() and d.name[:2].isdigit() and (d / "query.sql").exists()
    )


@pytest.fixture(scope="session")
def conn():
    c = psycopg.connect(**CONN_INFO)
    yield c
    c.close()


@pytest.fixture
def cur(conn):
    """A cursor inside a transaction that is rolled back after the test."""
    conn.rollback()  # ensure a clean starting point
    with conn.cursor() as c:
        yield c
    conn.rollback()


def read_sql(kata: str, side: str) -> str:
    base = SOLUTION_DIR if side == "solution" else PRACTICE_DIR
    return (base / kata / "query.sql").read_text()
