# Calendar Gap Fill

> Generate a complete date spine and LEFT JOIN event counts to fill gaps with zero.

## The problem

For `customer_id = 1`, the `events` table has rows only on days when events
occurred.  Produce one output row for every calendar day from the customer's
first event date to their last, including days with no events (those days
should show `event_count = 0`).

## Requirements

- Output columns in order: `d` (date), `event_count` (bigint/int)
- One row per calendar day from the customer's first to last event date (inclusive)
- `event_count` is the number of events on that day, or `0` if none
- Ordered by `d` ascending (grade: ordered)

## What you write

Your query in `practice/14_calendar_gap_fill/query.sql`.

## The real challenge

- `generate_series(start, end, '1 day')` produces the complete date spine; without it you have no rows to LEFT JOIN against for gap days.
- Cast `generate_series` result to `date` (it returns `timestamp` when the step is an interval).
- `COALESCE(count, 0)` turns the NULL from the LEFT JOIN into a zero -- forgetting COALESCE is the most common mistake.
- Do NOT use `CURRENT_DATE` or `now()` -- derive the range from the data itself with `MIN`/`MAX` on `event_ts`.

## Run

```
make check-kata KATA=14_calendar_gap_fill
```

## Reference

- Worked solution: `solution/14_calendar_gap_fill/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/functions-srf.html
