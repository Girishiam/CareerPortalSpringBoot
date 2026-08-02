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
function formatDate(raw, includeTime = false) {
  if (!raw) return "—";
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return String(raw);
  return new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    ...(includeTime
      ? { hour: "2-digit", minute: "2-digit", hour12: true }
      : {}),
  }).format(date);
}

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

async function loadAuthenticatedImage(image, url) {
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!response.ok) {
    image.alt = "Document unavailable";
    return;
  }
  image.src = URL.createObjectURL(await response.blob());
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
  list.querySelectorAll("[data-delete-job]").forEach((button) => {
    button.addEventListener("click", () => deleteJob(button.dataset.deleteJob));
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
  const deleteAction =
    (status === "DRAFT" &&
      Number(value(job, "application_count") || 0) === 0) ||
    status === "CLOSED"
      ? `<button class="btn btn-danger" data-delete-job="${id}" type="button">${status === "CLOSED" ? "Archive" : "Delete"}</button>`
      : "";

  return `
    <article class="card admin-job-card">
      <div>
        <div class="job-title-row">
          <span class="badge">${escapeHtml(status)}</span>
          <span class="job-code">${escapeHtml(value(job, "job_code"))}</span>
        </div>
        <h2>${escapeHtml(value(job, "job_title"))}</h2>
        <p>${escapeHtml(value(job, "designation") || "Designation not specified")} · ${escapeHtml(value(job, "job_location") || "Location not specified")}</p>
        <p>${escapeHtml(value(job, "vacancy_count"))} vacancies · ${escapeHtml(value(job, "application_count"))} applications</p>
      </div>
      <div class="row-actions">
        <a class="btn btn-secondary" href="/admin/jobs/${id}">Details</a>
        ${status === "DRAFT" || status === "APPROVED" ? `<a class="btn btn-secondary" href="/admin/jobs/${id}/edit">Edit</a>` : ""}
        <a class="btn btn-secondary" href="/admin/applications?jobId=${id}">Applications</a>
        ${action}
        ${deleteAction}
      </div>
    </article>
  `;
}

async function deleteJob(jobId, redirectAfter = false) {
  if (
    !window.confirm(
      "Remove this job? Draft jobs are deleted permanently. Closed jobs are archived while application history is retained.",
    )
  )
    return;
  try {
    await api(`/api/v1/admin/jobs/${jobId}`, { method: "DELETE" });
    notify("Job removed.");
    if (redirectAfter) window.location.assign("/admin/jobs");
    else await loadJobs();
  } catch (error) {
    notify(error.message, "error");
  }
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
  const pathParts = location.pathname.split("/").filter(Boolean);
  const editing = pathParts.at(-1) === "edit";
  const editJobId = editing ? Number(pathParts.at(-2)) : null;
  const [, qualifications] = await Promise.all([
    loadDepartments(),
    api("/api/v1/master-data/qualifications"),
  ]);
  const form = document.querySelector("#adminJobForm");
  const educationPanel = document.querySelector("#educationRequirements");
  const educationRows = document.querySelector("#educationRequirementRows");
  const educationToggle = form.elements.specificEducationRequired;

  function addEducationRequirement(initial = {}) {
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
      <select data-requirement="matchMode" required>
        <option value="MINIMUM_LEVEL">This level or higher</option>
        <option value="EQUIVALENT_LEVEL">Any equivalent level</option>
        <option value="EXACT">Exact qualification only</option>
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
    row.querySelector('[data-requirement="qualificationId"]').value = value(initial, "qualification_id") || "";
    row.querySelector('[data-requirement="matchMode"]').value = value(initial, "match_mode") || "MINIMUM_LEVEL";
    row.querySelector('[data-requirement="minimumResult"]').value = value(initial, "minimum_result") ?? "";
    row.querySelector('[data-requirement="resultType"]').value = value(initial, "result_type") || "GPA";
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

  let existingCircularName = null;
  if (editing) {
    const job = await api(`/api/v1/admin/jobs/${editJobId}`);
    if (!["DRAFT", "APPROVED"].includes(value(job, "status"))) {
      notify("Only unpublished jobs can be edited.", "error");
      form.querySelectorAll("input,select,textarea,button").forEach((element) => { element.disabled = true; });
      return;
    }
    document.title = "Edit job | Career Portal Administration";
    document.querySelector("#jobFormTitle").textContent = "Edit job posting";
    document.querySelector("#jobFormDescription").textContent = "Update this unpublished job before it is published.";
    document.querySelector("#jobFormHint").textContent = `Current state: ${value(job, "status")}. Published jobs cannot be edited.`;
    document.querySelector("#jobFormSubmit").textContent = "Save changes";
    const fields = {
      jobCode: "job_code", jobTitle: "job_title", designation: "designation",
      departmentId: "department_id", employmentType: "employment_type", vacancyCount: "vacancy_count",
      experienceType: "experience_type", jobLocation: "job_location", salaryDetails: "salary_details",
      publicationChannel: "publication_channel", ageReferenceDate: "age_reference_date",
      jobContext: "job_context", jobDescription: "job_description", responsibilities: "responsibilities",
      additionalRequirements: "additional_requirements", compensationBenefits: "compensation_benefits",
      applyPageHeader: "apply_page_header", existingEmployeeMaxAge: "existing_employee_max_age",
      externalApplicantMaxAge: "external_applicant_max_age", maximumDesignation: "maximum_designation",
    };
    Object.entries(fields).forEach(([formName, column]) => {
      if (form.elements[formName]) form.elements[formName].value = value(job, column) ?? "";
    });
    form.elements.applicationStartAt.value = dateTimeLocalValue(value(job, "application_start_at"));
    form.elements.applicationEndAt.value = dateTimeLocalValue(value(job, "application_end_at"));
    form.elements.ageReferenceDate.value = String(value(job, "age_reference_date") || "").substring(0, 10);
    ["specificEducationRequired", "existingEmployeeEligible", "externalApplicantEligible", "spouseDataRequired",
      "mobileRequired", "emailRequired", "relativeDeclarationRequired", "multipleApplicationRestricted", "coverLetterCvRequired"]
      .forEach((name) => { form.elements[name].checked = Boolean(value(job, name.replace(/[A-Z]/g, (letter) => `_${letter.toLowerCase()}`))); });
    educationRows.innerHTML = "";
    (job.educationRequirements || []).forEach(addEducationRequirement);
    existingCircularName = value(job, "circular_letter_name");
    syncEducationPanel();
    syncAgeFields();
  }

  document
    .querySelector("#adminJobForm")
    .addEventListener("submit", async (event) => {
      event.preventDefault();
      const circularFile = event.currentTarget.elements.circularLetter.files[0];
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
      body.circularLetterName = body.circularLetter?.name || existingCircularName || null;
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
            matchMode: row.querySelector('[data-requirement="matchMode"]').value,
          }))
        : [];

      try {
        const job = await api(
          editing ? `/api/v1/admin/jobs/${editJobId}` : "/api/v1/admin/jobs",
          json(editing ? "PUT" : "POST", body),
        );
        if (circularFile) {
          const circular = new FormData();
          circular.append("file", circularFile);
          await api(
            `/api/v1/admin/jobs/${value(job, "job_id")}/circular`,
            { method: "POST", body: circular },
          );
        }
        location.href = `/admin/jobs/${value(job, "job_id")}`;
      } catch (error) {
        notify(error.message, "error");
      }
    });
}

