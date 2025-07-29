ALTER TABLE t_inventory ADD COLUMN product_id VARCHAR(255);

UPDATE t_inventory SET t_inventory.product_id = t_inventory.sku_code;

ALTER TABLE t_inventory DROP COLUMN sku_code;

ALTER TABLE t_inventory MODIFY product_id VARCHAR(255) NOT NULL;

CREATE UNIQUE INDEX idx_inventory_product_id ON t_inventory(product_id);