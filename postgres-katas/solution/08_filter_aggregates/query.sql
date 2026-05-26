-- grade: set
-- Per customer country (including the NULL-country group), count total orders,
-- orders with status='delivered', and orders with status='cancelled'.
-- FILTER(...) keeps the aggregation concise and avoids CASE/WHEN sprawl.
SELECT
    c.country,
    COUNT(*)                                      AS total_orders,
    COUNT(*) FILTER (WHERE o.status = 'delivered') AS delivered,
    COUNT(*) FILTER (WHERE o.status = 'cancelled') AS cancelled
FROM orders o
JOIN customers c ON c.id = o.customer_id
GROUP BY c.country;
