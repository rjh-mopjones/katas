# Filter Aggregates

> Count orders by status using aggregate FILTER clauses, including the NULL-country group.

## The problem

Join `orders` to `customers` and group by `country`.  You need three counts per
country group: total orders, orders that were delivered, and orders that were
cancelled.  The dataset includes customers whose `country` is NULL (every
customer whose `id % 17 = 0`), so that NULL group must appear in the output too.

## Requirements

- Output columns in order: `country`, `total_orders`, `delivered`, `cancelled`
- One row per distinct country value, including NULL
- No ordering required (grade: set)

## What you write

Your query in `practice/08_filter_aggregates/query.sql`.

## The real challenge

- `FILTER (WHERE ...)` vs `SUM(CASE WHEN ... THEN 1 ELSE 0 END)`: both work, but FILTER is cleaner and slightly faster because the planner can skip the evaluation entirely for non-matching rows.
- NULL grouping gotcha: `GROUP BY country` naturally produces a NULL bucket, so no special handling is needed -- but `WHERE country IS NOT NULL` would silently drop it.

## Run

```
make check-kata KATA=08_filter_aggregates
```

## Reference

- Worked solution: `solution/08_filter_aggregates/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/sql-expressions.html#SYNTAX-AGGREGATES
