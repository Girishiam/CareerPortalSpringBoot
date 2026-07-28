# Career Portal backend contract

## Finalized requirement decisions

- Microsoft SQL Server is the system of record. Hibernate validates mappings and never creates or updates production schema.
- Applicant biodata uses fixed relational tables. Divisions, districts, upazilas, qualifications, subjects, institutions, and policies are configurable master data; form fields are not dynamic.
- `cv_number` permanently identifies an applicant. Each submitted job application receives its own `tracking_number`. Authentication uses email, mobile, username, or employee ID.
- Submission eligibility includes age, education, experience, required profile data, addresses, and mandatory documents. Any failure returns HTTP 422 and no tracking number is allocated.
- Application count is controlled by `circular_application_policy.max_applications_per_applicant`, not a yes/no flag.
- A job's `rules_version` is copied to each draft. Published jobs and their requirements are immutable; changed requirements require a new version.
- The exact deadline rule is `[application_start_at, application_end_at)`: the start instant is accepted and the end instant is rejected. All comparisons use SQL Server UTC time.

## REST contract

The generated OpenAPI contract is available at `/v3/api-docs` and Swagger UI at `/swagger-ui/index.html`.

Implemented first-release endpoints:

- `POST /api/v1/auth/applicants/register`
- `POST /api/v1/auth/login`
- `GET|PUT /api/v1/me/profile`
- `PUT /api/v1/me/addresses/{PRESENT|PERMANENT}`
- `POST|PUT|DELETE /api/v1/me/educations[/{educationId}]`
- `POST|PUT|DELETE /api/v1/me/experiences[/{experienceId}]`
- `POST /api/v1/me/documents`
- `GET /api/v1/jobs` and `GET /api/v1/jobs/{jobId}`
- `POST|PUT /api/v1/admin/jobs[/{jobId}]`
- `POST /api/v1/admin/jobs/{jobId}/{approve|publish|close}`
- `POST /api/v1/jobs/{jobId}/applications`
- `POST /api/v1/me/applications/{applicationId}/submit`

Every protected applicant operation derives the user and applicant from the signed JWT. IDs in paths identify resources, not owners; the ownership predicate is always part of the database query.

## Transaction ownership

| Action                                      | Owning transaction                                           | Atomic database work                                                                                                                                                                                                                                                                                                   |
| ------------------------------------------- | ------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Register applicant                          | `AuthService.register`                                       | Normalize and de-duplicate login values; insert an active user; assign APPLICANT; insert applicant; generate the permanent CV number; write audit. Any failure rolls back all rows. OTP/2FA verification is disabled for the current phase.                                                                            |
| Update profile/address/education/experience | Corresponding `ApplicantService` method                      | Validate and write one authenticated applicant aggregate. Address parent-child checks and write occur in the same transaction.                                                                                                                                                                                         |
| Upload document                             | `DocumentService.store`                                      | Validate bytes before the transaction writes metadata; insert `file_asset`; retire the previous active document; insert applicant relationship. Object cleanup must run if a database rollback occurs after a newly written object.                                                                                    |
| Create/update/transition job                | Corresponding `JobService` method                            | Check allowed state and perform the state change. Published requirements are not editable.                                                                                                                                                                                                                             |
| Create/resume draft                         | `ApplicationService.draft`                                   | Check the published window using `SYSUTCDATETIME`; enforce circular limit; return existing application or insert one. `UNIQUE(job_id, applicant_id)` is final duplicate protection.                                                                                                                                    |
| Submit application                          | `ApplicationService.submit`                                  | Lock owned draft; recheck deadline; validate completeness; evaluate and persist eligibility; block failures before numbering; copy all snapshots; execute `usp_SubmitJobApplication`; append status history; enqueue notification outbox. Any failure rolls back snapshots, status, tracking number, and outbox event. |
| Allocate tracking number                    | Nested in submission transaction, `usp_SubmitJobApplication` | Lock application; recheck owner/state/deadline/eligibility/snapshot; allocate from SQL sequence; mark submitted; return generated values. It never commits independently.                                                                                                                                              |

`@Transactional` uses the default `REQUIRED` propagation. `JdbcTemplate` and JPA therefore share the same datasource transaction; stored procedures must not contain their own `BEGIN/COMMIT TRANSACTION`.

## Validation and business rules

- All mandatory strings are trimmed and whitespace-only values are rejected.
- Email is lower-cased; Bangladeshi mobile syntax is validated.
- Education create and update call the same validator: GPA/CGPA needs a scale, result cannot exceed scale, division/class needs a grade, and passing year cannot be future.
- Experience end cannot precede start; current employment cannot have an end date.
- District must belong to division and upazila must belong to district.
- Uploads are limited by size, detected from magic bytes, fully decoded when images, dimension-checked, SHA-256 hashed, and stored outside applicant tables.
- Browser values never control owner, state, publisher, timestamps, eligibility, CV/tracking number, or deadlines.
- Submitted reports and downstream recruitment processes must read application snapshots.
- Duplicate/state conflicts return 409; eligibility failures return 422; SQL errors 50021–50025 are translated to the common error shape.

## Deferred schema and features

The initial migration contains the recruitment-stage/shortlist foundation only. Exam sessions, seat allocation, admit cards, results, viva, merit lists, appointment letters, CMS, support tickets, saved jobs, quota rules, holiday calendars, notification templates, and their APIs require approved table designs before implementation.

Production file storage should replace the local protected-storage adapter with S3/MinIO and add an antivirus scanner. Redis caching, CAPTCHA/rate limiting, refresh-token rotation and OTP delivery/verification are operational integrations still required before internet deployment.

## SQL Server TLS configuration

The development default keeps transport encryption enabled but accepts a locally
self-signed SQL Server certificate:

```text
DB_ENCRYPT=true
DB_TRUST_CERT=true
```

Production must use:

```text
DB_ENCRYPT=true
DB_TRUST_CERT=false
```

With production validation enabled, SQL Server must present a certificate whose
hostname matches `DB_HOST` and whose issuing CA is trusted by the application's
JVM. Do not use `DB_TRUST_CERT=true` as the production certificate fix.
