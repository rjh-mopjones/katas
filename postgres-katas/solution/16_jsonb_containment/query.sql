-- grade: set
-- assert: index=idx_events_metadata_gin
-- Find every event whose metadata contains {"flagged": true}.
-- Step 1: create a GIN index so the @> containment operator can use an index scan.
-- Step 2: run the containment query -- the planner will choose a Bitmap Index Scan.
CREATE INDEX IF NOT EXISTS idx_events_metadata_gin ON events USING gin (metadata);

SELECT id, customer_id
FROM events
WHERE metadata @> '{"flagged": true}';
