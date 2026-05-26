# Anti-Join with NOT EXISTS

> Find products that have never been sold -- the safest way, even when NULLs lurk.

## The problem
You need to identify every product that has never appeared in an `order_items` row. Three approaches exist: `NOT EXISTS` (subquery), `NOT IN` (subquery), and `LEFT JOIN ... WHERE key IS NULL`. They look equivalent but behave very differently when NULLs are present.

## Requirements
- Output columns in order: `id`, `sku`
- Every product with no matching row in `order_items`
- No required ordering (grade: set)

## What you write
Your query in `practice/02_anti_join/query.sql`.

## The real challenge
- Use `NOT EXISTS`. The classic trap: `NOT IN (SELECT product_id FROM order_items)` silently returns **zero rows** the moment any `product_id` in `order_items` is NULL, because `x <> NULL` evaluates to UNKNOWN, making the whole `NOT IN` condition UNKNOWN for every row. `NOT EXISTS` is immune -- it checks row existence, not value equality.

## Run
```
make check-kata KATA=02_anti_join
```

## Reference
- Worked solution: `solution/02_anti_join/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/functions-subquery.html#FUNCTIONS-SUBQUERY-NOTIN
