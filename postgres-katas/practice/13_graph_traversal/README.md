# Graph Traversal

> Find all acyclic paths from A to D in a weighted directed graph using a recursive CTE.

## The problem

The `graph_edges` table defines a small directed weighted graph with edges
including a cycle (D->B->C->D).  Find every acyclic path from node `'A'` to
node `'D'` using at most 5 hops (edges).  Return each path as a human-readable
string (e.g. `'A->C->D'`), its hop count, and the sum of edge weights along
the path.

## Requirements

- Output columns in order: `path` (text, e.g. `'A->C->D'`), `hops` (int), `total_weight` (int)
- Only paths that end at node `'D'`
- At most 5 hops (edges) per path
- No repeated nodes in a single path (acyclic)
- Ordered by `total_weight` ascending, then `path` ascending (grade: ordered)

## What you write

Your query in `practice/13_graph_traversal/query.sql`.

## The real challenge

- Cycle detection: maintain a `visited` array of node names and check `NOT (to_node = ANY(visited))` before extending the path.
- PostgreSQL 14+ offers `SEARCH` and `CYCLE` clauses for `WITH RECURSIVE` as syntactic sugar for exactly this pattern -- but the manual array approach works on all versions and is worth understanding.
- The path string is built with string concatenation (`||`); keep the accumulator in the recursive term, not a final aggregate.

## Run

```
make check-kata KATA=13_graph_traversal
```

## Reference

- Worked solution: `solution/13_graph_traversal/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/queries-with.html#QUERIES-WITH-CYCLE
