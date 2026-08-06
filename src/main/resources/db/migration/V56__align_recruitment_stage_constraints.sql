-- Reconcile legacy stage constraints with the configurable shortlist workflow.
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.recruitment_stage') AND name='CK_recruitment_stage_status')
  ALTER TABLE dbo.recruitment_stage DROP CONSTRAINT CK_recruitment_stage_status;
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.recruitment_stage') AND name='ck_recruitment_stage_status_v56')
  ALTER TABLE dbo.recruitment_stage DROP CONSTRAINT ck_recruitment_stage_status_v56;
ALTER TABLE dbo.recruitment_stage ADD CONSTRAINT ck_recruitment_stage_status_v56
  CHECK (status IN ('DRAFT','PLANNED','ACTIVE','COMPLETED','CANCELLED'));

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.recruitment_stage') AND name='CK_recruitment_stage_type')
  ALTER TABLE dbo.recruitment_stage DROP CONSTRAINT CK_recruitment_stage_type;
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.recruitment_stage') AND name='ck_recruitment_stage_type_v56')
  ALTER TABLE dbo.recruitment_stage DROP CONSTRAINT ck_recruitment_stage_type_v56;
ALTER TABLE dbo.recruitment_stage ADD CONSTRAINT ck_recruitment_stage_type_v56
  CHECK (stage_type IN ('SCREENING','MCQ','WRITTEN','PRACTICAL','VIVA','INTERVIEW','ASSESSMENT','FINAL_SELECTION','CUSTOM'));
