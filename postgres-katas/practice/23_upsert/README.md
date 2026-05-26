# Upsert — ON CONFLICT Stock Delta

> Insert new stock rows or increment existing ones in a single statement.

## The problem
You need to apply a +10 quantity delta to `inventory` for `product_id` 995..1005.
Products 995..1000 already exist; products 1001..1005 do not. Use
`INSERT ... ON CONFLICT (product_id) DO UPDATE` so that existing rows are
updated and missing rows are created — all in one atomic statement.
End with a `SELECT` of the resulting rows so the grader can compare output.

## Requirements
- Upsert all 11 product_ids (995 through 1005 inclusive) with delta +10
- Existing rows: `quantity = inventory.quantity + 10`
- New rows: `quantity = 10`, `updated_at = date '2024-02-01'`
- Final SELECT columns in order: `product_id`, `quantity`
- Ordered by `product_id ASC`

## What you write
Your query in `practice/23_upsert/query.sql`.

## The real challenge
- In the `DO UPDATE SET` clause, `inventory.quantity` refers to the existing
  row and `EXCLUDED.quantity` refers to the value you tried to insert. Using
  the wrong one silently sets quantity to 10 for all rows instead of adding.
- Use `generate_series(995, 1005)` as the source to avoid a long VALUES list.

## Run
```
make check-kata KATA=23_upsert
```

## Reference
- Worked solution: `solution/23_upsert/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT
