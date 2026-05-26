# MERGE — SQL:2003 Upsert

> Achieve the same stock delta as kata 23 using standard SQL MERGE (PG15+).

## The problem
Repeat the exact same +10 stock upsert from kata 23 — products 995..1005,
existing rows updated, new rows inserted — but this time use a `MERGE`
statement instead of `INSERT ... ON CONFLICT`. `MERGE` is the ISO SQL standard
for conditional insert/update/delete and was added in PostgreSQL 15.
End with the same `SELECT` so the grader confirms identical output.

## Requirements
- MERGE source: `generate_series(995, 1005)` with delta=10
- `WHEN MATCHED`: `UPDATE SET quantity = inventory.quantity + 10`, `updated_at = date '2024-02-01'`
- `WHEN NOT MATCHED`: `INSERT (product_id, quantity, updated_at) VALUES (..., delta, date '2024-02-01')`
- Final SELECT columns in order: `product_id`, `quantity`
- Ordered by `product_id ASC`

## What you write
Your query in `practice/24_merge/query.sql`.

## The real challenge
- In `MERGE`, the target table alias and the source alias must be different;
  inside `WHEN MATCHED UPDATE` you reference the target by its alias, not by
  `EXCLUDED` (that pseudo-table only exists in `ON CONFLICT`).
- `MERGE` is more powerful than `ON CONFLICT` (supports `WHEN NOT MATCHED BY SOURCE`
  for deletes in PG17), but `ON CONFLICT` is simpler for plain upserts. Know
  when to reach for each.

## Run
```
make check-kata KATA=24_merge
```

## Reference
- Worked solution: `solution/24_merge/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/sql-merge.html
