/*
  Uttara Bank PLC's published "Different Wings" structure is used as the
  primary reference. Codes are stable application identifiers; names remain
  human-readable for job creation and reporting.
*/
CREATE TABLE #bank_departments (
  code VARCHAR(30) NOT NULL PRIMARY KEY,
  name NVARCHAR(120) NOT NULL
);

INSERT #bank_departments(code,name)
VALUES
  ('HR','Human Resources Division'),
  ('PERSONNEL','Personnel Department'),
  ('DISCIPLINARY','Disciplinary Department'),
  ('TEST_KEY','Test Key Department'),
  ('RESEARCH_PLANNING','Research and Planning Department'),
  ('RISK_MGMT','Risk Management Department'),
  ('CENTRAL_ACCOUNTS','Central Accounts Division'),
  ('ACCOUNTS','Accounts Department'),
  ('RECONCILIATION','Reconciliation Department'),
  ('CREDIT','Credit Division'),
  ('CREDIT_APPROVAL','Credit Approval Department'),
  ('CREDIT_ADMIN','Credit Administration and Monitoring Department'),
  ('LEASE_FINANCE','Lease Finance Department'),
  ('RECOVERY','Recovery Department'),
  ('SUSTAINABLE_FIN','Sustainable Finance Department'),
  ('ICT','Information and Communication Technology Division'),
  ('MIS','Management Information Systems Department'),
  ('ICT_DEV_SUPPORT','ICT Development and Support Department'),
  ('CARD','Card Department'),
  ('ICT_SECURITY','ICT Security Cell'),
  ('ICC','Internal Control and Compliance Division'),
  ('AUDIT_INSPECTION','Audit and Inspection Department'),
  ('ICC_MONITORING','Internal Control Monitoring Department'),
  ('COMPLIANCE','Compliance Department'),
  ('BANKING_CONTROL','Banking Control and Common Services Division'),
  ('AML','Anti-Money Laundering Department'),
  ('BRANCH_OPERATIONS','Branch Operations Department'),
  ('BUSINESS_PROMOTION','Business Promotion Department'),
  ('BOARD_SHARE','Board and Share Division'),
  ('BOARD','Board Department'),
  ('SHARE','Share Department'),
  ('INTERNATIONAL','International Division'),
  ('CORRESPONDENT_BANK','Correspondent Banking Department'),
  ('REMITTANCE','Remittance Department'),
  ('TRADE_SERVICES','Trade Services Department'),
  ('OFFSHORE_BANKING','Offshore Banking Unit'),
  ('TREASURY','Treasury Division'),
  ('TREASURY_FRONT','Treasury Front Office'),
  ('TREASURY_MID','Treasury Mid Office'),
  ('TREASURY_BACK','Treasury Back Office'),
  ('ALM','Asset and Liability Management Department'),
  ('MONEY_MARKET','Money Market Department'),
  ('ESTABLISHMENT','Establishment Division'),
  ('GENERAL_SERVICES','General Services Department'),
  ('TRANSPORT','Transport Department'),
  ('STATIONERY_RECORDS','Stationery and Records Department'),
  ('ENGINEERING','Engineering Department'),
  ('PROCUREMENT','Purchase and Procurement Department'),
  ('CORPORATE_BANKING','Corporate Banking Division'),
  ('CREDIT_MARKETING','Credit Marketing Department'),
  ('CREDIT_BUS_DEV','Credit Business Development Department'),
  ('WOMEN_ENTREPRENEUR','Women Entrepreneur Development Unit'),
  ('MD_SECRETARIAT','Managing Director Secretariat'),
  ('PUBLIC_RELATIONS','Public Relations Department'),
  ('SME','SME Unit'),
  ('CIB','Credit Information Bureau Cell'),
  ('TRAINING','Training Institute');

UPDATE target
   SET target.name=source.name
  FROM dbo.department target
  JOIN #bank_departments source ON source.code=target.code;

DECLARE @columns NVARCHAR(MAX)=N'code,name';
DECLARE @selectColumns NVARCHAR(MAX)=N'source.code,source.name';

IF COL_LENGTH('dbo.department','department_code') IS NOT NULL
BEGIN
  SET @columns+=N',department_code';
  SET @selectColumns+=N',source.code';
END;

IF COL_LENGTH('dbo.department','department_name') IS NOT NULL
BEGIN
  SET @columns+=N',department_name';
  SET @selectColumns+=N',source.name';
END;

DECLARE @insertSql NVARCHAR(MAX);
IF COLUMNPROPERTY(OBJECT_ID('dbo.department'),'department_id','IsIdentity')=1
  SET @insertSql=N'
    INSERT dbo.department('+@columns+N')
    SELECT '+@selectColumns+N'
      FROM #bank_departments source
     WHERE NOT EXISTS (
       SELECT 1 FROM dbo.department target WHERE target.code=source.code
     );';
ELSE
  SET @insertSql=N'
    DECLARE @maximumId BIGINT=
      ISNULL((SELECT MAX(department_id) FROM dbo.department),0);
    INSERT dbo.department(department_id,'+@columns+N')
    SELECT @maximumId+ROW_NUMBER() OVER(ORDER BY source.code),'+@selectColumns+N'
      FROM #bank_departments source
     WHERE NOT EXISTS (
       SELECT 1 FROM dbo.department target WHERE target.code=source.code
     );';

EXEC sys.sp_executesql @insertSql;
DROP TABLE #bank_departments;
