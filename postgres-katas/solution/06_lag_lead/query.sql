-- grade: ordered
-- Monthly sales with month-over-month delta using LAG().
-- LAG(expr, 1) looks back one partition row; for the first month
-- it returns NULL, so prev_total and delta are both NULL.
SELECT
    date_trunc('month', order_date)::date                             AS month,
    sum(total)                                                        AS monthly_total,
    LAG(sum(total)) OVER (ORDER BY date_trunc('month', order_date))   AS prev_total,
    sum(total)
        - LAG(sum(total)) OVER (ORDER BY date_trunc('month', order_date)) AS delta
FROM orders
GROUP BY date_trunc('month', order_date)
ORDER BY month;
