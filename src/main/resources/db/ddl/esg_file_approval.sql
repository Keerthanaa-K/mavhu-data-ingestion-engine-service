-- DDL: uploaded files registry, user attribution and approval workflow for ESG file ingestion.
--
-- Run order (all idempotent):
--   1. esg_file_ingestion.sql         tables per file type + ingestion_jobs
--   2. esg_file_ingestion_grants.sql  DML grants for mavhu_admin (PostgreSQL only)
--   3. esg_file_approval.sql          this file
--   psql -h <host> -U mavhu_ingestion_service -d mavhu_db -v ON_ERROR_STOP=1 -f src/main/resources/db/ddl/esg_file_approval.sql
--
-- What it does:
--   * mavhu.uploaded_files: one row per uploaded file (who uploaded it, for which customer, where it is
--     in S3) and its approval state.
--   * approval_status is the source of truth (PENDING / APPROVED / REJECTED); is_approved mirrors it as
--     a boolean (true only when APPROVED), enforced by a CHECK constraint.
--   * ingestion_jobs and every ESG table get file_id (-> uploaded_files); ESG tables also get is_approved,
--     updated when a manager approves or rejects the file.
--
-- customer_id / uploaded_by_user_id / reviewed_by_user_id refer to mavhu.customers / mavhu.users. They are
-- validated by the application rather than by foreign keys, because those tables are owned by postgres and
-- mavhu_ingestion_service has no REFERENCES privilege on them.
--
-- Files ingested before this change have no known uploader: they are backfilled from ingestion_jobs with
-- uploaded_by_user_id NULL and approval_status PENDING. Every new upload records the uploading user.
-- Types are portable across PostgreSQL and H2 (PostgreSQL mode) so tests run this same file.