function pathId() {
  return Number(location.pathname.split("/").filter(Boolean).at(-1));
}

function dateTimeLocalValue(raw) {
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return "";
  const pad = (number) => String(number).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
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
    ["Designation", value(job, "designation")],
    ["Department ID", value(job, "department_id")],
    ["Experience type", value(job, "experience_type")],
    ["Location", value(job, "job_location")],
    ["Salary", value(job, "salary_details")],
    ["Publication channel", value(job, "publication_channel")],
    ["Restrict multiple applications", value(job, "multiple_application_restricted") ? "Yes" : "No"],
    ["Apply page header", value(job, "apply_page_header")],
    ["Job context", value(job, "job_context") || "—"],
    ["Description", value(job, "job_description")],
    ["Responsibilities", value(job, "responsibilities") || "—"],
    ["Additional requirements", value(job, "additional_requirements") || "—"],
    ["Compensation and benefits", value(job, "compensation_benefits") || "—"],
  ]);
  const scheduleForm = document.querySelector("#jobScheduleForm");
  scheduleForm.elements.applicationStartAt.value = dateTimeLocalValue(
    value(job, "application_start_at"),
  );
  scheduleForm.elements.applicationEndAt.value = dateTimeLocalValue(
    value(job, "application_end_at"),
  );
  if (value(job, "status") === "CLOSED") {
    scheduleForm.querySelectorAll("input,button").forEach((element) => {
      element.disabled = true;
    });
  }
  scheduleForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    const startsAt = new Date(scheduleForm.elements.applicationStartAt.value);
    const endsAt = new Date(scheduleForm.elements.applicationEndAt.value);
    if (endsAt <= startsAt) {
      notify("Application end must be after the application start.", "error");
      return;
    }
    const body = {
      applicationStartAt: startsAt.toISOString(),
      applicationEndAt: endsAt.toISOString(),
    };
    try {
      await api(
        `/api/v1/admin/jobs/${pathId()}/schedule`,
        json("PATCH", body),
      );
      notify("Application schedule updated.");
      window.location.reload();
    } catch (error) {
      notify(error.message, "error");
    }
  });
  const circular = document.querySelector("#adminCircularActions");
  if (value(job, "circular_pdf_available")) {
    circular.hidden = false;
    circular.querySelector("span").textContent = value(job, "circular_letter_name");
    circular.querySelectorAll("button").forEach((button) => {
      button.addEventListener("click", () =>
        openJobCircular(pathId(), button.dataset.download === "true"),
      );
      });
  }
  const uploadForm = document.querySelector("#adminCircularUploadForm");
  uploadForm.querySelector("button").textContent = value(
    job,
    "circular_pdf_available",
  )
    ? "Replace PDF"
    : "Upload PDF";
  uploadForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await api(`/api/v1/admin/jobs/${pathId()}/circular`, {
        method: "POST",
        body: new FormData(uploadForm),
      });
      notify("Circular PDF saved.");
      window.location.reload();
    } catch (error) {
      notify(error.message, "error");
    }
  });
  const deleteButton = document.querySelector("#deleteJobButton");
  const hasApplications = Number(value(job, "application_count") || 0) > 0;
  const status = value(job, "status");
  const isDraft = status === "DRAFT";
  const isClosed = status === "CLOSED";
  const editButton = document.querySelector("#editJobButton");
  if (["DRAFT", "APPROVED"].includes(status)) {
    editButton.hidden = false;
    editButton.href = `/admin/jobs/${pathId()}/edit`;
  }
  deleteButton.disabled = (!isDraft && !isClosed) || (isDraft && hasApplications);
  deleteButton.textContent = isClosed ? "Archive job" : "Delete job";
  if (!isDraft && !isClosed)
    deleteButton.title = "Only draft or closed jobs can be removed.";
  else if (isDraft && hasApplications)
    deleteButton.title = "Jobs with existing applications cannot be deleted.";
  deleteButton.addEventListener("click", () => deleteJob(pathId(), true));
}

