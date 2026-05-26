-- grade: ordered
-- Top 3 salary ranks per department using DENSE_RANK().
-- Window functions cannot appear in WHERE, so the rank filter
-- must live in a subquery or CTE.
-- DENSE_RANK vs RANK vs ROW_NUMBER:
--   ROW_NUMBER  -- always unique; 1,2,3,4 even for ties
--   RANK        -- gaps after ties; 1,1,3,4
--   DENSE_RANK  -- no gaps; 1,1,2,3  <-- used here so rank 3 is never skipped
SELECT department, name, salary, rnk
FROM (
    SELECT
        id,
        department,
        name,
        salary,
        DENSE_RANK() OVER (
            PARTITION BY department
            ORDER BY salary DESC
        ) AS rnk
    FROM employees
) ranked
WHERE rnk <= 3
ORDER BY department, rnk, id;
