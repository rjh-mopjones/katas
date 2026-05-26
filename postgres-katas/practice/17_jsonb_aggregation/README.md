# JSONB Aggregation

> Build a per-customer JSON object that maps device types to event counts using jsonb_object_agg.

## The problem

The `events` table records each event's device type inside `metadata->>'device'` (values: `mobile`, `desktop`, `tablet`). For every customer, produce a single jsonb value like `{"mobile": 12, "desktop": 8, "tablet": 10}` that summarises how many events came from each device. Use a CTE to pre-aggregate the (customer, device) counts, then collapse into a JSON object per customer.

## Requirements

- Output columns in order: `customer_id`, `device_counts`
- `device_counts` is type `jsonb`, produced by `jsonb_object_agg(device, cnt)`
- One row per customer
- No required ordering (grade: set)

## What you write

Your query in `practice/17_jsonb_aggregation/query.sql`.

## The real challenge

- `jsonb_object_agg(key, value)` expects exactly one row per key within each group; duplicate keys cause the last value to win (undefined order). Pre-aggregating in a CTE guarantees uniqueness.
- The sibling function `jsonb_agg(value)` builds a JSON *array* instead of an object — use `jsonb_object_agg` when you want key-value structure.
- Casting the count to a numeric type is optional; jsonb stores integers natively as JSON numbers.

## Run

```
make check-kata KATA=17_jsonb_aggregation
```

## Reference

- Worked solution: `solution/17_jsonb_aggregation/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/functions-aggregate.html
