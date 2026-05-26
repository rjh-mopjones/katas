# JSONB Navigation

> Extract nested JSON fields from a jsonb column using -> and ->>.

## The problem

The `products` table stores rich metadata as a `jsonb` column. Each row has a `color` string at the top level, a `weight` integer nested inside a `specs` object, and a `tags` array. For products with `id <= 50`, pull out these three pieces of information alongside the product id. Absent array elements should come back as NULL, not as an error.

## Requirements

- Output columns in order: `id`, `color`, `weight`, `second_tag`
- `color` — text value of `metadata->>'color'`
- `weight` — integer value of `(metadata->'specs'->>'weight')::int`
- `second_tag` — text value of the second element in the tags array (`metadata->'tags'->>1`); NULL when the array has fewer than 2 elements
- Filter: `id <= 50`
- No required ordering (grade: set)

## What you write

Your query in `practice/15_jsonb_navigation/query.sql`.

## The real challenge

- Knowing when to use `->` (returns jsonb) vs. `->>` (returns text): you must use `->>` at the final step to get a plain text or castable value.
- Chaining operators: `metadata->'specs'->>'weight'` — the first step stays as jsonb so the second step can navigate further.
- Array indexing is 0-based: index `1` is the *second* element; accessing a missing index returns NULL rather than raising an error.

## Run

```
make check-kata KATA=15_jsonb_navigation
```

## Reference

- Worked solution: `solution/15_jsonb_navigation/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/functions-json.html
