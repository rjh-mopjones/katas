# Hierarchy

> Walk an employee reporting chain upward to the CEO with a recursive CTE.

## The problem

The `employees` table is a self-referential tree: every employee has a
`manager_id` pointing to their direct manager, except the CEO (id=1) whose
`manager_id` is NULL.  Starting from employee id=120, walk the chain upward
level by level until you reach the CEO.

## Requirements

- Output columns in order: `depth`, `id`, `name`, `manager_id`
- `depth` starts at `1` for employee 120 and increases by 1 for each step toward the CEO
- Ordered by `depth` ascending (grade: ordered, depth is the unique tiebreaker since each row is a unique step)

## What you write

Your query in `practice/12_hierarchy/query.sql`.

## The real challenge

- The anchor selects the starting employee; the recursive member joins `employees` on `e.id = c.manager_id` to step upward -- not downward.
- Recursion terminates naturally when `c.manager_id IS NULL` (the CEO has no manager to join to).
- `WITH RECURSIVE` is required even though the self-reference in this case could theoretically terminate without it -- PostgreSQL requires the keyword for any CTE that references itself.

## Run

```
make check-kata KATA=12_hierarchy
```

## Reference

- Worked solution: `solution/12_hierarchy/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/queries-with.html#QUERIES-WITH-RECURSIVE
