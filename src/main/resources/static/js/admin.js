const token = localStorage.getItem("careerPortalToken");
const roles = JSON.parse(localStorage.getItem("careerPortalRoles") || "[]");
const page = document.body.dataset.adminPage;
const alertBox = document.querySelector("#adminAlert");
const recentSubmissions = new WeakSet();

document.addEventListener(
  "submit",
  (event) => {
    const form = event.target;
    if (recentSubmissions.has(form)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      return;
    }
    recentSubmissions.add(form);
    window.setTimeout(() => recentSubmissions.delete(form), 1500);
  },
  true,
);

if (
  !token ||
  !roles.some((role) => ["HR_ADMIN", "SYSTEM_ADMIN"].includes(role))
) {
  location.replace("/admin/login");
}

const value = (row, name) => row?.[name] ?? row?.[name.toUpperCase()];
const escapeHtml = (input) =>
  String(input ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");

function notify(message, type = "success") {
  if (!alertBox) return;
  alertBox.textContent = message;
  alertBox.className = `alert show ${type}`;
  window.scrollTo({ top: 0, behavior: "smooth" });
  window.setTimeout(() => alertBox.classList.remove("show"), 5000);
}

async function api(url, options = {}) {
  const response = await fetch(url, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      ...options.headers,
    },
  });

  if (response.status === 401 || response.status === 403) {
    localStorage.removeItem("careerPortalToken");
    localStorage.removeItem("careerPortalRoles");
    location.replace("/admin/login");
    throw new Error("Your administrator session has expired.");
  }

  const result =
    response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(result?.message || "The request could not be completed.");
  }
  return result;
}

function json(method, body) {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}

document.querySelector("#adminLogout")?.addEventListener("click", () => {
  localStorage.removeItem("careerPortalToken");
  localStorage.removeItem("careerPortalRoles");
  location.replace("/admin/login");
});

async function loadDashboard() {
  const data = await api("/api/v1/admin/dashboard");
  document.querySelector("#metricJobs").textContent =
    value(data, "total_jobs") || 0;
  document.querySelector("#metricPublished").textContent =
    value(data, "published_jobs") || 0;
  document.querySelector("#metricApplicants").textContent =
    value(data, "total_applicants") || 0;
  document.querySelector("#metricApplications").textContent =
    value(data, "submitted_applications") || 0;

  const recent = data.recentApplications || [];
  document.querySelector("#recentApplicationRows").innerHTML = recent.length
    ? recent
        .map(
          (application) => `
            <tr>
              <td>${escapeHtml(value(application, "full_name") || "Draft applicant")}</td>
              <td>${escapeHtml(value(application, "job_code"))} · ${escapeHtml(value(application, "job_title"))}</td>
              <td>${escapeHtml(value(application, "tracking_number") || "Draft")}</td>
              <td><span class="badge">${escapeHtml(value(application, "status"))}</span></td>
            </tr>
          `,
        )
        .join("")
    : `<tr><td colspan="4" class="table-empty">No applications yet.</td></tr>`;
}

async function loadJobs() {
  const jobs = await api("/api/v1/admin/jobs");
  const list = document.querySelector("#adminJobList");
  list.innerHTML = jobs.length
    ? jobs.map(jobCard).join("")
    : `<div class="empty">No jobs have been created.</div>`;

  list.querySelectorAll("[data-job-action]").forEach((button) => {
    button.addEventListener("click", () =>
      transitionJob(button.dataset.jobId, button.dataset.jobAction),
    );
  });
}

