# JSON_TABLE — Shredding a JSON Array

> Use PostgreSQL 17's JSON_TABLE to expand a jsonb array into relational rows with ordinality.

## The problem

Each product row has a `metadata->'tags'` array containing 1-4 tag strings such as `new`, `sale`, `clearance`, and `featured`. For products with `id <= 20`, unnest the tags array so that each tag becomes its own output row. Include the product's color and weight on every row, and number the tags 1, 2, 3, … within each product using `FOR ORDINALITY`.

## Requirements

- Output columns in order: `product_id`, `color`, `weight`, `tag`, `tag_ord`
- `product_id` — `products.id`
- `color` — `metadata->>'color'` (text)
- `weight` — `(metadata->'specs'->>'weight')::int`
- `tag` — the tag string
- `tag_ord` — 1-based position of the tag in the array (`FOR ORDINALITY`)
- Filter: `id <= 20`
- Order: `product_id ASC, tag_ord ASC` (grade: ordered)

## What you write

Your query in `practice/18_json_table/query.sql`.

## The real challenge

- `JSON_TABLE` is a PostgreSQL 17 feature (SQL/JSON standard). It acts as an implicit `LATERAL` table function; place it after a comma in the `FROM` clause.
- The `'$[*]'` path iterates every element of the top-level array.
- `FOR ORDINALITY` adds a 1-based integer counter column — similar to `WITH ORDINALITY` on `jsonb_array_elements`, but expressed inside the `COLUMNS` clause.
- The `PATH '$'` for the tag column means "the element itself" (a scalar string).

## Run

```
make check-kata KATA=18_json_table
```

## Reference

- Worked solution: `solution/18_json_table/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/17/functions-json.html#FUNCTIONS-SQLJSON-TABLE