CREATE TABLE IF NOT EXISTS mavhu.uploaded_files (
    file_id             UUID                     PRIMARY KEY,
    customer_id         INTEGER                  NOT NULL,
    -- NULL only for files ingested before user attribution existed
    uploaded_by_user_id INTEGER,
    file_type           VARCHAR(64)              NOT NULL,
    original_file_name  VARCHAR(512),
    content_type        VARCHAR(255),
    size_bytes          BIGINT,
    s3_path             VARCHAR(1024)            NOT NULL,
    approval_status     VARCHAR(16)              NOT NULL DEFAULT 'PENDING',
    is_approved         BOOLEAN                  NOT NULL DEFAULT FALSE,
    reviewed_by_user_id INTEGER,
    reviewed_at         TIMESTAMP WITH TIME ZONE,
    review_comment      TEXT,
    uploaded_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_uploaded_files_s3_path UNIQUE (s3_path),
    CONSTRAINT ck_uploaded_files_approval_status CHECK (approval_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_uploaded_files_is_approved CHECK (is_approved = (approval_status = 'APPROVED')),
    CONSTRAINT ck_uploaded_files_review CHECK (
        (approval_status = 'PENDING' AND reviewed_by_user_id IS NULL AND reviewed_at IS NULL)
        OR (approval_status <> 'PENDING' AND reviewed_by_user_id IS NOT NULL AND reviewed_at IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS idx_uploaded_files_customer_status
    ON mavhu.uploaded_files (customer_id, approval_status, uploaded_at);
CREATE INDEX IF NOT EXISTS idx_uploaded_files_uploaded_by ON mavhu.uploaded_files (uploaded_by_user_id);

-- ingestion_jobs -> uploaded_files ------------------------------------------------------------
ALTER TABLE mavhu.ingestion_jobs
    ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);

-- Backfill: one uploaded_files row per pre-existing job (file_id reuses the job id).
INSERT INTO mavhu.uploaded_files (file_id, customer_id, uploaded_by_user_id, file_type, original_file_name,
                                  s3_path, approval_status, is_approved, uploaded_at)
SELECT j.job_id, j.customer_id, NULL, j.file_type, j.original_file_name, j.s3_path, 'PENDING', FALSE, j.created_at
FROM mavhu.ingestion_jobs j
WHERE j.file_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM mavhu.uploaded_files f WHERE f.s3_path = j.s3_path);

UPDATE mavhu.ingestion_jobs j
SET file_id = (SELECT f.file_id FROM mavhu.uploaded_files f WHERE f.s3_path = j.s3_path)
WHERE j.file_id IS NULL;

ALTER TABLE mavhu.ingestion_jobs ALTER COLUMN file_id SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_ingestion_jobs_file ON mavhu.ingestion_jobs (file_id);

-- ESG tables: file_id + is_approved ----------------------------------------------------------
-- Pattern per table: add columns, backfill file_id from the row's job, then require it.

ALTER TABLE mavhu.esg_entity_reference ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_entity_reference ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_entity_reference t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_entity_reference ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_entity_reference_file ON mavhu.esg_entity_reference (file_id);

ALTER TABLE mavhu.esg_scope1_direct_emissions ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_scope1_direct_emissions ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_scope1_direct_emissions t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_scope1_direct_emissions ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_scope1_file ON mavhu.esg_scope1_direct_emissions (file_id);

ALTER TABLE mavhu.esg_scope2_energy_emissions ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_scope2_energy_emissions ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_scope2_energy_emissions t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_scope2_energy_emissions ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_scope2_file ON mavhu.esg_scope2_energy_emissions (file_id);

ALTER TABLE mavhu.esg_scope3_operational_emissions ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_scope3_operational_emissions ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_scope3_operational_emissions t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_scope3_operational_emissions ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_scope3_file ON mavhu.esg_scope3_operational_emissions (file_id);

ALTER TABLE mavhu.esg_financed_emissions ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_financed_emissions ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_financed_emissions t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_financed_emissions ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_financed_file ON mavhu.esg_financed_emissions (file_id);

ALTER TABLE mavhu.esg_dq_portfolio_summary ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_dq_portfolio_summary ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_dq_portfolio_summary t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_dq_portfolio_summary ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_dq_summary_file ON mavhu.esg_dq_portfolio_summary (file_id);

ALTER TABLE mavhu.esg_insurance_emissions ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_insurance_emissions ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_insurance_emissions t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_insurance_emissions ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_insurance_file ON mavhu.esg_insurance_emissions (file_id);

ALTER TABLE mavhu.esg_facilitated_emissions ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_facilitated_emissions ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_facilitated_emissions t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_facilitated_emissions ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_facilitated_file ON mavhu.esg_facilitated_emissions (file_id);

ALTER TABLE mavhu.esg_satellite_geospatial ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_satellite_geospatial ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_satellite_geospatial t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_satellite_geospatial ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_geospatial_file ON mavhu.esg_satellite_geospatial (file_id);

ALTER TABLE mavhu.esg_social_financial_inclusion ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_social_financial_inclusion ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_social_financial_inclusion t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_social_financial_inclusion ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_fin_inclusion_file ON mavhu.esg_social_financial_inclusion (file_id);

ALTER TABLE mavhu.esg_social_workforce ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_social_workforce ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_social_workforce t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_social_workforce ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_workforce_file ON mavhu.esg_social_workforce (file_id);

ALTER TABLE mavhu.esg_governance_board ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_governance_board ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_governance_board t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_governance_board ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_gov_board_file ON mavhu.esg_governance_board (file_id);

ALTER TABLE mavhu.esg_governance_incidents ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_governance_incidents ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_governance_incidents t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_governance_incidents ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_gov_incidents_file ON mavhu.esg_governance_incidents (file_id);

ALTER TABLE mavhu.esg_ingestion_audit_log ADD COLUMN IF NOT EXISTS file_id UUID REFERENCES mavhu.uploaded_files (file_id);
ALTER TABLE mavhu.esg_ingestion_audit_log ADD COLUMN IF NOT EXISTS is_approved BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE mavhu.esg_ingestion_audit_log t SET file_id = (SELECT j.file_id FROM mavhu.ingestion_jobs j WHERE j.job_id = t.ingestion_job_id) WHERE t.file_id IS NULL;
ALTER TABLE mavhu.esg_ingestion_audit_log ALTER COLUMN file_id SET NOT NULL;
CREATE INDEX IF NOT EXISTS idx_esg_audit_log_file ON mavhu.esg_ingestion_audit_log (file_id);
