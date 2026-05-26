-- grade: set
-- assert: index=idx_orders_external_ref
-- assert: no-seq-scan
-- external_ref is unindexed; a Seq Scan is used until we add a B-tree index.
-- After CREATE INDEX the planner switches to an Index Scan for the point lookup.
CREATE INDEX idx_orders_external_ref ON orders(external_ref);

SELECT id, customer_id, total
FROM orders
WHERE external_ref = 'ORD-005000';
