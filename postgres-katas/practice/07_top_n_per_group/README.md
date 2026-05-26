# Top-N Per Group with ROW_NUMBER

> Fetch each customer's two highest-value orders -- exactly two rows per customer, ties broken deterministically.

## The problem
For every customer, find the two orders with the highest `total`. When two orders share the same total, break the tie by `order_id ASC` so results are deterministic. Return exactly two rows per customer -- no more, no less.

## Requirements
- Output columns in order: `customer_id`, `order_id`, `total`, `rn`
- `rn = 1` is the highest-total order, `rn = 2` the second-highest
- Ordered by `customer_id ASC`, `rn ASC` (grade: ordered)

## What you write
Your query in `practice/07_top_n_per_group/query.sql`.

## The real challenge
- Three approaches exist: (1) `ROW_NUMBER()` in a subquery -- used here, guarantees exactly N rows per group; (2) `LATERAL` subquery -- readable but can be slower without good indexes; (3) `DISTINCT ON (customer_id)` -- PostgreSQL-specific, elegant for top-1 but awkward for top-N. The window approach is cleanest for top-N. Key detail: use `ROW_NUMBER`, not `RANK` or `DENSE_RANK`, when you need a hard row limit rather than a rank threshold -- ties with `RANK` could yield more than N rows.

## Run
```
make check-kata KATA=07_top_n_per_group
```

## Reference
- Worked solution: `solution/07_top_n_per_group/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/functions-window.html
