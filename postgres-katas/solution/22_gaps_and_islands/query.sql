-- grade: ordered
-- For customer_id=1, collapse distinct login dates into consecutive-day islands.
-- Subtracting the row_number (as an interval) from each date produces the same
-- "anchor" date for every date within a consecutive run; GROUP BY that anchor.
WITH login_dates AS (
    SELECT DISTINCT DATE(event_ts) AS login_date
    FROM events
    WHERE customer_id = 1
),
grouped AS (
    SELECT
        login_date,
        login_date - (ROW_NUMBER() OVER (ORDER BY login_date) * INTERVAL '1 day')::interval AS grp
    FROM login_dates
)
SELECT
    MIN(login_date)  AS island_start,
    MAX(login_date)  AS island_end,
    COUNT(*)::int    AS days
FROM grouped
GROUP BY grp
ORDER BY island_start;
