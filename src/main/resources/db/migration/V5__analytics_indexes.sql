-- V5: Add indexes for analytics query performance
-- transaction_items.item_code is used in analytics GROUP BY and WHERE filters
CREATE INDEX IF NOT EXISTS idx_transaction_items_item_code ON transaction_items (item_code);

-- Composite index for product sales trend queries (JOIN + GROUP BY)
CREATE INDEX IF NOT EXISTS idx_transaction_items_item_code_transaction_id ON transaction_items (item_code, transaction_id);
