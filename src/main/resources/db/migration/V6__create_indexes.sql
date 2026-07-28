CREATE INDEX ix_application_applicant_status ON dbo.job_application (applicant_id, status);

CREATE INDEX ix_application_job_status ON dbo.job_application (job_id, status, submitted_at);

CREATE INDEX ix_education_applicant ON dbo.applicant_education (applicant_id);

CREATE INDEX ix_experience_applicant ON dbo.applicant_experience (applicant_id);

CREATE INDEX ix_outbox_pending ON dbo.notification_outbox (status, created_at);

CREATE INDEX ix_job_public_window ON dbo.job_posting (status, application_start_at, application_end_at);
