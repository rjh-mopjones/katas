-- grade: ordered
-- Walk the employee reporting chain from id=120 up to the CEO (id=1).
-- The anchor is employee 120 (depth=1); each recursive step follows manager_id
-- upward, incrementing depth.  ORDER BY depth shows the chain bottom-to-top.
WITH RECURSIVE chain AS (
    -- anchor: start at the target employee
    SELECT
        1          AS depth,
        e.id,
        e.name,
        e.manager_id
    FROM employees e
    WHERE e.id = 120

    UNION ALL

    -- recursive member: step one level up toward the CEO
    SELECT
        c.depth + 1,
        e.id,
        e.name,
        e.manager_id
    FROM chain c
    JOIN employees e ON e.id = c.manager_id
)
SELECT depth, id, name, manager_id
FROM chain
ORDER BY depth;
