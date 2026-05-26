# Queue Processing — FOR UPDATE SKIP LOCKED

> Claim jobs atomically so two workers never process the same row.

## The problem
The `jobs` table holds 200 pending jobs. Multiple workers run concurrently;
each should claim up to 5 jobs and mark them `processing` without ever
overlapping with another worker. A naive `SELECT ... LIMIT 5` followed by an
`UPDATE` has a race condition. `FOR UPDATE SKIP LOCKED` solves it atomically:
rows already locked by another transaction are skipped rather than waited on.

## Requirements
- Claim up to 5 jobs per worker call
- Set `status = 'processing'` on the claimed rows
- Return the claimed `id` values (as the first and only column)
- Two concurrent workers must claim completely disjoint sets of ids

## What you write
Your query in `practice/25_queue_skip_locked/query.sql`.

## The real challenge
- The subquery `SELECT id FROM jobs WHERE status='pending' ORDER BY id LIMIT 5 FOR UPDATE SKIP LOCKED`
  must appear *inside* the `WHERE id IN (...)` of the outer `UPDATE`. If you
  do two separate statements — a SELECT then an UPDATE — you lose atomicity and
  introduce a race window.
- `SKIP LOCKED` means a worker never blocks; it just gets fewer than 5 rows if
  the queue is nearly empty. `NOWAIT` would raise an error instead of skipping.

## Run
```
make check-kata KATA=25_queue_skip_locked
```

## Reference
- Worked solution: `solution/25_queue_skip_locked/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/sql-select.html#SQL-FOR-UPDATE-SHARE
