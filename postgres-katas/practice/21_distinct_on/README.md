# DISTINCT ON — Most-Recent Event Per Customer

> Pick exactly one row per group without a subquery or window function.

## The problem
The `events` table records many events per customer. You need to return the
single most-recent event for every customer. PostgreSQL's `DISTINCT ON` clause
does this in one pass, but its interaction with `ORDER BY` is subtle and trips
up even experienced developers.

## Requirements
- Output columns in order: `customer_id`, `event_type`, `event_ts`
- One row per customer (the latest by `event_ts`; break ties with `id DESC`)
- Ordered by `customer_id ASC`

## What you write
Your query in `practice/21_distinct_on/query.sql`.

## The real challenge
- `DISTINCT ON (customer_id)` keeps the *first* row PostgreSQL sees for each
  customer after sorting — so `ORDER BY` must begin with `customer_id`, then
  the tiebreaker columns that determine "most recent". Swapping or omitting any
  part of the `ORDER BY` silently returns the wrong row.
- Compare against `ROW_NUMBER() OVER (PARTITION BY customer_id ORDER BY event_ts DESC)` —
  `DISTINCT ON` is often faster but is PostgreSQL-specific.

## Run
```
make check-kata KATA=21_distinct_on
```

## Reference
- Worked solution: `solution/21_distinct_on/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/sql-select.html#SQL-DISTINCT
