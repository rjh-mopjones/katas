-- grade: set
-- Customers who have placed at least one high-value order (total >= 900.00).
-- A semi-join (EXISTS) is the right tool: we only care whether a matching order
-- exists, not how many, so no JOIN + DISTINCT and no NOT IN NULL trap.
SELECT c.id, c.email
FROM customers c
WHERE EXISTS (
    SELECT 1
    FROM orders o
    WHERE o.customer_id = c.id
      AND o.total >= 900.00
);
