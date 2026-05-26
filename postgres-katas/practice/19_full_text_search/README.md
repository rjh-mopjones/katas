# Full-Text Search with ts_rank

> Find articles that mention both 'database' and 'optimization' and rank them by relevance.

## The problem

The `articles` table contains 3,000 rows. Approximately 200 of them (those where `id % 15 = 0`) contain both the words `database` and `optimization` in their `content` column. Use PostgreSQL's built-in full-text search to find these articles, apply English stemming so that plurals and verb forms also match, and order the results by relevance score.

## Requirements

- Output columns in order: `id`, `title`, `rank`
- `rank` is the `ts_rank` score (float4) from matching against the English tsvector
- Filter: articles whose tsvector matches `to_tsquery('english', 'database & optimization')`
- Order: `rank DESC, id ASC` (grade: ordered)

## What you write

Your query in `practice/19_full_text_search/query.sql`.

## The real challenge

- `to_tsvector('english', content)` tokenises and stems the text; `to_tsquery('english', 'database & optimization')` parses the boolean query with stemming. Both must use the same language configuration for stemming to align.
- The `@@` operator returns true when a tsvector matches a tsquery.
- `ts_rank` scores based on term frequency; `ts_rank_cd` scores by cover density (proximity). Neither requires an index to compute correctly.
- In production you would create a GIN index on `to_tsvector('english', content)` (or a generated tsvector column) to avoid a full table scan. This kata grades correctness only, not the query plan.

## Run

```
make check-kata KATA=19_full_text_search
```

## Reference

- Worked solution: `solution/19_full_text_search/query.sql`
- PostgreSQL docs: https://www.postgresql.org/docs/current/textsearch.html
