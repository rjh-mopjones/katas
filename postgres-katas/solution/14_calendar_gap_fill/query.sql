-- grade: ordered
-- For customer_id=1, produce one row per calendar day from first to last
-- event date.  Days with no events get event_count=0 (gap fill via LEFT JOIN).
-- Use fixed date literals derived from the data; do NOT use CURRENT_DATE.
WITH date_range AS (
    SELECT
        MIN(event_ts::date) AS first_day,
        MAX(event_ts::date) AS last_day
    FROM events
    WHERE customer_id = 1
),
calendar AS (
    SELECT generate_series(first_day, last_day, '1 day'::interval)::date AS d
    FROM date_range
),
daily_counts AS (
    SELECT event_ts::date AS d, COUNT(*) AS cnt
    FROM events
    WHERE customer_id = 1
    GROUP BY event_ts::date
)
SELECT
    cal.d,
    COALESCE(dc.cnt, 0) AS event_count
FROM calendar cal
LEFT JOIN daily_counts dc ON dc.d = cal.d
ORDER BY cal.d;
