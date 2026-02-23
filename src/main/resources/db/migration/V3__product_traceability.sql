-- V3: Product traceability & product aliases

-- Add etl_job_id FK to products table
ALTER TABLE products ADD COLUMN etl_job_id BIGINT;
ALTER TABLE products ADD CONSTRAINT fk_product_etl_job FOREIGN KEY (etl_job_id) REFERENCES etl_jobs(id);
CREATE INDEX idx_product_etl_job ON products(etl_job_id);

-- Create product_aliases table
CREATE TABLE product_aliases (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    etl_job_id BIGINT NOT NULL,
    item_code VARCHAR(100) NOT NULL,
    item_name VARCHAR(300),
    unit VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_alias_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_alias_etl_job FOREIGN KEY (etl_job_id) REFERENCES etl_jobs(id)
);

CREATE INDEX idx_alias_product ON product_aliases(product_id);
CREATE INDEX idx_alias_etl_job ON product_aliases(etl_job_id);
CREATE INDEX idx_alias_item_code ON product_aliases(item_code);
