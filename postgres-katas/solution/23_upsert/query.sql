-- grade: ordered
-- mode: mutation
-- Apply a +10 stock delta to inventory for product_id 995..1005 using
-- INSERT ... ON CONFLICT.  Existing rows get quantity += 10; new rows
-- (1001..1005) are inserted with quantity=10 and updated_at='2024-02-01'.
INSERT INTO inventory (product_id, quantity, updated_at)
SELECT s.product_id, 10, DATE '2024-02-01'
FROM generate_series(995, 1005) AS s(product_id)
ON CONFLICT (product_id) DO UPDATE
    SET quantity   = inventory.quantity + 10,
        updated_at = DATE '2024-02-01';

SELECT product_id, quantity
FROM inventory
WHERE product_id BETWEEN 995 AND 1005
ORDER BY product_id;
