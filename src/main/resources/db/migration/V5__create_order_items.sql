CREATE TABLE order_items (
                             id         BIGSERIAL PRIMARY KEY,
                             order_id   BIGINT         NOT NULL REFERENCES purchase_orders (id) ON DELETE CASCADE,
                             item_id    BIGINT         NOT NULL,
                             item_name  VARCHAR(255)   NOT NULL,
                             quantity   INT            NOT NULL CHECK (quantity > 0),
                             unit_price NUMERIC(12,2)  NOT NULL CHECK (unit_price >= 0),
                             line_total NUMERIC(12,2)  NOT NULL CHECK (line_total >= 0)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
CREATE INDEX idx_order_items_item  ON order_items (item_id);

-- Every existing order was single-line; carry it over as one row before the
-- columns it lived in disappear.
INSERT INTO order_items (order_id, item_id, item_name, quantity, unit_price, line_total)
SELECT o.id,
       o.item_id,
       COALESCE(i.name, 'Item ' || o.item_id),
       o.quantity,
       ROUND(o.amount / o.quantity, 2),
       o.amount
FROM purchase_orders o
         LEFT JOIN items i ON i.id = o.item_id;

-- idx_orders_item goes with the column.
ALTER TABLE purchase_orders DROP COLUMN item_id;
ALTER TABLE purchase_orders DROP COLUMN quantity;
