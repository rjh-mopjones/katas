-- mode: concurrency
-- Atomically claim up to 5 pending jobs for a worker using FOR UPDATE SKIP LOCKED.
-- Two concurrent workers will each claim disjoint sets because SKIP LOCKED causes
-- rows already locked by another transaction to be skipped rather than waited on.
UPDATE jobs
SET status = 'processing'
WHERE id IN (
    SELECT id
    FROM jobs
    WHERE status = 'pending'
    ORDER BY id
    LIMIT 5
    FOR UPDATE SKIP LOCKED
)
RETURNING id;
