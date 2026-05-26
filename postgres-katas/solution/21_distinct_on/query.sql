-- grade: ordered
-- Return the single most-recent event per customer using DISTINCT ON.
-- DISTINCT ON (customer_id) keeps the first row in each customer group
-- after the ORDER BY is applied; ORDER BY must start with the DISTINCT ON key.
SELECT DISTINCT ON (customer_id)
    customer_id,
    event_type,
    event_ts
FROM events
ORDER BY customer_id, event_ts DESC, id DESC;
