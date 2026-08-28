ALTER TABLE purchase_orders ADD COLUMN conversation_id   VARCHAR(100);
ALTER TABLE purchase_orders ADD COLUMN payment_token     VARCHAR(255);
ALTER TABLE purchase_orders ADD COLUMN payment_id        VARCHAR(100);
ALTER TABLE purchase_orders ADD COLUMN buyer_name        VARCHAR(100);
ALTER TABLE purchase_orders ADD COLUMN buyer_surname     VARCHAR(100);
ALTER TABLE purchase_orders ADD COLUMN buyer_phone       VARCHAR(30);
ALTER TABLE purchase_orders ADD COLUMN buyer_identity_no VARCHAR(20);
ALTER TABLE purchase_orders ADD COLUMN address           TEXT;
ALTER TABLE purchase_orders ADD COLUMN city              VARCHAR(100);
ALTER TABLE purchase_orders ADD COLUMN country           VARCHAR(100);
ALTER TABLE purchase_orders ADD COLUMN zip_code          VARCHAR(20);
ALTER TABLE purchase_orders ADD COLUMN error_message     TEXT;

CREATE UNIQUE INDEX idx_orders_conversation ON purchase_orders (conversation_id);
CREATE INDEX idx_orders_token ON purchase_orders (payment_token);