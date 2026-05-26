-- grade: ordered
-- Full-text search with ts_rank: find articles that mention both 'database'
-- and 'optimization' (after English stemming) and rank them by relevance.
-- to_tsvector parses and stems the document; to_tsquery parses the query;
-- @@ is the match operator; ts_rank scores the match.
SELECT
    id,
    title,
    ts_rank(
        to_tsvector('english', content),
        to_tsquery('english', 'database & optimization')
    ) AS rank
FROM articles
WHERE to_tsvector('english', content) @@ to_tsquery('english', 'database & optimization')
ORDER BY rank DESC, id;
