-- grade: set
-- Sales totals using ROLLUP(year, status) so we get detail rows, per-year
-- subtotals, and the grand total in one pass.
-- GROUPING() returns 1 when the column was rolled up (i.e. is a subtotal/total).
SELECT
    EXTRACT(YEAR FROM o.order_date)::int         AS yr,
    o.status,
    SUM(o.total)                                 AS total_sales,
    GREATEST(GROUPING(EXTRACT(YEAR FROM o.order_date)),
             GROUPING(o.status))                 AS is_subtotal
FROM orders o
GROUP BY ROLLUP(EXTRACT(YEAR FROM o.order_date), o.status);