async function openJobCircular(jobId, download) {
  const previewWindow = download ? null : window.open("", "_blank");
  if (previewWindow) {
    previewWindow.document.title = "Loading circular PDF";
    previewWindow.document.body.textContent = "Loading circular PDF...";
  }
  try {
    const response = await fetch(
      `/api/v1/jobs/${jobId}/circular?download=${download}`,
      { headers: { Authorization: `Bearer ${token}` } },
    );
    if (!response.ok) {
      const payload = await response.json().catch(() => null);
      throw new Error(payload?.message || "The circular PDF could not be loaded.");
    }
    const url = URL.createObjectURL(await response.blob());
    if (download) {
      const link = document.createElement("a");
      link.href = url;
      const disposition = response.headers.get("Content-Disposition") || "";
      const filename =
        disposition.match(/filename="?([^";]+)"?/i)?.[1] || "job-circular.pdf";
      link.download = filename;
      link.style.display = "none";
      document.body.append(link);
      link.click();
      link.remove();
    } else {
      if (previewWindow) previewWindow.location.replace(url);
      else {
        const link = document.createElement("a");
        link.href = url;
        link.target = "_blank";
        link.rel = "noopener";
        document.body.append(link);
        link.click();
        link.remove();
      }
    }
    window.setTimeout(() => URL.revokeObjectURL(url), 60000);
  } catch (error) {
    previewWindow?.close();
    notify(error.message, "error");
  }
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
    `<option value="">All jobs</option>` +
    jobs
      .map(
        (job) =>
          `<option value="${value(job, "job_id")}">${escapeHtml(value(job, "job_code"))} · ${escapeHtml(value(job, "job_title"))}</option>`,
      )
      .join("");

  const requestedJob = new URLSearchParams(location.search).get("jobId");
  if (requestedJob) filter.value = requestedJob;
  filter.addEventListener("change", () => loadApplications(0));
  document
    .querySelector("#searchApplications")
    .addEventListener("click", () => loadApplications(0));
  document
    .querySelector("#trackingNumberSearch")
    .addEventListener("keydown", (event) => {
      if (event.key === "Enter") loadApplications(0);
    });
  document
    .querySelector("#clearApplicationFilters")
    .addEventListener("click", () => {
      filter.value = "";
      document.querySelector("#trackingNumberSearch").value = "";
      loadApplications(0);
    });
  document
    .querySelector("#exportApplications")
    .addEventListener("click", exportApplications);
  await loadApplications(0);
}

async function loadApplications(pageNumber) {
  const jobId = document.querySelector("#applicationJobFilter").value;
  const tracking = document.querySelector("#trackingNumberSearch").value.trim();
  const rows = document.querySelector("#applicationRows");
  if (tracking && !/^\d+$/.test(tracking)) {
    notify("Tracking number must contain digits only.", "error");
    return;
  }

  const result = await api(
    `/api/v1/admin/applications?jobId=${encodeURIComponent(jobId)}&trackingNumber=${encodeURIComponent(tracking)}&page=${pageNumber}&size=20`,
  );
  rows.innerHTML = result.content.length
    ? result.content
        .map(
          (application) => `
            <tr>
              <td>${escapeHtml(value(application, "full_name"))}</td>
              <td>${escapeHtml(value(application, "cv_number"))}</td>
              <td>${escapeHtml(value(application, "email") || value(application, "mobile") || "—")}</td>
              <td>${escapeHtml(value(application, "job_code"))}<br><span class="hint">${escapeHtml(value(application, "job_title"))}</span></td>
              <td>${escapeHtml(value(application, "tracking_number"))}</td>
              <td><span class="badge">${escapeHtml(value(application, "eligibility_status"))}</span></td>
              <td>${formatDate(value(application, "submitted_at"), true)}</td>
              <td><a class="text-button" href="/admin/applications/${value(application, "application_id")}">Review</a></td>
            </tr>
          `,
        )
        .join("")
    : `<tr><td colspan="8" class="table-empty">No submitted applications match these filters.</td></tr>`;
  renderPagination(result.page, result.totalPages, loadApplications);
}

async function exportApplications() {
  const jobId = document.querySelector("#applicationJobFilter").value;
  const tracking = document.querySelector("#trackingNumberSearch").value.trim();
  if (tracking && !/^\d+$/.test(tracking)) {
    notify("Tracking number must contain digits only.", "error");
    return;
  }
  try {
    const response = await fetch(
      `/api/v1/admin/applications/export?jobId=${encodeURIComponent(jobId)}&trackingNumber=${encodeURIComponent(tracking)}`,
      { headers: { Authorization: `Bearer ${token}` } },
    );
    if (!response.ok) {
      const payload = await response.json().catch(() => null);
      throw new Error(payload?.message || "The XLSX export could not be generated.");
    }
    const url = URL.createObjectURL(await response.blob());
    const link = document.createElement("a");
    link.href = url;
    link.download = "submitted-applications.xlsx";
    link.style.display = "none";
    document.body.append(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 30000);
  } catch (error) {
    notify(error.message, "error");
  }
}

function renderPagination(current, total, callback) {
  const pagination = document.querySelector("#adminPagination");
  if (!pagination) return;
  if (!total) { pagination.innerHTML = ""; return; }
  const pages = new Set([0, total - 1]);
  for (let index = Math.max(0, current - 2); index <= Math.min(total - 1, current + 2); index++) pages.add(index);
  let previous = -1;
  pagination.innerHTML = [...pages].sort((a, b) => a - b).map((index) => {
    const gap = previous >= 0 && index - previous > 1 ? `<span class="pagination-gap">…</span>` : "";
    previous = index;
    return `${gap}<button class="btn ${index === current ? "btn-primary" : "btn-secondary"}" data-page="${index}">${index + 1}</button>`;
  }).join("");
  pagination.querySelectorAll("[data-page]").forEach((button) => {
    button.addEventListener("click", () =>
      callback(Number(button.dataset.page)),
    );
  });
}

