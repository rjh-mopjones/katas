# Gaps and Islands — Login Streaks

> Collapse consecutive dates into contiguous activity islands.

## The problem
`customer_id = 1` has login activity on 25 distinct dates spread across five
consecutive-day streaks separated by 2-day gaps. Using only the `events` table,
identify each streak ("island"), report its start date, end date, and length.
There should be exactly 5 islands, each 5 days long.

## Requirements
- Output columns in order: `island_start` (date), `island_end` (date), `days` (int)
- One row per consecutive-day island
- Ordered by `island_start ASC`

## What you write
Your query in `practice/22_gaps_and_islands/query.sql`.

## The real challenge
- The classic trick: `login_date - ROW_NUMBER() OVER (ORDER BY login_date) * INTERVAL '1 day'`
  produces the same anchor date for every date inside a consecutive run, and a
  different anchor whenever there is a gap. `GROUP BY` that anchor to collapse
  each island.
- You must `SELECT DISTINCT DATE(event_ts)` first — multiple events on the same
  day must count as one login day, not many.

## Run
```
make check-kata KATA=22_gaps_and_islands
```

## Reference
- Worked solution: `solution/22_gaps_and_islands/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/functions-window.html
