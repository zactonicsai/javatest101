-- ===========================================================================
-- V1 — initial schema
-- ===========================================================================

CREATE TABLE users (
    id              UUID PRIMARY KEY,
    external_id     VARCHAR(255) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    display_name    VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    user_id         VARCHAR(255) NOT NULL,
    status          VARCHAR(32)  NOT NULL,
    contact_email   VARCHAR(255),
    order_date      DATE,
    idempotency_key VARCHAR(255) UNIQUE,
    correlation_id  VARCHAR(255) UNIQUE,
    metadata        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status  ON orders(status);
CREATE INDEX idx_orders_created ON orders(created_at);

CREATE TABLE order_lines (
    id              UUID PRIMARY KEY,
    order_id        UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    sku             VARCHAR(64)  NOT NULL,
    quantity        INTEGER      NOT NULL CHECK (quantity > 0),
    unit_price      NUMERIC(19,2) NOT NULL CHECK (unit_price >= 0),
    line_no         INTEGER      NOT NULL
);

CREATE INDEX idx_order_lines_order_id ON order_lines(order_id);
CREATE INDEX idx_order_lines_sku      ON order_lines(sku);
