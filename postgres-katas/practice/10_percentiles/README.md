# Percentiles

> Compute median, 90th-percentile, and modal material per product category using ordered-set aggregates.

## The problem

For each `category_id` in `products`, calculate three statistics about price
and material: the median price, the 90th-percentile price, and the most
frequently occurring material string extracted from the nested JSONB field
`metadata->'specs'->>'material'`.

## Requirements

- Output columns in order: `category_id`, `median_price`, `p90_price`, `top_material`
- `median_price` uses `percentile_cont(0.5)`
- `p90_price` uses `percentile_cont(0.9)`
- `top_material` uses `mode()` on the text value of `metadata->'specs'->>'material'`
- No ordering required (grade: set)

## What you write

Your query in `practice/10_percentiles/query.sql`.

## The real challenge

- `percentile_cont` interpolates between adjacent values (returns a numeric); `percentile_disc` returns an actual value from the dataset.  For an even number of rows they differ.
- `mode()` is also an ordered-set aggregate -- it requires `WITHIN GROUP (ORDER BY ...)` syntax even though "order" feels counterintuitive for a frequency measure.
- The JSONB path `metadata -> 'specs' ->> 'material'` extracts a text value; using `->` (not `->>`) returns JSONB and breaks the text ordering inside `mode()`.

## Run

```
make check-kata KATA=10_percentiles
```

## Reference

- Worked solution: `solution/10_percentiles/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/functions-aggregate.html#FUNCTIONS-ORDEREDSET-TABLE
