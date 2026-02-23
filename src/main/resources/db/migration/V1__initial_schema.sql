-- Create products table
CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    description VARCHAR(500),
    price NUMERIC(19, 2),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create index on product name
CREATE INDEX idx_product_name ON products(name);

-- Create customers table
CREATE TABLE customers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    email VARCHAR(200),
    phone VARCHAR(20),
    address VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on customer
CREATE INDEX idx_customer_name ON customers(name);
CREATE INDEX idx_customer_email ON customers(email);

-- Create etl_jobs table
CREATE TABLE etl_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(100) NOT NULL UNIQUE,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    total_records INTEGER NOT NULL DEFAULT 0,
    extracted_records INTEGER NOT NULL DEFAULT 0,
    valid_records INTEGER NOT NULL DEFAULT 0,
    failed_records INTEGER NOT NULL DEFAULT 0,
    duplicate_records INTEGER NOT NULL DEFAULT 0,
    loaded_records INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration_seconds BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes on etl_jobs
CREATE INDEX idx_etl_job_status ON etl_jobs(status);
CREATE INDEX idx_etl_job_created ON etl_jobs(created_at);

-- Create transactions table
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_date DATE NOT NULL,
    customer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity NUMERIC(19, 2) NOT NULL,
    price NUMERIC(19, 2) NOT NULL,
    total_amount NUMERIC(19, 2) NOT NULL,
    etl_job_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transaction_customer FOREIGN KEY (customer_id) REFERENCES customers(id),
    CONSTRAINT fk_transaction_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_transaction_etl_job FOREIGN KEY (etl_job_id) REFERENCES etl_jobs(id)
);

-- Create indexes on transactions
CREATE INDEX idx_transaction_date ON transactions(transaction_date);
CREATE INDEX idx_transaction_customer ON transactions(customer_id);
CREATE INDEX idx_transaction_product ON transactions(product_id);
CREATE INDEX idx_transaction_etl_job ON transactions(etl_job_id);
CREATE INDEX idx_duplicate_detection ON transactions(transaction_date, customer_id, total_amount);

-- Create error_logs table
CREATE TABLE error_logs (
    id BIGSERIAL PRIMARY KEY,
    etl_job_id BIGINT NOT NULL,
    row_number INTEGER NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    error_description TEXT NOT NULL,
    original_value TEXT,
    error_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_error_log_etl_job FOREIGN KEY (etl_job_id) REFERENCES etl_jobs(id)
);

-- Create index on error_logs
CREATE INDEX idx_error_log_job ON error_logs(etl_job_id);
