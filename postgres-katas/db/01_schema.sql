-- ============================================================================
-- postgres-katas schema
-- One realistic e-commerce + activity dataset that serves every kata.
-- Determinism: NUMERIC for money, fixed anchor dates, UTC, C collation.
-- ============================================================================

-- Session/DB settings for stable, reproducible results.
ALTER DATABASE katas SET timezone TO 'UTC';
ALTER DATABASE katas SET extra_float_digits TO 0;

CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- trigram similarity (kata 20)

-- ----------------------------------------------------------------------------
-- Reference / dimension tables
-- ----------------------------------------------------------------------------
CREATE TABLE categories (
    id          int PRIMARY KEY,
    name        text NOT NULL UNIQUE,
    description text
);

CREATE TABLE products (
    id          int PRIMARY KEY,
    category_id int  NOT NULL REFERENCES categories(id),
    sku         text NOT NULL UNIQUE,
    name        text NOT NULL,
    price       numeric(10,2) NOT NULL,
    stock       int  NOT NULL,
    metadata    jsonb,                    -- {"color": "...", "tags": [...], "specs": {...}}
    created_at  date NOT NULL
);

CREATE TABLE customers (
    id          int PRIMARY KEY,
    email       text NOT NULL UNIQUE,
    first_name  text NOT NULL,
    last_name   text NOT NULL,
    country     text,                     -- nullable on purpose (NULL-handling katas)
    created_at  date NOT NULL
);

-- ----------------------------------------------------------------------------
-- Fact tables
-- ----------------------------------------------------------------------------
CREATE TABLE orders (
    id           int PRIMARY KEY,
    customer_id  int  NOT NULL REFERENCES customers(id),
    order_date   date NOT NULL,
    status       text NOT NULL,           -- pending | shipped | delivered | cancelled
    total        numeric(12,2) NOT NULL,
    external_ref text NOT NULL            -- high-cardinality; intentionally UNindexed (kata 26)
);

CREATE TABLE order_items (
    id         int PRIMARY KEY,
    order_id   int NOT NULL REFERENCES orders(id),
    product_id int NOT NULL REFERENCES products(id),
    quantity   int NOT NULL,
    unit_price numeric(10,2) NOT NULL
);

-- Self-referential manager hierarchy (recursive CTE kata 12).
CREATE TABLE employees (
    id         int PRIMARY KEY,
    manager_id int REFERENCES employees(id),   -- NULL for the CEO
    name       text NOT NULL,
    department text NOT NULL,
    salary     numeric(10,2) NOT NULL,
    hire_date  date NOT NULL
);

-- Time-series activity (windows, gaps-and-islands, JSON, calendar gap-fill).
CREATE TABLE events (
    id          bigint PRIMARY KEY,
    customer_id int  NOT NULL REFERENCES customers(id),
    event_type  text NOT NULL,            -- view | add_to_cart | purchase | review | refund
    event_ts    timestamp NOT NULL,       -- no tz: synthetic, UTC by construction
    metadata    jsonb                     -- {"device": "...", "amount": n, "tags": [...]}
);

-- Directed weighted graph with at least one cycle (recursive traversal kata 13).
CREATE TABLE graph_edges (
    from_node text NOT NULL,
    to_node   text NOT NULL,
    weight    int  NOT NULL,
    PRIMARY KEY (from_node, to_node)
);

-- Free text for full-text search (kata 19).
CREATE TABLE articles (
    id      int PRIMARY KEY,
    title   text NOT NULL,
    content text NOT NULL
);

-- Mutable target for upsert / MERGE katas (23, 24).
CREATE TABLE inventory (
    product_id int PRIMARY KEY REFERENCES products(id),
    quantity   int  NOT NULL,
    updated_at date NOT NULL
);

-- Work queue for the FOR UPDATE / SKIP LOCKED kata (25).
CREATE TABLE jobs (
    id         int  PRIMARY KEY,
    status     text NOT NULL DEFAULT 'pending',   -- pending | processing | done
    payload    text NOT NULL,
    created_at timestamp NOT NULL
);

-- ----------------------------------------------------------------------------
-- "Normal" indexes a real schema would have. Deliberately NOT indexed:
--   * orders.external_ref      -> kata 26 (learner adds the index)
--   * products.metadata (GIN)  -> kata 16 (learner adds the GIN index)
--   * articles tsvector (GIN)  -> kata 19
--   * trigram GIN              -> kata 20
-- ----------------------------------------------------------------------------
CREATE INDEX idx_orders_customer   ON orders(customer_id);
CREATE INDEX idx_orders_date       ON orders(order_date);
CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_prod  ON order_items(product_id);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_events_customer   ON events(customer_id);
CREATE INDEX idx_events_ts         ON events(event_ts);
CREATE INDEX idx_employees_manager ON employees(manager_id);
