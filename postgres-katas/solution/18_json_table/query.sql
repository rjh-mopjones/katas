-- grade: ordered
-- Use JSON_TABLE (PostgreSQL 17) to shred the tags array inside products.metadata.
-- FOR ORDINALITY produces a 1-based row counter so the caller knows tag position.
-- The implicit LATERAL join means one output row per array element per product.
SELECT
    p.id                                     AS product_id,
    p.metadata->>'color'                     AS color,
    (p.metadata->'specs'->>'weight')::int    AS weight,
    t.tag,
    t.tag_ord
FROM products p,
     JSON_TABLE(
         p.metadata->'tags',
         '$[*]'
         COLUMNS (
             tag_ord  FOR ORDINALITY,
             tag      text  PATH '$'
         )
     ) AS t
WHERE p.id <= 20
ORDER BY p.id, t.tag_ord;
