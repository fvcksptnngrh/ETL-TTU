-- Drop old tables that will be replaced
DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS customers;
DROP TABLE IF EXISTS products;

-- Create normalized transactions table (1 row per receipt)
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_no VARCHAR(100) NOT NULL UNIQUE,
    trx_date DATE,
    department VARCHAR(100),
    customer_code VARCHAR(100),
    customer_name VARCHAR(200),
    customer_address VARCHAR(500),
    subtotal NUMERIC(19, 2),
    discount_total NUMERIC(19, 2),
    tax_total NUMERIC(19, 2),
    fee_total NUMERIC(19, 2),
    grand_total NUMERIC(19, 2),
    is_valid BOOLEAN NOT NULL DEFAULT TRUE,
    etl_job_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_etl_job FOREIGN KEY (etl_job_id) REFERENCES etl_jobs(id)
);

CREATE INDEX idx_transaction_no ON transactions(transaction_no);
CREATE INDEX idx_transaction_date ON transactions(trx_date);
CREATE INDEX idx_transaction_etl_job ON transactions(etl_job_id);
CREATE INDEX idx_transaction_valid ON transactions(is_valid);

-- Create transaction_items table
CREATE TABLE transaction_items (
    id BIGSERIAL PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    line_no VARCHAR(20),
    item_code VARCHAR(100),
    item_name VARCHAR(300),
    qty NUMERIC(19, 4),
    unit VARCHAR(50),
    unit_price NUMERIC(19, 2),
    discount_pct NUMERIC(19, 2),
    line_total NUMERIC(19, 2),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_item_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id)
);

CREATE INDEX idx_item_transaction ON transaction_items(transaction_id);

-- Create products table (unique by item_code)
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    item_code VARCHAR(100) NOT NULL UNIQUE,
    item_name VARCHAR(300),
    default_unit VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_product_item_code ON products(item_code);