function jobCard(job) {
  const status = value(job, "status");
  const id = value(job, "job_id");
  const action =
    status === "DRAFT"
      ? actionButton(id, "approve", "Approve", "btn-secondary")
      : status === "APPROVED"
        ? actionButton(id, "publish", "Publish", "btn-primary")
        : status === "PUBLISHED"
          ? actionButton(id, "close", "Close", "btn-danger")
          : "";

  return `
    <article class="card admin-job-card">
      <div>
        <div class="job-title-row">
          <span class="badge">${escapeHtml(status)}</span>
          <span class="job-code">${escapeHtml(value(job, "job_code"))}</span>
        </div>
        <h2>${escapeHtml(value(job, "job_title"))}</h2>
        <p>${escapeHtml(value(job, "vacancy_count"))} vacancies · ${escapeHtml(value(job, "application_count"))} applications</p>
      </div>
      <div class="row-actions">
        <a class="btn btn-secondary" href="/admin/jobs/${id}">Details</a>
        <a class="btn btn-secondary" href="/admin/applications?jobId=${id}">Applications</a>
        ${action}
      </div>
    </article>
  `;
}

function actionButton(id, action, label, style) {
  return `<button class="btn ${style}" data-job-action="${action}" data-job-id="${id}">${label}</button>`;
}

async function transitionJob(jobId, action) {
  try {
    await api(`/api/v1/admin/jobs/${jobId}/${action}`, { method: "POST" });
    notify(`Job ${action} completed.`);
    await loadJobs();
  } catch (error) {
    notify(error.message, "error");
  }
}

async function loadDepartments() {
  const departments = await api("/api/v1/master-data/departments");
  document.querySelector("[name=departmentId]").innerHTML = departments
    .map(
      (department) =>
        `<option value="${value(department, "id")}">${escapeHtml(value(department, "name"))}</option>`,
    )
    .join("");
}

async function initializeJobForm() {
  const [, qualifications] = await Promise.all([
    loadDepartments(),
    api("/api/v1/master-data/qualifications"),
  ]);
  const form = document.querySelector("#adminJobForm");
  const educationPanel = document.querySelector("#educationRequirements");
  const educationRows = document.querySelector("#educationRequirementRows");
  const educationToggle = form.elements.specificEducationRequired;

  function addEducationRequirement() {
    const row = document.createElement("div");
    row.className = "requirement-row";
    row.innerHTML = `
      <select data-requirement="qualificationId" required>
        <option value="">Select level</option>
        ${qualifications
          .map(
            (item) =>
              `<option value="${value(item, "id")}">${escapeHtml(value(item, "name"))}</option>`,
          )
          .join("")}
      </select>
      <input data-requirement="minimumResult" type="number" min="0" max="5" step="0.01" placeholder="e.g. 2.50" required />
      <select data-requirement="resultType" required>
        <option value="GPA">GPA / grade</option>
        <option value="DIVISION">Division</option>
      </select>
      <button class="text-button danger" type="button" aria-label="Remove education requirement">Remove</button>
    `;
    row.querySelector("button").addEventListener("click", () => row.remove());
    educationRows.append(row);
  }

  function syncEducationPanel() {
    educationPanel.hidden = !educationToggle.checked;
    educationPanel.querySelectorAll("select,input").forEach((input) => {
      input.disabled = !educationToggle.checked;
    });
    if (educationToggle.checked && !educationRows.children.length)
      addEducationRequirement();
  }

  function syncAgeFields() {
    const pairs = [
      ["existingEmployeeEligible", "existingEmployeeMaxAge"],
      ["externalApplicantEligible", "externalApplicantMaxAge"],
    ];
    pairs.forEach(([flag, age]) => {
      const enabled = form.elements[flag].checked;
      form.elements[age].disabled = !enabled;
      form.elements[age].required = enabled;
    });
  }

  educationToggle.addEventListener("change", syncEducationPanel);
  form.elements.existingEmployeeEligible.addEventListener("change", syncAgeFields);
  form.elements.externalApplicantEligible.addEventListener("change", syncAgeFields);
  document
    .querySelector("#addEducationRequirement")
    .addEventListener("click", addEducationRequirement);
  syncEducationPanel();
  syncAgeFields();

  document
    .querySelector("#adminJobForm")
    .addEventListener("submit", async (event) => {
      event.preventDefault();
      const body = Object.fromEntries(new FormData(event.currentTarget));
      event.currentTarget
        .querySelectorAll('input[type="checkbox"]')
        .forEach((input) => (body[input.name] = input.checked));
      body.departmentId = Number(body.departmentId);
      body.vacancyCount = Number(body.vacancyCount);
      body.existingEmployeeMaxAge = body.existingEmployeeMaxAge
        ? Number(body.existingEmployeeMaxAge)
        : null;
      body.externalApplicantMaxAge = body.externalApplicantMaxAge
        ? Number(body.externalApplicantMaxAge)
        : null;
      body.applicationStartAt = new Date(body.applicationStartAt).toISOString();
      body.applicationEndAt = new Date(body.applicationEndAt).toISOString();
      body.circularLetterName = body.circularLetter?.name || null;
      delete body.circularLetter;
      body.educationRequirements = educationToggle.checked
        ? [...educationRows.querySelectorAll(".requirement-row")].map((row) => ({
            qualificationId: Number(
              row.querySelector('[data-requirement="qualificationId"]').value,
            ),
            minimumResult: Number(
              row.querySelector('[data-requirement="minimumResult"]').value,
            ),
            resultType: row.querySelector('[data-requirement="resultType"]')
              .value,
          }))
        : [];

      try {
        const job = await api("/api/v1/admin/jobs", json("POST", body));
        location.href = `/admin/jobs/${value(job, "job_id")}`;
      } catch (error) {
        notify(error.message, "error");
      }
    });
}

