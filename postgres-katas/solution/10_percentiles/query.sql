-- grade: set
-- Per category: median price, 90th-percentile price, and the most frequent
-- material value from the nested JSON spec.
-- percentile_cont interpolates; mode() picks the most common discrete value.
SELECT
    p.category_id,
    percentile_cont(0.5) WITHIN GROUP (ORDER BY p.price)                           AS median_price,
    percentile_cont(0.9) WITHIN GROUP (ORDER BY p.price)                           AS p90_price,
    mode()               WITHIN GROUP (ORDER BY p.metadata -> 'specs' ->> 'material') AS top_material
FROM products p
GROUP BY p.category_id;
