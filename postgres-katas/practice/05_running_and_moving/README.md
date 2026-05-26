# Running Total and Moving Average

> Compute a cumulative event count and a 7-day rolling sum for a single customer's activity timeline.

## The problem
For `customer_id = 1`, aggregate events by calendar day and compute three metrics alongside the daily count: a running total of all events up to that day, and a 7-day moving sum covering the current day and the six preceding days. The events table has exactly 25 distinct activity days for this customer.

## Requirements
- Output columns in order: `event_day` (date), `daily_count`, `running_total`, `moving_7day`
- Filter to `customer_id = 1` only
- Ordered by `event_day ASC` (grade: ordered)

## What you write
Your query in `practice/05_running_and_moving/query.sql`.

## The real challenge
- Window frames: `ROWS UNBOUNDED PRECEDING` counts physical rows, `RANGE UNBOUNDED PRECEDING` groups rows with equal ORDER BY values. Since each day collapses to one row after `GROUP BY`, both give the same running total here -- but for raw event rows they diverge. The 7-day moving window uses `ROWS BETWEEN 6 PRECEDING AND CURRENT ROW` (7 rows by position), not a calendar interval; gaps in the date sequence shrink the effective window. `GROUPS` frames (PostgreSQL 11+) count distinct peer groups instead of rows.

## Run
```
make check-kata KATA=05_running_and_moving
```

## Reference
- Worked solution: `solution/05_running_and_moving/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/sql-expressions.html#SYNTAX-WINDOW-FUNCTIONS