function pathId() {
  return Number(location.pathname.split("/").filter(Boolean).at(-1));
}

async function loadJobDetails() {
  const job = await api(`/api/v1/jobs/${pathId()}`);
  document.querySelector("#jobDetailsTitle").textContent = value(
    job,
    "job_title",
  );
  document.querySelector("#jobDetailsCode").textContent =
    `${value(job, "job_code")} · ${value(job, "status")}`;
  document.querySelector("#jobDetails").innerHTML = detailsMarkup([
    ["Status", value(job, "status")],
    ["Vacancies", value(job, "vacancy_count")],
    ["Employment type", value(job, "employment_type")],
    ["Application starts", value(job, "application_start_at")],
    ["Application ends", value(job, "application_end_at")],
    ["Age reference date", value(job, "age_reference_date")],
    ["Description", value(job, "job_description")],
    ["Responsibilities", value(job, "responsibilities") || "—"],
  ]);
}

function detailsMarkup(items) {
  return items
    .map(
      ([label, content]) => `
        <div class="detail-item">
          <span>${escapeHtml(label)}</span>
          <strong>${escapeHtml(content ?? "—")}</strong>
        </div>
      `,
    )
    .join("");
}

async function initializeApplications() {
  const jobs = await api("/api/v1/admin/jobs");
  const filter = document.querySelector("#applicationJobFilter");
  filter.innerHTML =
    `<option value="">Select a job</option>` +
    jobs
      .map(
        (job) =>
          `<option value="${value(job, "job_id")}">${escapeHtml(value(job, "job_code"))} · ${escapeHtml(value(job, "job_title"))}</option>`,
      )
      .join("");

  const requestedJob = new URLSearchParams(location.search).get("jobId");
  if (requestedJob) filter.value = requestedJob;
  filter.addEventListener("change", () => loadApplications(0));
  await loadApplications(0);
}

async function loadApplications(pageNumber) {
  const jobId = document.querySelector("#applicationJobFilter").value;
  const rows = document.querySelector("#applicationRows");
  if (!jobId) {
    rows.innerHTML = `<tr><td colspan="6" class="table-empty">Select a job to review applications.</td></tr>`;
    renderPagination(0, 0, () => {});
    return;
  }

  const result = await api(
    `/api/v1/admin/jobs/${jobId}/applications?page=${pageNumber}&size=20`,
  );
  rows.innerHTML = result.content.length
    ? result.content
        .map(
          (application) => `
            <tr>
              <td>${escapeHtml(value(application, "full_name"))}</td>
              <td>${escapeHtml(value(application, "cv_number"))}</td>
              <td>${escapeHtml(value(application, "email") || value(application, "mobile") || "—")}</td>
              <td>${escapeHtml(value(application, "tracking_number") || "Draft")}</td>
              <td><span class="badge">${escapeHtml(value(application, "status"))}</span></td>
              <td><a class="text-button" href="/admin/applications/${value(application, "application_id")}">Review</a></td>
            </tr>
          `,
        )
        .join("")
    : `<tr><td colspan="6" class="table-empty">No applications for this job.</td></tr>`;
  renderPagination(result.page, result.totalPages, loadApplications);
}

