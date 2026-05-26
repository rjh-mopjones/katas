-- grade: ordered
-- Per-day event stats for customer 1: daily count, running total, 7-day moving sum.
-- ROWS UNBOUNDED PRECEDING  -- cumulative from first row to current
-- ROWS BETWEEN 6 PRECEDING AND CURRENT ROW  -- last 7 rows (by position, not calendar)
-- Note: ROWS counts physical rows; RANGE counts rows with the same ORDER BY value;
-- GROUPS counts distinct peer groups. ROWS is the right frame here because
-- each day is one aggregated row, giving an exact 7-row window.
SELECT
    event_ts::date                             AS event_day,
    count(*)                                   AS daily_count,
    sum(count(*)) OVER (
        ORDER BY event_ts::date
        ROWS UNBOUNDED PRECEDING
    )                                          AS running_total,
    sum(count(*)) OVER (
        ORDER BY event_ts::date
        ROWS BETWEEN 6 PRECEDING AND CURRENT ROW
    )                                          AS moving_7day
FROM events
WHERE customer_id = 1
GROUP BY event_ts::date
ORDER BY event_day;
