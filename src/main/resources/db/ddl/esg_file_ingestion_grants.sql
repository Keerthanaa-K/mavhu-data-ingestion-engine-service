-- Grants for the ESG file ingestion tables (created by esg_file_ingestion.sql).
--
-- The tables are owned by mavhu_ingestion_service (the role that ran the DDL). mavhu_admin gets
-- data manipulation only: SELECT, INSERT, UPDATE, DELETE. It deliberately does NOT get TRUNCATE,
-- REFERENCES or TRIGGER, and does not own the tables, so it cannot ALTER, DROP or otherwise change DDL.
-- Identity sequences get USAGE, SELECT (nextval/currval) but not UPDATE (setval).
--
-- Run as the table owner after esg_file_ingestion.sql (PostgreSQL only; not run by the H2 tests):
--   psql -h <host> -U mavhu_ingestion_service -d mavhu_db -v ON_ERROR_STOP=1 -f src/main/resources/db/ddl/esg_file_ingestion_grants.sql

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE
    mavhu.ingestion_jobs,
    mavhu.esg_entity_reference,
    mavhu.esg_scope1_direct_emissions,
    mavhu.esg_scope2_energy_emissions,
    mavhu.esg_scope3_operational_emissions,
    mavhu.esg_financed_emissions,
    mavhu.esg_dq_portfolio_summary,
    mavhu.esg_insurance_emissions,
    mavhu.esg_facilitated_emissions,
    mavhu.esg_satellite_geospatial,
    mavhu.esg_social_financial_inclusion,
    mavhu.esg_social_workforce,
    mavhu.esg_governance_board,
    mavhu.esg_governance_incidents,
    mavhu.esg_ingestion_audit_log
TO mavhu_admin;

GRANT USAGE, SELECT ON SEQUENCE
    mavhu.esg_entity_reference_id_seq,
    mavhu.esg_scope1_direct_emissions_id_seq,
    mavhu.esg_scope2_energy_emissions_id_seq,
    mavhu.esg_scope3_operational_emissions_id_seq,
    mavhu.esg_financed_emissions_id_seq,
    mavhu.esg_dq_portfolio_summary_id_seq,
    mavhu.esg_insurance_emissions_id_seq,
    mavhu.esg_facilitated_emissions_id_seq,
    mavhu.esg_satellite_geospatial_id_seq,
    mavhu.esg_social_financial_inclusion_id_seq,
    mavhu.esg_social_workforce_id_seq,
    mavhu.esg_governance_board_id_seq,
    mavhu.esg_governance_incidents_id_seq,
    mavhu.esg_ingestion_audit_log_id_seq
TO mavhu_admin;

-- Default privileges: tables and sequences that mavhu_ingestion_service creates in mavhu from now on
-- get the same DML-only grants automatically. Only affects objects created by mavhu_ingestion_service.
ALTER DEFAULT PRIVILEGES FOR ROLE mavhu_ingestion_service IN SCHEMA mavhu
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO mavhu_admin;

ALTER DEFAULT PRIVILEGES FOR ROLE mavhu_ingestion_service IN SCHEMA mavhu
    GRANT USAGE, SELECT ON SEQUENCES TO mavhu_admin;
