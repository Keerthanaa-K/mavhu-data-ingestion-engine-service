-- The ESG ingestion DDL (db/ddl/esg_file_ingestion.sql) runs before Hibernate creates the mavhu schema.
CREATE SCHEMA IF NOT EXISTS mavhu;

-- H2 has no equivalent to PostgreSQL's CREATE TYPE ... AS ENUM.
-- A DOMAIN gives Hibernate's generated DDL a "customer_identifier" type to resolve
-- when creating mavhu.customers, mirroring the real Postgres enum for tests.
CREATE DOMAIN IF NOT EXISTS customer_identifier AS VARCHAR(255) CHECK (VALUE IN ('BANK', 'AGROBUSINESS'));
