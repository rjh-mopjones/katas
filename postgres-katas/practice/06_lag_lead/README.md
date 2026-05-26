# Month-over-Month Delta with LAG

> Calculate monthly revenue and the change from the previous month -- in a single pass.

## The problem
Aggregate all orders by calendar month and, for each month, show the total revenue, the previous month's total, and the difference between them. The first month has no predecessor, so `prev_total` and `delta` should be NULL there.

## Requirements
- Output columns in order: `month` (date, first day of month), `monthly_total`, `prev_total`, `delta`
- `prev_total` is NULL for the earliest month
- Ordered by `month ASC` (grade: ordered)

## What you write
Your query in `practice/06_lag_lead/query.sql`.

## The real challenge
- `LAG` and `LEAD` operate over the result of `GROUP BY` -- not the raw table rows -- so you apply the window function on top of the aggregate. The gotcha: you cannot reference the alias `monthly_total` inside the same `SELECT` list, so you must repeat `sum(total)` (or use a CTE/subquery). Also note that `date_trunc('month', ...)` returns a `timestamp`; casting to `::date` keeps output clean.

## Run
```
make check-kata KATA=06_lag_lead
```

## Reference
- Worked solution: `solution/06_lag_lead/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/functions-window.html
