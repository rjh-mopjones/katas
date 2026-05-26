# Semi-Join with EXISTS

> Find customers who have placed at least one high-value order -- without duplicates and without NULL traps.

## The problem
You need to identify customers who have placed at least one order with a total of 900.00 or more. A naive `JOIN` would return one row per qualifying order, forcing you to add `DISTINCT`. A semi-join with `EXISTS` stops at the first match per customer and never inflates the result set.

## Requirements
- Output columns in order: `id`, `email`
- One row per customer (no duplicates)
- Only customers where at least one order has `total >= 900.00`
- No required ordering (grade: set)

## What you write
Your query in `practice/01_semi_join/query.sql`.

## The real challenge
- The senior-level insight: `EXISTS` short-circuits -- it returns `true` as soon as the subquery finds one matching row, so no aggregation or `DISTINCT` is needed. Compare with `JOIN ... DISTINCT` (scans all matching rows) and `IN (subquery)` (materialises the full subquery and breaks on NULLs in the list).

## Run
```
make check-kata KATA=01_semi_join
```

## Reference
- Worked solution: `solution/01_semi_join/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/functions-subquery.html#FUNCTIONS-SUBQUERY-EXISTS