function durationLabel(seconds) {
  const value = Math.max(0, Math.round(Number(seconds) || 0));
  const hours = Math.floor(value / 3600);
  const minutes = Math.floor((value % 3600) / 60);
  const remainder = value % 60;
  return [hours && `${hours}h`, (hours || minutes) && `${minutes}m`, `${remainder}s`].filter(Boolean).join(" ");
}

async function initializeDemoAdmitCards() {
  const batchSelect = document.querySelector("#demoBatch");
  const batches = await api("/api/v1/admin/demo-admit-cards/batches");
  batchSelect.innerHTML = batches.map((batch) =>
    `<option value="${value(batch, "batch_id")}">${escapeHtml(value(batch, "batch_name"))} (${Number(value(batch, "total_records")).toLocaleString()})</option>`).join("");
  if (!batches.length) {
    document.querySelector("#demoCardRows").innerHTML = `<tr><td colspan="8" class="table-empty">No demo batches found.</td></tr>`;
    return;
  }
  const jobCodes = Array.from({ length: 10 }, (_, index) => `JOB-${String(index + 1).padStart(3, "0")}`);
  document.querySelector("#demoJob").insertAdjacentHTML("beforeend", jobCodes.map((code) => `<option>${code}</option>`).join(""));
  let timer;
  const refreshProgress = async () => {
    const progress = await api(`/api/v1/admin/demo-admit-cards/batches/${batchSelect.value}/progress`);
    const percent = Number(value(progress, "percent") || 0);
    document.querySelector("#demoProgressTitle").textContent = value(progress, "batch_name");
    document.querySelector("#demoProgressPercent").textContent = `${percent.toFixed(1)}%`;
    document.querySelector("#demoProgressBar").style.width = `${Math.min(100, percent)}%`;
    document.querySelector("#demoProgressSummary").textContent = `${Number(value(progress, "completed") || 0).toLocaleString()} of ${Number(value(progress, "total_records") || 0).toLocaleString()} PDFs completed`;
    document.querySelector("#demoProgressFacts").innerHTML = detailsMarkup([
      ["Status", value(progress, "batch_status")], ["Pending", Number(value(progress, "pending") || 0).toLocaleString()],
      ["Processing", value(progress, "processing") || 0], ["Failed", value(progress, "failed") || 0],
      ["Speed", `${Number(value(progress, "pdfsPerSecond") || 0).toFixed(2)} PDFs/sec`],
      ["Elapsed", durationLabel(value(progress, "elapsedSeconds"))], ["Estimated remaining", durationLabel(value(progress, "estimatedRemainingSeconds"))],
      ["Workers", value(progress, "worker_count")],
    ]);
    const running = progress.running === true;
    document.querySelector("#generateDemoBatch").disabled = running || Number(value(progress, "pending") || 0) === 0;
    document.querySelector("#resetDemoBatch").disabled = running;
    return running;
  };
  let currentPage = 0;
  const loadCards = async (page = 0) => {
    currentPage = page;
    const params = new URLSearchParams({ batchId: batchSelect.value, page, size: 50 });
    [["tracking", "#demoTracking"], ["roll", "#demoRoll"], ["name", "#demoName"], ["jobCode", "#demoJob"], ["status", "#demoStatus"]]
      .forEach(([key, selector]) => { const filter = document.querySelector(selector).value.trim(); if (filter) params.set(key, filter); });
    const result = await api(`/api/v1/admin/demo-admit-cards?${params}`);
    document.querySelector("#demoCardRows").innerHTML = result.content.length ? result.content.map((card) => `<tr>
      <td><strong>${escapeHtml(value(card, "applicant_name"))}</strong><br><span class="hint">Roll ${escapeHtml(value(card, "roll_number"))}</span></td>
      <td>${escapeHtml(value(card, "tracking_number"))}<br><span class="hint">${escapeHtml(value(card, "cv_number"))}</span></td>
      <td>${escapeHtml(value(card, "job_code"))}<br><span class="hint">${escapeHtml(value(card, "job_title"))}</span></td>
      <td>${escapeHtml(value(card, "exam_type"))}</td><td>${escapeHtml(value(card, "center_name"))}<br><span class="hint">${escapeHtml(value(card, "room_number"))} / seat ${escapeHtml(value(card, "seat_number"))}</span></td>
      <td><span class="badge">${escapeHtml(value(card, "generation_status"))}</span></td>
      <td>${value(card, "generation_ms") ? `${value(card, "generation_ms")} ms<br><span class="hint">${Math.round(Number(value(card, "pdf_size_bytes")) / 1024)} KB</span>` : "—"}</td>
      <td><button class="text-button" data-demo-pdf="${value(card, "demo_card_id")}">${value(card, "generation_status") === "COMPLETED" ? "View PDF" : "Generate PDF"}</button></td>
    </tr>`).join("") : `<tr><td colspan="8" class="table-empty">No records match these filters.</td></tr>`;
    document.querySelectorAll("[data-demo-pdf]").forEach((button) => button.addEventListener("click", () => openDemoPdf(button.dataset.demoPdf)));
    renderPagination(result.page, result.totalPages, loadCards);
  };
  let pollInProgress = false;
  const schedulePoll = () => {
    clearTimeout(timer);
    timer = window.setTimeout(async () => {
      if (pollInProgress || document.hidden) { schedulePoll(); return; }
      pollInProgress = true;
      try {
        const running = await refreshProgress();
        if (running) schedulePoll();
        else await loadCards(currentPage);
      } catch (error) {
        notify(error.message, "error");
      } finally {
        pollInProgress = false;
      }
    }, 2000);
  };
  const openDemoPdf = async (cardId) => {
    try {
      const response = await fetch(`/api/v1/admin/demo-admit-cards/${cardId}/pdf`, { headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok) throw new Error((await response.json().catch(() => null))?.message || "PDF generation failed.");
      const url = URL.createObjectURL(await response.blob()); window.open(url, "_blank", "noopener");
      window.setTimeout(() => URL.revokeObjectURL(url), 60000); await loadCards(currentPage); await refreshProgress();
    } catch (error) { notify(error.message, "error"); }
  };
  batchSelect.addEventListener("change", async () => { clearTimeout(timer); currentPage = 0; const running = await refreshProgress(); await loadCards(); if (running) schedulePoll(); });
  document.querySelector("#refreshDemoProgress").addEventListener("click", refreshProgress);
  document.querySelector("#generateDemoBatch").addEventListener("click", async () => {
    if (!confirm("Generate all pending PDFs for this demo batch?")) return;
    try { await api(`/api/v1/admin/demo-admit-cards/batches/${batchSelect.value}/generate`, { method: "POST" }); notify("Background PDF generation started."); await refreshProgress(); schedulePoll(); }
    catch (error) { notify(error.message, "error"); }
  });
  document.querySelector("#resetDemoBatch").addEventListener("click", async () => {
    if (!confirm("Delete every generated PDF for this demo batch and reset all records to PENDING? The dummy records will be preserved.")) return;
    try {
      clearTimeout(timer);
      const result = await api(`/api/v1/admin/demo-admit-cards/batches/${batchSelect.value}/generated-pdfs`, { method: "DELETE" });
      notify(`${Number(result.resetRecords || 0).toLocaleString()} demo cards reset and ready to generate again.`);
      currentPage = 0; await refreshProgress(); await loadCards();
    } catch (error) { notify(error.message, "error"); }
  });
  document.querySelector("#searchDemoCards").addEventListener("click", () => loadCards());
  document.querySelector("#clearDemoFilters").addEventListener("click", () => { ["#demoTracking", "#demoRoll", "#demoName", "#demoJob", "#demoStatus"].forEach((selector) => { document.querySelector(selector).value = ""; }); loadCards(); });
  const running = await refreshProgress(); await loadCards(); if (running) schedulePoll();
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

function recordText(values) {
  return values
    .filter((item) => item !== null && item !== undefined && item !== "")
    .join(" · ");
}

async function loadCompleteApplicationDetails() {
  const application = await api(`/api/v1/admin/applications/${pathId()}`);
  document.querySelector("#applicationDetailsTitle").textContent =
    value(application, "full_name");
  document.querySelector("#applicationDetailsTracking").textContent =
    value(application, "tracking_number") || "Draft application";

  const cards = [
    ["Addresses", application.addresses || [], (item) => recordText([
      value(item, "address_type"), value(item, "address_line"),
      value(item, "upazila_name"), value(item, "district_name"),
      value(item, "division_name"), value(item, "postcode"),
    ])],
    ["Education", application.educations || [], (item) => recordText([
      value(item, "qualification_name") || `Qualification #${value(item, "qualification_id")}`,
      value(item, "subject_name"), value(item, "institution_name"),
      `${value(item, "result_type") || ""} ${value(item, "result_value") ?? value(item, "result_grade") ?? ""}${value(item, "result_scale") ? `/${value(item, "result_scale")}` : ""}`.trim(),
      value(item, "passing_year"),
    ])],
    ["Experience", application.experiences || [], (item) => recordText([
      value(item, "designation"), value(item, "employer_name"),
      `${formatDate(value(item, "start_date"))} to ${value(item, "end_date") ? formatDate(value(item, "end_date")) : "Present"}`,
    ])],
    ["Training", application.trainings || [], (item) => recordText([
      value(item, "training_title"), value(item, "training_summary"),
      value(item, "duration_months") ? `${value(item, "duration_months")} month(s)` : null,
    ])],
    ["Languages", application.languages || [], (item) => recordText([
      value(item, "language_name"), `Speaking: ${value(item, "speaking") || "—"}`,
      `Writing: ${value(item, "writing") || "—"}`,
      `Listening: ${value(item, "listening") || "—"}`,
      `Reading: ${value(item, "reading") || "—"}`,
    ])],
    ["Extracurricular activities", application.activities || [], (item) => recordText([
      value(item, "activity_name"), value(item, "organization"),
      value(item, "role_name"), value(item, "activity_summary"),
      value(item, "achievement"),
    ])],
    ["References", application.references || [], (item) => recordText([
      value(item, "full_name"), value(item, "organization"),
      value(item, "designation"), value(item, "relationship"),
      value(item, "email"), value(item, "mobile"),
    ])],
    ["Documents", application.documents || [], (item) => recordText([
      value(item, "document_type"), value(item, "original_name"),
      value(item, "media_type"),
      value(item, "size_bytes") ? `${value(item, "size_bytes")} bytes` : null,
      value(item, "validation_status"),
    ])],
  ];

  document.querySelector("#applicationDetails").innerHTML = `
    <article class="card details-grid">
      ${detailsMarkup([
        ["Job", `${value(application, "job_code")} · ${value(application, "job_title")}`],
        ["Designation", value(application, "job_designation")],
        ["Employment type", value(application, "employment_type")],
        ["Job location", value(application, "job_location")],
        ["CV number", value(application, "cv_number")],
        ["Status", value(application, "status")],
        ["Eligibility", value(application, "eligibility_status")],
        ["Submitted at", formatDate(value(application, "submitted_at"), true)],
      ])}
    </article>
    <article class="card"><h2>Personal and contact information</h2>
      <div class="details-grid">${detailsMarkup([
        ["Full name", value(application, "full_name")],
        ["Father's name", value(application, "father_name")],
        ["Mother's name", value(application, "mother_name")],
        ["Date of birth", formatDate(value(application, "date_of_birth"))],
        ["Gender", value(application, "gender")],
        ["Marital status", value(application, "marital_status")],
        ["Nationality", value(application, "nationality")],
        ["NID number", value(application, "nid_number")],
        ["Passport number", value(application, "passport_number")],
        ["Email", value(application, "email")],
        ["Mobile", value(application, "mobile")],
      ])}</div>
    </article>
    <article class="card"><h2>Applicant photo and signature</h2>
      <div class="details-grid">
        <div><h3>Photo</h3><div class="document-preview photo-preview">
          <img id="adminApplicantPhoto" alt="Applicant photo" hidden />
          <span id="adminApplicantPhotoStatus">Loading photo...</span>
        </div></div>
        <div><h3>Signature</h3><div class="document-preview signature-preview">
          <img id="adminApplicantSignature" alt="Applicant signature" hidden />
          <span id="adminApplicantSignatureStatus">Loading signature...</span>
        </div></div>
      </div>
    </article>
    ${cards.map(([title, records, label]) => recordCard(title, records, label)).join("")}
  `;
  await Promise.all([
    loadAdminApplicationImage(
      "#adminApplicantPhoto",
      "#adminApplicantPhotoStatus",
      value(application, "application_id"),
      "PHOTO",
    ),
    loadAdminApplicationImage(
      "#adminApplicantSignature",
      "#adminApplicantSignatureStatus",
      value(application, "application_id"),
      "SIGNATURE",
    ),
  ]);
}

async function loadAdminApplicationImage(
  imageSelector,
  statusSelector,
  applicationId,
  documentType,
) {
  const image = document.querySelector(imageSelector);
  const status = document.querySelector(statusSelector);
  try {
    const response = await fetch(
      `/api/v1/admin/applications/${applicationId}/documents/${documentType}/content`,
      { headers: { Authorization: `Bearer ${token}` }, cache: "no-store" },
    );
    if (!response.ok) throw new Error(`${documentType} is unavailable.`);
    const blob = await response.blob();
    if (!blob.type.startsWith("image/"))
      throw new Error(`${documentType} is not an image.`);
    const objectUrl = URL.createObjectURL(blob);
    image.dataset.objectUrl = objectUrl;
    image.src = objectUrl;
    image.hidden = false;
    status.hidden = true;
  } catch (error) {
    image.hidden = true;
    status.hidden = false;
    status.textContent = error.message;
  }
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

async function initializeExams() {
  const jobs = await api("/api/v1/admin/jobs");
  document.querySelector("#examJob").innerHTML = jobs
    .map((job) => `<option value="${value(job, "job_id")}">${escapeHtml(value(job, "job_code"))} · ${escapeHtml(value(job, "job_title"))}</option>`)
    .join("");
  document.querySelector("#examForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const body = Object.fromEntries(new FormData(form));
    body.jobId = Number(body.jobId);
    ["examStartAt", "examEndAt", "reportingAt"].forEach((field) => {
      body[field] = body[field] ? new Date(body[field]).toISOString() : null;
    });
    try {
      const created = await api("/api/v1/admin/exams", json("POST", body));
      location.assign(`/admin/exams/${value(created, "exam_event_id")}`);
    } catch (error) {
      notify(error.message, "error");
    }
  });
  const exams = await api("/api/v1/admin/exams");
  document.querySelector("#examRows").innerHTML = exams.length
    ? exams.map((exam) => `<tr>
      <td><strong>${escapeHtml(value(exam, "title"))}</strong><br><span class="hint">${escapeHtml(value(exam, "exam_type"))}</span></td>
      <td>${escapeHtml(value(exam, "job_code"))}<br><span class="hint">${escapeHtml(value(exam, "job_title"))}</span></td>
      <td>${formatDate(value(exam, "exam_start_at"), true)}</td>
      <td>${escapeHtml(value(exam, "candidate_count"))}</td><td>${escapeHtml(value(exam, "center_count"))}</td>
      <td><span class="badge">${escapeHtml(value(exam, "status"))}</span></td>
      <td><a class="text-button" href="/admin/exams/${value(exam, "exam_event_id")}">Manage</a></td>
    </tr>`).join("")
    : `<tr><td colspan="7" class="table-empty">No exam events have been created.</td></tr>`;
}

async function initializeExamDetails() {
  const eventId = pathId();
  let exam;
  const refresh = async () => {
    exam = await api(`/api/v1/admin/exams/${eventId}`);
    setExamDetails(exam);
  };
  const call = async (url, options, message) => {
    try {
      await api(url, options);
      notify(message);
      await refresh();
    } catch (error) {
      notify(error.message, "error");
    }
  };
  await refresh();
  const applications = await api(`/api/v1/admin/jobs/${value(exam, "job_id")}/applications?page=0&size=100`);
  const existing = new Set((exam.candidates || []).map((row) => String(value(row, "application_id"))));
  document.querySelector("#candidatePicker").innerHTML = applications.content
    .filter((row) => !existing.has(String(value(row, "application_id"))))
    .map((row) => `<label class="candidate-option"><input type="checkbox" value="${value(row, "application_id")}" />
      <span><strong>${escapeHtml(value(row, "full_name"))}</strong><small>${escapeHtml(value(row, "tracking_number"))} · ${escapeHtml(value(row, "cv_number"))}</small></span></label>`)
    .join("") || `<div class="empty">All available submitted applications are already selected.</div>`;
  document.querySelector("#selectAllCandidates").addEventListener("click", () =>
    document.querySelectorAll("#candidatePicker input").forEach((input) => { input.checked = true; }));
  document.querySelector("#addCandidates").addEventListener("click", () => {
    const applicationIds = [...document.querySelectorAll("#candidatePicker input:checked")].map((input) => Number(input.value));
    if (!applicationIds.length) return notify("Select at least one candidate.", "error");
    call(`/api/v1/admin/exams/${eventId}/candidates`, json("POST", { applicationIds }), "Candidates selected.");
  });
  document.querySelector("#centerForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.currentTarget));
    call(`/api/v1/admin/exams/${eventId}/centers`, json("POST", body), "Center added.");
    event.currentTarget.reset();
  });
  document.querySelector("#roomForm").addEventListener("submit", (event) => {
    event.preventDefault();
    const body = Object.fromEntries(new FormData(event.currentTarget));
    const centerId = body.centerId;
    body.capacity = Number(body.capacity);
    delete body.centerId;
    call(`/api/v1/admin/exams/${eventId}/centers/${centerId}/rooms`, json("POST", body), "Room added.");
    event.currentTarget.reset();
  });
  document.querySelector("#assignRolls").addEventListener("click", () =>
    call(`/api/v1/admin/exams/${eventId}/rolls`, { method: "POST" }, "Six-digit rolls assigned."));
  document.querySelector("#assignSeats").addEventListener("click", () =>
    call(`/api/v1/admin/exams/${eventId}/seat-plan/auto-assign`, { method: "POST" }, "Seat plan assigned."));
  document.querySelector("#generateCards").addEventListener("click", () => {
    if (confirm("Generate and lock all admit cards and the seat plan?"))
      call(`/api/v1/admin/exams/${eventId}/generate`, { method: "POST" }, "Admit cards generated.");
  });
  document.querySelector("#publishCards").addEventListener("click", () => {
    if (confirm("Publish admit cards to applicants and queue notifications?"))
      call(`/api/v1/admin/exams/${eventId}/publish`, { method: "POST" }, "Admit cards published and notifications queued.");
  });
}

function setExamDetails(exam) {
  document.querySelector("#examType").textContent = value(exam, "exam_type");
  document.querySelector("#examTitle").textContent = value(exam, "title");
  document.querySelector("#examJob").textContent = `${value(exam, "job_code")} · ${value(exam, "job_title")}`;
  document.querySelector("#examStatus").textContent = value(exam, "status");
  document.querySelector("#examFacts").innerHTML = detailsMarkup([
    ["Starts", formatDate(value(exam, "exam_start_at"), true)],
    ["Ends", formatDate(value(exam, "exam_end_at"), true)],
    ["Reporting", formatDate(value(exam, "reporting_at"), true)],
    ["Candidates", (exam.candidates || []).length],
  ]);
  const centers = exam.centers || [];
  const grouped = new Map();
  centers.forEach((row) => {
    const id = value(row, "center_id");
    if (!grouped.has(id)) grouped.set(id, { row, rooms: [] });
    if (value(row, "room_id")) grouped.get(id).rooms.push(row);
  });
  document.querySelector("#centerList").innerHTML = [...grouped.values()].map(({ row, rooms }) =>
    `<div class="list-item"><div><h3>${escapeHtml(value(row, "center_code"))} · ${escapeHtml(value(row, "center_name"))}</h3>
    <p>${escapeHtml(value(row, "address"))}</p><small>${rooms.map((room) => `${escapeHtml(value(room, "room_number"))} (${value(room, "assigned_count")}/${value(room, "capacity")})`).join(" · ") || "No rooms yet"}</small></div></div>`).join("")
    || `<div class="empty">No centers added.</div>`;
  document.querySelector("#roomCenter").innerHTML = [...grouped.values()].map(({ row }) =>
    `<option value="${value(row, "center_id")}">${escapeHtml(value(row, "center_code"))} · ${escapeHtml(value(row, "center_name"))}</option>`).join("");
  document.querySelector("#examCandidateRows").innerHTML = (exam.candidates || []).map((candidate) => `<tr>
    <td><strong>${escapeHtml(value(candidate, "full_name"))}</strong><br><span class="hint">${escapeHtml(value(candidate, "cv_number"))}</span></td>
    <td>${escapeHtml(value(candidate, "tracking_number"))}</td><td>${escapeHtml(value(candidate, "roll_number") || "Not assigned")}</td>
    <td>${escapeHtml(value(candidate, "center_name") || "Not assigned")}</td>
    <td>${escapeHtml(value(candidate, "room_number") || "—")} / ${escapeHtml(value(candidate, "seat_number") || "—")}</td>
    <td><select data-result-candidate="${value(candidate, "exam_candidate_id")}">
      ${["PENDING","PASSED","FAILED","ABSENT"].map((status) => `<option ${status === value(candidate, "result_status") ? "selected" : ""}>${status}</option>`).join("")}
    </select></td>
    <td>${value(candidate, "admit_card_generated_at")
      ? `<a class="text-button" href="/admin/admit-cards/${value(candidate, "exam_candidate_id")}">${value(candidate, "admit_card_published_at") ? "Download" : "View generated"}</a>`
      : "Pending"}</td>
  </tr>`).join("") || `<tr><td colspan="7" class="table-empty">No candidates selected.</td></tr>`;
  document.querySelectorAll("[data-result-candidate]").forEach((select) => select.addEventListener("change", async () => {
    try {
      await api(`/api/v1/admin/exams/${value(exam, "exam_event_id")}/candidates/${select.dataset.resultCandidate}/result`,
        json("PATCH", { resultStatus: select.value }));
      notify("Candidate result updated.");
    } catch (error) { notify(error.message, "error"); }
  }));
  const locked = value(exam, "status") !== "DRAFT";
  document.querySelectorAll("#centerForm input,#centerForm button,#roomForm input,#roomForm select,#roomForm button,#addCandidates,#assignRolls,#assignSeats")
    .forEach((element) => { element.disabled = locked; });
  document.querySelector("#generateCards").disabled = locked;
  document.querySelector("#publishCards").disabled = value(exam, "status") !== "GENERATED";
}

async function initializeAdminAdmitCards() {
  const jobSelect = document.querySelector("#admitCardJob");
  const jobs = await api("/api/v1/admin/jobs");
  jobSelect.insertAdjacentHTML(
    "beforeend",
    jobs
      .map(
        (job) =>
          `<option value="${value(job, "job_id")}">${escapeHtml(value(job, "job_code"))} · ${escapeHtml(value(job, "job_title"))}</option>`,
      )
      .join(""),
  );

  const loadCards = async () => {
    const query = jobSelect.value ? `?jobId=${encodeURIComponent(jobSelect.value)}` : "";
    const cards = await api(`/api/v1/admin/admit-cards${query}`);
    document.querySelector("#adminAdmitCardRows").innerHTML = cards.length
      ? cards
          .map(
            (card) => `<tr>
              <td><strong>${escapeHtml(value(card, "full_name"))}</strong><br><span class="hint">${escapeHtml(value(card, "tracking_number"))} · ${escapeHtml(value(card, "cv_number"))}</span></td>
              <td>${escapeHtml(value(card, "job_code"))}<br><span class="hint">${escapeHtml(value(card, "job_title"))}</span></td>
              <td>${escapeHtml(value(card, "title"))}<br><span class="hint">${formatDate(value(card, "exam_start_at"), true)}</span></td>
              <td>${escapeHtml(value(card, "roll_number"))}</td>
              <td>${escapeHtml(value(card, "center_name") || "—")} / ${escapeHtml(value(card, "room_number") || "—")} / ${escapeHtml(value(card, "seat_number") || "—")}</td>
              <td><span class="badge">${value(card, "admit_card_published_at") ? "Published" : "Generated"}</span></td>
              <td><a class="btn btn-secondary" href="/admin/admit-cards/${value(card, "exam_candidate_id")}">View / download</a></td>
            </tr>`,
          )
          .join("")
      : `<tr><td colspan="7" class="table-empty">No generated admit cards found for this job.</td></tr>`;
  };

  jobSelect.addEventListener("change", loadCards);
  await loadCards();
}

function admitCardDetail(label, content) {
  return `<div class="detail-item"><span>${escapeHtml(label)}</span><strong>${escapeHtml(content || "—")}</strong></div>`;
}

async function initializeAdminAdmitCardDetails() {
  const candidateId = pathId();
  const card = await api(`/api/v1/admin/admit-cards/${candidateId}`);
  document.querySelector("#cardExamType").textContent = `${value(card, "exam_type")} examination`;
  document.querySelector("#cardName").textContent = value(card, "full_name");
  document.querySelector("#cardRoll").textContent = value(card, "roll_number");
  document.querySelector("#cardDetails").innerHTML = [
    admitCardDetail("Position", `${value(card, "job_code")} · ${value(card, "job_title")}`),
    admitCardDetail("Exam", value(card, "title")),
    admitCardDetail("Date and time", `${formatDate(value(card, "exam_start_at"), true)} – ${formatDate(value(card, "exam_end_at"), true)}`),
    admitCardDetail("Reporting time", formatDate(value(card, "reporting_at"), true)),
    admitCardDetail("Center", `${value(card, "center_code")} · ${value(card, "center_name")}`),
    admitCardDetail("Center address", value(card, "center_address")),
    admitCardDetail("Room", [value(card, "room_number"), value(card, "floor_name")].filter(Boolean).join(", ")),
    admitCardDetail("Seat number", value(card, "seat_number")),
    admitCardDetail("Tracking number", value(card, "tracking_number")),
    admitCardDetail("CV number", value(card, "cv_number")),
  ].join("");
  const instructions = value(card, "instructions");
  document.querySelector("#cardInstructions").innerHTML = instructions
    ? `<ol>${String(instructions).split(/\r?\n/).filter(Boolean).map((line) => `<li>${escapeHtml(line.replace(/^\d+[.)]\s*/, ""))}</li>`).join("")}</ol>`
    : `<ol><li>Bring a printed copy of this admit card and an original photo identity document.</li>
      <li>Report at the center by the stated reporting time.</li>
      <li>Mobile phones, smart watches, bags and communication devices are prohibited.</li>
      <li>Use the same signature as submitted with your application.</li></ol>`;
  await Promise.all([
    loadAuthenticatedImage(document.querySelector("#cardPhoto"), `/api/v1/admin/admit-cards/${candidateId}/documents/PHOTO`),
    loadAuthenticatedImage(document.querySelector("#cardSignature"), `/api/v1/admin/admit-cards/${candidateId}/documents/SIGNATURE`),
  ]);
  document.querySelector("#printAdminAdmitCard").addEventListener("click", () => window.print());
}

const loaders = {
  dashboard: loadDashboard,
  jobs: loadJobs,
  "job-form": initializeJobForm,
  "job-details": loadJobDetails,
  applications: initializeApplications,
  "application-details": loadCompleteApplicationDetails,
  exams: initializeExams,
  "exam-details": initializeExamDetails,
  "admit-cards": initializeAdminAdmitCards,
  "admit-card-details": initializeAdminAdmitCardDetails,
  "demo-admit-cards": initializeDemoAdmitCards,
  users: loadUsers,
};

loaders[page]?.().catch((error) => notify(error.message, "error"));
