# JSONB Containment with GIN Index

> Accelerate a jsonb containment query with a GIN index and verify the plan with EXPLAIN.

## The problem

The `events` table has 50,000 rows. Roughly 515 of them have `metadata->>'flagged' = 'true'`. A sequential scan works but is slow. Create a GIN index on the `metadata` column, then use the `@>` containment operator to find all flagged events. The grader checks that the final SELECT uses the index.

## Requirements

- Your script must first `CREATE INDEX IF NOT EXISTS idx_events_metadata_gin ON events USING gin (metadata);`
- Then run: `SELECT id, customer_id FROM events WHERE metadata @> '{"flagged": true}';`
- Output columns in order: `id`, `customer_id`
- No required ordering (grade: set)
- The grader asserts that an index scan on `idx_events_metadata_gin` appears in the query plan

## What you write

Your query in `practice/16_jsonb_containment/query.sql`.

## The real challenge

- The `@>` operator checks whether the left jsonb value *contains* the right jsonb value — a full structural subset check, not just key existence.
- A plain `btree` index cannot accelerate `@>`. You need a `GIN` index; for jsonb the default operator class (`jsonb_ops`) supports `@>`, `?`, `?|`, and `?&`. The alternative `jsonb_path_ops` is smaller and faster for `@>` only.
- Always run `EXPLAIN` after creating the index to confirm the planner chose a Bitmap Index Scan; with only ~1% selectivity it should always do so.

## Run

```
make check-kata KATA=16_jsonb_containment
```

## Reference

- Worked solution: `solution/16_jsonb_containment/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/datatype-json.html#JSON-INDEXING
