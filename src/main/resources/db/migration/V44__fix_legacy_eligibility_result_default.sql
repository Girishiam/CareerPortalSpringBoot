IF OBJECT_ID(
     'dbo.df_eligibility_evaluation_overall_result_v43',
     'D'
   ) IS NOT NULL
  ALTER TABLE dbo.eligibility_evaluation
    DROP CONSTRAINT df_eligibility_evaluation_overall_result_v43;

IF COL_LENGTH('dbo.eligibility_evaluation','overall_result') IS NOT NULL
  ALTER TABLE dbo.eligibility_evaluation
    ADD CONSTRAINT df_eligibility_evaluation_overall_result_v44
      DEFAULT 'PENDING' FOR overall_result;
