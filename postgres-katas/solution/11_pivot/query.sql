-- grade: set
-- Pivot event counts per customer into columns: views, purchases, refunds.
-- Conditional aggregation with FILTER avoids a full crosstab() extension.
SELECT
    e.customer_id,
    COUNT(*) FILTER (WHERE e.event_type = 'view')     AS views,
    COUNT(*) FILTER (WHERE e.event_type = 'purchase') AS purchases,
    COUNT(*) FILTER (WHERE e.event_type = 'refund')   AS refunds
FROM events e
GROUP BY e.customer_id;
