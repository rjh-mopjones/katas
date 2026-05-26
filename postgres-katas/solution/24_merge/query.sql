-- grade: ordered
-- mode: mutation
-- Achieve the same +10 stock delta as kata 23 but using SQL:2003 MERGE (PG15+).
-- WHEN MATCHED updates existing rows; WHEN NOT MATCHED inserts new rows.
MERGE INTO inventory AS tgt
USING (
    SELECT s.product_id, 10 AS delta
    FROM generate_series(995, 1005) AS s(product_id)
) AS src
ON tgt.product_id = src.product_id
WHEN MATCHED THEN
    UPDATE SET quantity   = tgt.quantity + src.delta,
               updated_at = DATE '2024-02-01'
WHEN NOT MATCHED THEN
    INSERT (product_id, quantity, updated_at)
    VALUES (src.product_id, src.delta, DATE '2024-02-01');

SELECT product_id, quantity
FROM inventory
WHERE product_id BETWEEN 995 AND 1005
ORDER BY product_id;
