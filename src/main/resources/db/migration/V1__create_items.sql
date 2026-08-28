CREATE TABLE items (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    price       NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    currency    VARCHAR(3) NOT NULL DEFAULT 'TRY',
    stock       INT NOT NULL DEFAULT 0 CHECK (stock >= 0),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    version     BIGINT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_items_active ON items (active) WHERE active = true;