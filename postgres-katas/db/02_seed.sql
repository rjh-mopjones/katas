-- ============================================================================
-- Deterministic seed. No random(): every value is a pure function of a
-- generate_series index, so results are byte-for-byte reproducible.
-- Anchor date: 2024-01-01 (orders span 2023-2024).
-- ============================================================================

-- Categories (8) --------------------------------------------------------------
INSERT INTO categories(id, name, description) VALUES
  (1,'Electronics','Gadgets and devices'),
  (2,'Books','Print and digital'),
  (3,'Clothing','Apparel and accessories'),
  (4,'Home','Household and kitchen'),
  (5,'Toys','Games and toys'),
  (6,'Sports','Outdoor and fitness'),
  (7,'Grocery','Food and drink'),
  (8,'Beauty','Cosmetics and care');

-- Products (2000) -------------------------------------------------------------
INSERT INTO products(id, category_id, sku, name, price, stock, metadata, created_at)
SELECT
  i,
  ((i - 1) % 8) + 1,
  'SKU-' || lpad(i::text, 5, '0'),
  'Product ' || i,
  (((i % 500) + 1) + 0.99)::numeric(10,2),
  (i % 300) + 1,
  jsonb_build_object(
    'color', (array['red','blue','green','black'])[((i - 1) % 4) + 1],
    'tags',  to_jsonb((array['new','sale','clearance','featured'])[1:((i % 4) + 1)]),
    'specs', jsonb_build_object(
                'weight',   (i % 50) + 1,
                'material', (array['plastic','metal','wood'])[((i - 1) % 3) + 1])
  ),
  date '2023-01-01' + ((i * 7) % 600)
FROM generate_series(1, 2000) AS g(i);

-- Customers (2000); ~1 in 17 has NULL country (NULL-handling katas) -----------
INSERT INTO customers(id, email, first_name, last_name, country, created_at)
SELECT
  i,
  'customer' || i || '@example.com',
  (array['Alice','Bob','Carol','David','Eve','Frank','Grace','Heidi','Ivan','Judy'])[((i - 1) % 10) + 1],
  (array['Smith','Jones','Brown','Taylor','Wilson','Davis','Evans','Walker','White','Hall',
         'Green','Clark','Young','King','Wright','Hill','Adams','Baker','Carter','Diaz'])[((i - 1) % 20) + 1],
  CASE WHEN i % 17 = 0 THEN NULL
       ELSE (array['US','UK','DE','FR','CA','AU','JP','BR'])[((i - 1) % 8) + 1] END,
  date '2022-06-01' + ((i * 3) % 700)
FROM generate_series(1, 2000) AS g(i);

-- Orders (10000) --------------------------------------------------------------
INSERT INTO orders(id, customer_id, order_date, status, total, external_ref)
SELECT
  i,
  ((i - 1) % 2000) + 1,
  date '2023-01-01' + ((i * 13) % 730),
  (array['pending','shipped','delivered','cancelled'])[(i % 4) + 1],
  (((i % 900) + 10) + 0.50)::numeric(12,2),
  'ORD-' || lpad(i::text, 6, '0')
FROM generate_series(1, 10000) AS g(i);

-- Order items (30000) ---------------------------------------------------------
INSERT INTO order_items(id, order_id, product_id, quantity, unit_price)
SELECT
  i,
  ((i - 1) % 10000) + 1,
  ((i * 13) % 1500) + 1,        -- only products 1..1500 are ever ordered; 1501..2000 are not (kata 02)
  (i % 5) + 1,
  (((i % 200) + 5) + 0.50)::numeric(10,2)
FROM generate_series(1, 30000) AS g(i);

-- Employees (120) — 4-level manager hierarchy --------------------------------
--   id 1            : CEO (manager NULL)
--   id 2..6         : report to 1
--   id 7..30        : report to 2..6
--   id 31..120      : report to 7..30
INSERT INTO employees(id, manager_id, name, department, salary, hire_date)
SELECT
  i,
  CASE
    WHEN i = 1  THEN NULL
    WHEN i <= 6 THEN 1
    WHEN i <= 30 THEN ((i - 7) % 5) + 2
    ELSE ((i - 31) % 24) + 7
  END,
  'Employee ' || i,
  (array['Engineering','Sales','Marketing','Finance','Support'])[((i - 1) % 5) + 1],
  (50000 + (i % 40) * 1500)::numeric(10,2),
  date '2018-01-01' + ((i * 11) % 2200)
FROM generate_series(1, 120) AS g(i);

-- Events (2000 customers x 25 days = 50000) -----------------------------------
-- Login days per customer form 5 islands of 5 consecutive days separated by
-- 2-day gaps: days {0-4, 7-11, 14-18, 21-25, 28-32}. Clean gaps-and-islands.
INSERT INTO events(id, customer_id, event_type, event_ts, metadata)
SELECT
  row_number() OVER (ORDER BY c.id, d) AS id,
  c.id,
  (array['view','add_to_cart','purchase','review','refund'])[((c.id + d) % 5) + 1],
  (date '2024-01-01' + (d + (d / 5) * 2))::timestamp
      + ((c.id % 24) * interval '1 hour')
      + (d * interval '7 minute'),
  jsonb_build_object(
    'device',  (array['mobile','desktop','tablet'])[((c.id + d) % 3) + 1],
    'amount',  CASE WHEN ((c.id + d) % 5) + 1 = 3 THEN ((c.id * d) % 500) + 1 END,
    'flagged', ((c.id * 25 + d) % 97 = 0),
    'tags',    to_jsonb((array['promo','organic','referral'])[1:((d % 3) + 1)])
  )
FROM customers c CROSS JOIN generate_series(0, 24) AS s(d);

-- Graph (directed, weighted; contains cycle B->C->D->B) ------------------------
INSERT INTO graph_edges(from_node, to_node, weight) VALUES
  ('A','B',4), ('A','C',10), ('A','E',7),
  ('B','C',3),
  ('C','D',2), ('C','F',8),
  ('D','B',1),                 -- back-edge: creates the B->C->D->B cycle
  ('E','F',5),
  ('F','D',2);

-- Articles (3000) — deterministic vocabulary so FTS matches are stable --------
-- 'database' present when i%3=0, 'optimization' when i%5=0; both when i%15=0.
INSERT INTO articles(id, title, content)
SELECT
  i,
  'Article ' || i,
  'This guide on '
    || CASE WHEN i % 3 = 0 THEN 'database '     ELSE 'network '   END
    || CASE WHEN i % 5 = 0 THEN 'optimization ' ELSE 'design '    END
    || 'covers '
    || (array['indexes','queries','scaling','caching','replication','partitioning'])[((i - 1) % 6) + 1]
    || ' in depth for engineers.'
FROM generate_series(1, 3000) AS g(i);

-- Inventory (first 1000 products) -------------------------------------------
INSERT INTO inventory(product_id, quantity, updated_at)
SELECT i, (i % 50) + 1, date '2024-01-01'
FROM generate_series(1, 1000) AS g(i);

-- Jobs queue (200 pending) ----------------------------------------------------
INSERT INTO jobs(id, status, payload, created_at)
SELECT i, 'pending', 'job-payload-' || i,
       timestamp '2024-01-01 00:00:00' + (i * interval '1 minute')
FROM generate_series(1, 200) AS g(i);

-- Stats for realistic query plans (index katas depend on this).
ANALYZE;
