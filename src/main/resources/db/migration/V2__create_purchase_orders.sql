CREATE TABLE purchase_orders (
                                 id             BIGSERIAL PRIMARY KEY,
                                 item_id        BIGINT         NOT NULL,
                                 buyer_email    VARCHAR(255)   NOT NULL,
                                 quantity       INT            NOT NULL CHECK (quantity > 0),
                                 amount         NUMERIC(12,2)  NOT NULL CHECK (amount >= 0),
                                 currency       VARCHAR(3)        NOT NULL,
                                 status         VARCHAR(20)    NOT NULL,
                                 checkout_token VARCHAR(100),
                                 created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_status ON purchase_orders (status);
CREATE INDEX idx_orders_email  ON purchase_orders (buyer_email);
CREATE INDEX idx_orders_item   ON purchase_orders (item_id);