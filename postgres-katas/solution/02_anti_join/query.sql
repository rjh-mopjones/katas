-- grade: set
-- Products that have never appeared in any order.
-- NOT EXISTS is the safest anti-join: it handles NULLs correctly,
-- unlike NOT IN which silently returns zero rows when any subquery
-- value is NULL (because NULL <> x is UNKNOWN, not TRUE).
SELECT p.id, p.sku
FROM products p
WHERE NOT EXISTS (
    SELECT 1
    FROM order_items oi
    WHERE oi.product_id = p.id
);
