-- grade: ordered
-- Top 2 orders by total per customer, tiebroken by order id ASC.
-- ROW_NUMBER() assigns a unique rank even for ties (tiebreak by id ASC),
-- ensuring exactly 2 rows per customer.
-- The rank filter must be in the outer WHERE because window functions
-- are not allowed in WHERE of the same query level.
SELECT customer_id, order_id, total, rn
FROM (
    SELECT
        customer_id,
        id   AS order_id,
        total,
        ROW_NUMBER() OVER (
            PARTITION BY customer_id
            ORDER BY total DESC, id ASC
        ) AS rn
    FROM orders
) ranked
WHERE rn <= 2
ORDER BY customer_id, rn;
