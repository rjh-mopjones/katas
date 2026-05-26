# Pivot

> Turn event-type rows into columns with conditional aggregation.

## The problem

The `events` table stores one row per event with an `event_type` column.  Your
task is to produce one row per `customer_id` with separate count columns for
`view`, `purchase`, and `refund` events -- a classic pivot operation using only
standard SQL.

## Requirements

- Output columns in order: `customer_id`, `views`, `purchases`, `refunds`
- `views` counts rows where `event_type = 'view'`
- `purchases` counts rows where `event_type = 'purchase'`
- `refunds` counts rows where `event_type = 'refund'`
- No ordering required (grade: set)

## What you write

Your query in `practice/11_pivot/query.sql`.

## The real challenge

- The standard approach is `COUNT(*) FILTER (WHERE event_type = 'view')` or equivalently `SUM(CASE WHEN event_type = 'view' THEN 1 ELSE 0 END)`.  FILTER is preferred: it is SQL-standard (SQL:2003) and the planner can skip the CASE evaluation.
- The `crosstab()` function from `tablefunc` extension can do this too, but requires the extension to be installed and the syntax is more complex -- conditional aggregation is almost always the right default.
- A customer with zero events of a given type gets `0`, not NULL, because `COUNT(*)` never returns NULL.

## Run

```
make check-kata KATA=11_pivot
```

## Reference

- Worked solution: `solution/11_pivot/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/sql-expressions.html#SYNTAX-AGGREGATES
