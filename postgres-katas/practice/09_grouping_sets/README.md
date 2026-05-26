# Grouping Sets

> Compute sales subtotals and a grand total in one query with ROLLUP and GROUPING().

## The problem

Use `ROLLUP(year, status)` over the `orders` table to produce detail rows (one
per year+status combination), per-year subtotals (status rolled up), and a
grand total (both rolled up).  Add an `is_subtotal` flag that is `1` whenever
either dimension was rolled up, and `0` for detail rows.

## Requirements

- Output columns in order: `yr` (integer or numeric), `status`, `total_sales`, `is_subtotal`
- `yr` is the four-digit year extracted from `order_date`
- `total_sales` is the SUM of `total` for the group
- `is_subtotal` is `1` for subtotal/grand-total rows, `0` for detail rows
- No ordering required (grade: set)

## What you write

Your query in `practice/09_grouping_sets/query.sql`.

## The real challenge

- `GROUPING(col)` returns `1` when `col` was aggregated away by ROLLUP/CUBE -- it does NOT return `1` just because the value happens to be NULL in the data.
- `ROLLUP(a, b)` produces grouping sets `(a,b)`, `(a)`, and `()` -- that is N+1 sets for N columns.  `CUBE` would add `(b)` too; plain `GROUPING SETS` gives full manual control.
- Casting `EXTRACT(...)` to `int` avoids a `double precision` column that surprises type-strict graders.

## Run

```
make check-kata KATA=09_grouping_sets
```

## Reference

- Worked solution: `solution/09_grouping_sets/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/queries-table-expressions.html#QUERIES-GROUPING-SETS
