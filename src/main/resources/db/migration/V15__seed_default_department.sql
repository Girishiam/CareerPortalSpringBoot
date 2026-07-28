/*
  Ensure a usable department exists in installations whose legacy master-data
  table was created but never populated.
*/
IF OBJECT_ID('dbo.department', 'U') IS NOT NULL
AND COL_LENGTH('dbo.department', 'code') IS NULL
BEGIN
  ALTER TABLE dbo.department ADD code VARCHAR(30) NULL;
END;

IF OBJECT_ID('dbo.department', 'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM dbo.department)
BEGIN
  IF COL_LENGTH('dbo.department', 'department_name') IS NOT NULL
  BEGIN
    IF COL_LENGTH('dbo.department', 'department_code') IS NOT NULL
    BEGIN
      IF COLUMNPROPERTY(OBJECT_ID('dbo.department'), 'department_id', 'IsIdentity') = 1
        EXEC('INSERT dbo.department (code,name,department_code,department_name)
              VALUES (''HR'',''Human Resources'',''HR'',''Human Resources'')');
      ELSE
        EXEC('INSERT dbo.department
                (department_id,code,name,department_code,department_name)
              VALUES (1,''HR'',''Human Resources'',''HR'',''Human Resources'')');
    END
    ELSE
    BEGIN
      IF COLUMNPROPERTY(OBJECT_ID('dbo.department'), 'department_id', 'IsIdentity') = 1
        EXEC('INSERT dbo.department (code,name,department_name)
              VALUES (''HR'',''Human Resources'',''Human Resources'')');
      ELSE
        EXEC('INSERT dbo.department (department_id,code,name,department_name)
              VALUES (1,''HR'',''Human Resources'',''Human Resources'')');
    END;
  END
  ELSE
  BEGIN
    IF COLUMNPROPERTY(OBJECT_ID('dbo.department'), 'department_id', 'IsIdentity') = 1
      EXEC('INSERT dbo.department (code,name)
            VALUES (''HR'',''Human Resources'')');
    ELSE
      EXEC('INSERT dbo.department (department_id,code,name)
            VALUES (1,''HR'',''Human Resources'')');
  END;
END;

IF OBJECT_ID('dbo.department', 'U') IS NOT NULL
AND COL_LENGTH('dbo.department', 'code') IS NOT NULL
BEGIN
  EXEC('UPDATE dbo.department SET code=''DEPT-''+CONVERT(VARCHAR(20),department_id)
        WHERE code IS NULL');
  ALTER TABLE dbo.department ALTER COLUMN code VARCHAR(30) NOT NULL;
END;
