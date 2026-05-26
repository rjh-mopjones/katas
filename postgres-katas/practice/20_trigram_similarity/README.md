# Trigram Similarity Search

> Use pg_trgm to find customers whose last name is similar to a misspelled input.

## The problem

A user types `'Wilsen'` when searching for a customer — a typo for `'Wilson'`. Using the `pg_trgm` extension's `similarity()` function, find all customers whose `last_name` has a trigram similarity score of at least 0.4 compared to `'Wilsen'`. Return the matching customers along with their similarity score, ordered most-similar first.

## Requirements

- Output columns in order: `id`, `last_name`, `sim`
- `sim` is `similarity(last_name, 'Wilsen')` (float4)
- Filter: `similarity(last_name, 'Wilsen') >= 0.4`
- Order: `sim DESC, id ASC` (grade: ordered)

## What you write

Your query in `practice/20_trigram_similarity/query.sql`.

## The real challenge

- `similarity(a, b)` returns a float between 0 and 1 based on shared trigrams (3-character substrings). The `%` operator is shorthand for `similarity(...) > pg_trgm.similarity_threshold` (default 0.3) — but the threshold is a GUC, not a literal, so prefer the explicit function call for portable code.
- In production, create a `GIN` index with the `gin_trgm_ops` operator class (`CREATE INDEX ... USING gin (last_name gin_trgm_ops)`) to turn this into an index scan instead of a sequential scan. Without it, every row must be evaluated.
- Trigram similarity is case-sensitive by default; use `lower()` on both sides for case-insensitive fuzzy matching.

## Run

```
make check-kata KATA=20_trigram_similarity
```

## Reference

- Worked solution: `solution/20_trigram_similarity/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/pgtrgm.html
