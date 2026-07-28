CREATE TABLE dbo.recruitment_stage (
  stage_id BIGINT IDENTITY PRIMARY KEY,
  job_id BIGINT NOT NULL REFERENCES dbo.job_posting (job_id),
  stage_code VARCHAR(40) NOT NULL,
  stage_order INT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
  CONSTRAINT uq_job_stage UNIQUE (job_id, stage_code)
);

CREATE TABLE dbo.shortlist_batch (
  batch_id BIGINT IDENTITY PRIMARY KEY,
  stage_id BIGINT NOT NULL REFERENCES dbo.recruitment_stage (stage_id),
  file_id BIGINT NULL REFERENCES dbo.file_asset (file_id),
  status VARCHAR(30) NOT NULL,
  created_by BIGINT NOT NULL,
  created_at DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME()
);

CREATE TABLE dbo.stage_candidate (
  stage_candidate_id BIGINT IDENTITY PRIMARY KEY,
  stage_id BIGINT NOT NULL REFERENCES dbo.recruitment_stage (stage_id),
  application_id BIGINT NOT NULL REFERENCES dbo.job_application (application_id),
  roll_number VARCHAR(50) NULL,
  CONSTRAINT uq_stage_candidate UNIQUE (stage_id, application_id)
);
