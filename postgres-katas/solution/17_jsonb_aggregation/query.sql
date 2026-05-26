-- grade: set
-- Per customer, build a JSON object that maps each device type to the number
-- of events from that device.  jsonb_object_agg(key, value) is the right tool
-- when you want to turn grouped rows into a JSON object rather than an array.
WITH counts AS (
    SELECT
        customer_id,
        metadata->>'device'  AS device,
        COUNT(*)             AS cnt
    FROM events
    GROUP BY customer_id, metadata->>'device'
)
SELECT
    customer_id,
    jsonb_object_agg(device, cnt) AS device_counts
FROM counts
GROUP BY customer_id;
