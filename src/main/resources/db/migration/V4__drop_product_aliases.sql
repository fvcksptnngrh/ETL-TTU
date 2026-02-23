-- V4: Drop product_aliases table
-- Inconsistencies can be queried directly from transaction_items
-- No need for a separate table

DROP TABLE IF EXISTS product_aliases;
