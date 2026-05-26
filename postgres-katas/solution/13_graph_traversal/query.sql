-- grade: ordered
-- Find all acyclic paths from node 'A' to node 'D' with at most 5 hops.
-- A visited-node array prevents revisiting nodes (cycle detection).
-- The path is built as a text string; hops and total_weight are accumulated.
WITH RECURSIVE paths AS (
    -- anchor: start at node A
    SELECT
        'A'::text          AS path,
        'A'::text          AS current_node,
        0                  AS hops,
        0                  AS total_weight,
        ARRAY['A'::text]   AS visited

    UNION ALL

    -- recursive member: extend path by one edge
    SELECT
        p.path || '->' || g.to_node,
        g.to_node,
        p.hops + 1,
        p.total_weight + g.weight,
        p.visited || g.to_node
    FROM paths p
    JOIN graph_edges g ON g.from_node = p.current_node
    WHERE p.hops < 5
      AND NOT (g.to_node = ANY(p.visited))
)
SELECT path, hops, total_weight
FROM paths
WHERE current_node = 'D'
ORDER BY total_weight, path;
