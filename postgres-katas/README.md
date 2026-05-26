# PostgreSQL Katas

Senior-level PostgreSQL **query** katas. Each kata gives you a problem against a shared, realistic
dataset; you write a single query (or short script) and an auto-grader checks your result against
the reference answer.

Same two-sided model as the rest of the repo — `solution/` is the answer key, `practice/` is where
you work — adapted for SQL: the grader (`checker/`) plays the role the reference tests play in the
code modules.

```
postgres-katas/
├── docker-compose.yml      # postgres:17 on localhost:5433 (never touches a local 5432)
├── db/01_schema.sql        # the shared schema (+ indexes, extensions)
├── db/02_seed.sql          # deterministic seed (fixed dates, generate_series, NUMERIC)
├── solution/<NN_name>/query.sql   # reference query (the answer key)
├── practice/<NN_name>/query.sql   # you write here  (+ README.md = the problem)
└── checker/                # pytest + psycopg grader
```

## Setup

Needs Docker and Python 3. From this directory:

```bash
make up        # start postgres:17 and load the schema + seed (first run only)
make venv      # create the grader virtualenv (checker/.venv) and install deps
```

## Workflow

```bash
cat practice/04_ranking/README.md          # read the problem
$EDITOR practice/04_ranking/query.sql       # write your query
make check-kata KATA=04_ranking             # grade just this one
make check                                  # grade everything (blank = all RED)
make shell                                  # open psql to explore the data
```

The grader runs your query and the reference query against the same data and compares the result
sets (row order ignored unless the kata says otherwise; NULL/numeric/JSON normalized). Stuck? The
worked answer is the twin at `solution/<NN_name>/query.sql`.

## The dataset

One e-commerce + activity schema: `categories, products (jsonb metadata), customers, orders,
order_items, employees (manager hierarchy), events (50k, time-series + jsonb), graph_edges (with a
cycle), articles (full text), inventory, jobs (a work queue)`. Fully deterministic — fixed dates
and `generate_series`, no `random()` — so every kata has one stable correct answer.

## Katas

| # | Kata | Technique |
|---|------|-----------|
| 01 | semi_join | `EXISTS` semi-join |
| 02 | anti_join | `NOT EXISTS` (and the `NOT IN` NULL trap) |
| 03 | set_ops | `EXCEPT` / `UNION` / `INTERSECT` |
| 04 | ranking | `DENSE_RANK` vs `RANK` vs `ROW_NUMBER` |
| 05 | running_and_moving | window frames: `ROWS` vs `RANGE` vs `GROUPS` |
| 06 | lag_lead | `LAG`/`LEAD` period-over-period deltas |
| 07 | top_n_per_group | top-N per partition (window vs LATERAL vs DISTINCT ON) |
| 08 | filter_aggregates | aggregate `FILTER (WHERE …)` |
| 09 | grouping_sets | `ROLLUP`/`CUBE` + `GROUPING()` |
| 10 | percentiles | `percentile_cont` / `mode() WITHIN GROUP` |
| 11 | pivot | conditional-aggregation pivot |
| 12 | hierarchy | recursive CTE up a manager tree |
| 13 | graph_traversal | recursive CTE + cycle detection |
| 14 | calendar_gap_fill | `generate_series` date spine + `LEFT JOIN` |
| 15 | jsonb_navigation | `->`, `->>`, casts, array indexing |
| 16 | jsonb_containment | `@>` + GIN index (EXPLAIN-asserted) |
| 17 | jsonb_aggregation | `jsonb_object_agg` |
| 18 | json_table | `JSON_TABLE` shredding (PG17) |
| 19 | full_text_search | `tsvector`/`tsquery`/`ts_rank` |
| 20 | trigram_similarity | `pg_trgm` `similarity()` |
| 21 | distinct_on | `DISTINCT ON` latest-per-group |
| 22 | gaps_and_islands | consecutive-run grouping |
| 23 | upsert | `INSERT … ON CONFLICT DO UPDATE` |
| 24 | merge | `MERGE` (PG15+) |
| 25 | queue_skip_locked | `FOR UPDATE SKIP LOCKED` work queue |
| 26 | explain_and_index | read `EXPLAIN`, add an index, kill the Seq Scan |

Suggested order is just 01 → 26 (easy → hard).
