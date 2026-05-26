-- grade: ordered
-- Use pg_trgm's similarity() function to find customers whose last_name is
-- close to the misspelled value 'Wilsen'.  A similarity score of 1.0 means
-- identical; 0.0 means no trigrams in common.  Threshold >= 0.4 catches
-- 'Wilson' (score 0.4) while ignoring unrelated names.
SELECT
    id,
    last_name,
    similarity(last_name, 'Wilsen') AS sim
FROM customers
WHERE similarity(last_name, 'Wilsen') >= 0.4
ORDER BY sim DESC, id;
