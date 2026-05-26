-- grade: set
-- For products with id <= 50, extract JSON fields from the metadata column.
-- -> navigates to a JSON sub-object/array; ->> returns the value as text.
-- Array elements are 0-indexed: ->>1 is the second tag, NULL when absent.
SELECT
    id,
    metadata->>'color'                       AS color,
    (metadata->'specs'->>'weight')::int      AS weight,
    metadata->'tags'->>1                     AS second_tag
FROM products
WHERE id <= 50;