function renderPagination(current, total, callback) {
  const pagination = document.querySelector("#adminPagination");
  if (!pagination) return;
  pagination.innerHTML = Array.from(
    { length: total },
    (_, index) =>
      `<button class="btn ${index === current ? "btn-primary" : "btn-secondary"}" data-page="${index}">${index + 1}</button>`,
  ).join("");
  pagination.querySelectorAll("[data-page]").forEach((button) => {
    button.addEventListener("click", () =>
      callback(Number(button.dataset.page)),
    );
  });
}

async function loadApplicationDetails() {
  const application = await api(`/api/v1/admin/applications/${pathId()}`);
  document.querySelector("#applicationDetailsTitle").textContent = value(
    application,
    "full_name",
  );
  document.querySelector("#applicationDetailsTracking").textContent =
    value(application, "tracking_number") || "Draft application";

  const education = application.educations || [];
  const experience = application.experiences || [];
  const documents = application.documents || [];
  document.querySelector("#applicationDetails").innerHTML = `
    <article class="card details-grid">
      ${detailsMarkup([
        [
          "Job",
          `${value(application, "job_code")} · ${value(application, "job_title")}`,
        ],
        ["CV number", value(application, "cv_number")],
        ["Status", value(application, "status")],
        ["Eligibility", value(application, "eligibility_status")],
        ["Email", value(application, "email")],
        ["Mobile", value(application, "mobile")],
        ["Date of birth", value(application, "date_of_birth")],
        ["Nationality", value(application, "nationality")],
      ])}
    </article>
    ${recordCard("Education", education, (item) => `Qualification #${value(item, "qualification_id")} · ${value(item, "passing_year")}`)}
    ${recordCard("Experience", experience, (item) => `${value(item, "designation")} at ${value(item, "employer_name")}`)}
    ${recordCard("Documents", documents, (item) => `${value(item, "document_type")} · ${value(item, "validation_status")}`)}
  `;
}

function recordCard(title, records, label) {
  const content = records.length
    ? records
        .map(
          (record) =>
            `<div class="list-item">${escapeHtml(label(record))}</div>`,
        )
        .join("")
    : `<div class="empty">No ${title.toLowerCase()} records.</div>`;
  return `<article class="card"><h2>${title}</h2><div class="list">${content}</div></article>`;
}

async function loadUsers(pageNumber = 0) {
  const result = await api(`/api/v1/admin/users?page=${pageNumber}&size=20`);
  document.querySelector("#userRows").innerHTML = result.content.length
    ? result.content
        .map(
          (user) => `
            <tr>
              <td>#${escapeHtml(value(user, "user_id"))}</td>
              <td>${escapeHtml(value(user, "email") || value(user, "mobile") || value(user, "username"))}</td>
              <td>${escapeHtml(value(user, "roles") || "No role")}</td>
              <td><span class="badge">${escapeHtml(value(user, "status"))}</span></td>
              <td>${escapeHtml(value(user, "created_at"))}</td>
            </tr>
          `,
        )
        .join("")
    : `<tr><td colspan="5" class="table-empty">No users found.</td></tr>`;
  renderPagination(result.page, result.totalPages, loadUsers);
}

const loaders = {
  dashboard: loadDashboard,
  jobs: loadJobs,
  "job-form": initializeJobForm,
  "job-details": loadJobDetails,
  applications: initializeApplications,
  "application-details": loadApplicationDetails,
  users: loadUsers,
};

loaders[page]?.().catch((error) => notify(error.message, "error"));
