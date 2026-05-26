# Window Ranking with DENSE_RANK

> Rank employees by salary within each department and keep only the top three ranks.

## The problem
For every department, find the employees at salary ranks 1, 2, and 3. When multiple employees share a salary they share a rank. Use `DENSE_RANK()` so that a tie at rank 1 does not cause rank 3 to disappear.

## Requirements
- Output columns in order: `department`, `name`, `salary`, `rnk`
- Only rows where `rnk <= 3`
- Ordered by `department ASC`, `rnk ASC`, `id ASC` (grade: ordered)

## What you write
Your query in `practice/04_ranking/query.sql`.

## The real challenge
- Window functions are forbidden in `WHERE` -- you must wrap the ranked query in a subquery or CTE and filter in the outer query. Choosing the right function matters: `ROW_NUMBER` always assigns unique ranks (ties are broken arbitrarily, not stably); `RANK` leaves gaps after ties (1,1,3); `DENSE_RANK` never skips a rank (1,1,2,3) -- essential when you want "top 3 salary levels" not "top 3 rows".

## Run
```
make check-kata KATA=04_ranking
```

## Reference
- Worked solution: `solution/04_ranking/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/functions-window.html
