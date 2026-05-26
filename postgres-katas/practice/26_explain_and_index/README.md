# EXPLAIN and Index Creation — Seq Scan to Index Scan

> Turn a full-table scan into a fast B-tree point lookup by adding an index.

## The problem
The query `SELECT id, customer_id, total FROM orders WHERE external_ref = 'ORD-005000'`
performs a sequential scan because `external_ref` has no index. With 10,000 rows
that is acceptable today, but it will not scale. Your task: create a B-tree index
on `orders(external_ref)` and then run the same SELECT so the grader can verify
both that the index exists and that the plan no longer uses a Seq Scan.

## Requirements
- `CREATE INDEX idx_orders_external_ref ON orders(external_ref)`
- Final SELECT columns in order: `id`, `customer_id`, `total`
- The grader asserts: index `idx_orders_external_ref` exists, and no Seq Scan
  is used on the `orders` table for the final SELECT

## What you write
Your query (script) in `practice/26_explain_and_index/query.sql`.

## The real challenge
- After `CREATE INDEX` the planner must choose an Index Scan (or Bitmap Index
  Scan). For a single-row point lookup on a high-cardinality column this is
  guaranteed — but only after the index exists in the same transaction/session.
- Run `EXPLAIN` before and after to see the plan change from `Seq Scan` to
  `Index Scan using idx_orders_external_ref`. Understanding `EXPLAIN` output
  (cost, rows, width) is an essential senior-engineer skill.

## Run
```
make check-kata KATA=26_explain_and_index
```

## Reference
- Worked solution: `solution/26_explain_and_index/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/sql-createindex.html
