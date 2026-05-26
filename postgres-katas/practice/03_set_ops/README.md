# Set Operations with EXCEPT

> Identify customers who are stuck in pending -- they ordered but never received a delivery.

## The problem
You need to find customers who have at least one `pending` order but have never had a `delivered` order. Rather than using `NOT EXISTS` or `NOT IN`, express this as a set subtraction: the set of customers with a pending order, minus the set with a delivered order.

## Requirements
- Output column: `customer_id`
- Customers who appear in orders with `status = 'pending'` but NOT in orders with `status = 'delivered'`
- No required ordering (grade: set)

## What you write
Your query in `practice/03_set_ops/query.sql`.

## The real challenge
- `EXCEPT` automatically deduplicates both sides -- you do not need `DISTINCT`. The subtle gotcha: column count and types must match exactly across the two `SELECT` lists. Also, `EXCEPT ALL` exists and keeps duplicates if you need multiset subtraction -- plain `EXCEPT` is almost always what you want.

## Run
```
make check-kata KATA=03_set_ops
```

## Reference
- Worked solution: `solution/03_set_ops/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/queries-union.html
