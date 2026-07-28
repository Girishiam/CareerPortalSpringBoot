const token = localStorage.getItem("careerPortalToken");
const page = document.body.dataset.portalPage;
const alertBox = document.querySelector("#portalAlert");
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

if (!token) {
  window.location.replace("/login");
}

function column(row, name) {
  return row?.[name] ?? row?.[name.toUpperCase()];
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function display(value, fallback = "—") {
  return value === null || value === undefined || value === ""
    ? fallback
    : escapeHtml(value);
}

function formatDate(value, includeTime = false) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return display(value);
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    ...(includeTime ? { timeStyle: "short" } : {}),
  }).format(date);
}

function notify(message, type = "success") {
  if (!alertBox) return;
  alertBox.textContent = message;
  alertBox.className = `alert show ${type}`;
  alertBox.scrollIntoView({ behavior: "smooth", block: "center" });
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
  if (response.status === 401) {
    localStorage.removeItem("careerPortalToken");
    localStorage.removeItem("careerPortalRoles");
    window.location.replace("/login");
    throw new Error("Your session has expired.");
  }
  const payload =
    response.status === 204 ? null : await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(payload?.message || "The request could not be completed.");
  }
  return payload;
}

function json(method, body) {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  };
}

function formData(form) {
  const values = Object.fromEntries(new FormData(form));
  form.querySelectorAll('input[type="checkbox"]').forEach((input) => {
    values[input.name] = input.checked;
  });
  return values;
}

function numberFields(values, names) {
  names.forEach((name) => {
    values[name] = values[name] === "" ? null : Number(values[name]);
  });
  return values;
}

function pathId(segment) {
  const parts = window.location.pathname.split("/").filter(Boolean);
  const index = parts.indexOf(segment);
  return index >= 0 ? Number(parts[index + 1]) : 0;
}

function empty(message) {
  return `<div class="empty">${escapeHtml(message)}</div>`;
}

function detail(label, value) {
  return `<div class="detail-item"><span>${escapeHtml(label)}</span><strong>${display(value)}</strong></div>`;
}

function setText(selector, value) {
  const element = document.querySelector(selector);
  if (element) element.textContent = value ?? "";
}

function logout() {
  localStorage.removeItem("careerPortalToken");
  localStorage.removeItem("careerPortalRoles");
  window.location.replace("/login");
}

async function loadHeaderProfile() {
  const profile = await api("/api/v1/me/profile");
  const name = column(profile, "full_name") || "Applicant";
  const cvNumber = column(profile, "cv_number") || "CV number pending";
  document.querySelectorAll("[data-applicant-name]").forEach((element) => {
    element.textContent = name;
  });
  document.querySelectorAll("[data-applicant-cv]").forEach((element) => {
    element.textContent = cvNumber;
  });
  document.querySelectorAll("[data-applicant-avatar]").forEach((element) => {
    element.textContent = name.charAt(0).toUpperCase();
  });
  return profile;
}

function applicationCard(application) {
  const id = column(application, "application_id");
  const tracking =
    column(application, "tracking_number") || "Draft application";
  return `
    <a class="list-item linked-item" href="/portal/applications/${id}">
      <div>
        <h3>${display(column(application, "job_title"))}</h3>
        <p>${display(column(application, "job_code"))} · ${display(tracking)}</p>
      </div>
      <span class="badge">${display(column(application, "status"))}</span>
    </a>`;
}

async function initDashboard(profile) {
  const [educations, experiences, applications] = await Promise.all([
    api("/api/v1/me/educations"),
    api("/api/v1/me/experiences"),
    api("/api/v1/me/applications"),
  ]);
  setText(
    "#welcomeName",
    (column(profile, "full_name") || "Applicant").split(" ")[0],
  );
  setText("#cvNumber", column(profile, "cv_number") || "—");
  setText("#educationCount", educations.length);
  setText("#experienceCount", experiences.length);
  document.querySelector("#applicationList").innerHTML = applications.length
    ? applications.slice(0, 5).map(applicationCard).join("")
    : empty("You have not started an application yet.");
}

async function initPersonal(profile) {
  const form = document.querySelector("#profileForm");
  const fields = {
    fullName: "full_name",
    fatherName: "father_name",
    motherName: "mother_name",
    dateOfBirth: "date_of_birth",
    gender: "gender",
    maritalStatus: "marital_status",
    nationality: "nationality",
    nidNumber: "nid_number",
    email: "email",
    mobile: "mobile",
  };
  Object.entries(fields).forEach(([name, source]) => {
    if (form.elements[name])
      form.elements[name].value = column(profile, source) ?? "";
  });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      await api("/api/v1/me/profile", json("PUT", formData(form)));
      notify("Personal information saved.");
      await loadHeaderProfile();
    } catch (error) {
      notify(error.message, "error");
    }
  });
}

function option(value, label) {
  return `<option value="${escapeHtml(value)}">${escapeHtml(label)}</option>`;
}

async function fillSelect(select, url, placeholder = "Select") {
  const rows = await api(url);
  select.innerHTML =
    option("", placeholder) +
    rows.map((row) => option(column(row, "id"), column(row, "name"))).join("");
}

async function initAddresses() {
  const addresses = await api("/api/v1/me/addresses");
  for (const form of document.querySelectorAll(".address-form")) {
    const division = form.elements.divisionId;
    const district = form.elements.districtId;
    const upazila = form.elements.upazilaId;
    await fillSelect(division, "/api/v1/master-data/divisions");

    division.addEventListener("change", async () => {
      district.innerHTML = option("", "Select");
      upazila.innerHTML = option("", "Select");
      if (division.value) {
        await fillSelect(
          district,
          `/api/v1/master-data/districts?divisionId=${division.value}`,
        );
      }
    });
    district.addEventListener("change", async () => {
      upazila.innerHTML = option("", "Select");
      if (district.value) {
        await fillSelect(
          upazila,
          `/api/v1/master-data/upazilas?districtId=${district.value}`,
        );
      }
    });

    const saved = addresses.find(
      (address) => column(address, "address_type") === form.dataset.type,
    );
    if (saved) {
      form.elements.addressLine.value = column(saved, "address_line") || "";
      form.elements.postcode.value = column(saved, "postcode") || "";
      division.value = column(saved, "division_id");
      await fillSelect(
        district,
        `/api/v1/master-data/districts?divisionId=${division.value}`,
      );
      district.value = column(saved, "district_id");
      await fillSelect(
        upazila,
        `/api/v1/master-data/upazilas?districtId=${district.value}`,
      );
      upazila.value = column(saved, "upazila_id");
    }

    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      try {
        const body = numberFields(formData(form), [
          "divisionId",
          "districtId",
          "upazilaId",
        ]);
        await api(
          `/api/v1/me/addresses/${form.dataset.type}`,
          json("PUT", body),
        );
        notify(`${form.dataset.type.toLowerCase()} address saved.`);
      } catch (error) {
        notify(error.message, "error");
      }
    });
  }
}

async function loadEducation() {
  const rows = await api("/api/v1/me/educations");
  const list = document.querySelector("#educationList");
  list.innerHTML = rows.length
    ? rows
        .map(
          (row) => `
            <div class="list-item">
              <div>
                <h3>Qualification #${display(column(row, "qualification_id"))}</h3>
                <p>
                  ${display(column(row, "result_type"))}
                  ${display(column(row, "result_value") ?? column(row, "result_grade"), "")}
                  · Passed ${display(column(row, "passing_year"))}
                </p>
              </div>
              <button class="btn btn-danger" data-delete-education="${column(row, "education_id")}" type="button">Delete</button>
            </div>`,
        )
        .join("")
    : empty("No education records yet.");
  list.querySelectorAll("[data-delete-education]").forEach((button) => {
    button.addEventListener("click", async () => {
      if (!window.confirm("Delete this education record?")) return;
      try {
        await api(`/api/v1/me/educations/${button.dataset.deleteEducation}`, {
          method: "DELETE",
        });
        notify("Education record deleted.");
        await loadEducation();
      } catch (error) {
        notify(error.message, "error");
      }
    });
  });
}

async function initEducation() {
  const form = document.querySelector("#educationForm");
  await Promise.all([
    fillSelect(
      form.elements.qualificationId,
      "/api/v1/master-data/qualifications",
    ),
    fillSelect(
      form.elements.subjectId,
      "/api/v1/master-data/subjects",
      "Optional",
    ),
    fillSelect(
      form.elements.institutionId,
      "/api/v1/master-data/institutions",
      "Other institution",
    ),
  ]);
  document
    .querySelector("#toggleEducationForm")
    .addEventListener("click", () => {
      form.hidden = !form.hidden;
    });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const body = numberFields(formData(form), [
        "qualificationId",
        "subjectId",
        "institutionId",
        "resultValue",
        "resultScale",
        "passingYear",
      ]);
      await api("/api/v1/me/educations", json("POST", body));
      form.reset();
      form.hidden = true;
      notify("Education record added.");
      await loadEducation();
    } catch (error) {
      notify(error.message, "error");
    }
  });
  await loadEducation();
}

async function loadExperience() {
  const rows = await api("/api/v1/me/experiences");
  const list = document.querySelector("#experienceList");
  list.innerHTML = rows.length
    ? rows
        .map(
          (row) => `
            <div class="list-item">
              <div>
                <h3>${display(column(row, "designation"))}</h3>
                <p>
                  ${display(column(row, "employer_name"))} ·
                  ${formatDate(column(row, "start_date"))} to
                  ${formatDate(column(row, "end_date"), false).replace("—", "Present")}
                </p>
              </div>
              <button class="btn btn-danger" data-delete-experience="${column(row, "experience_id")}" type="button">Delete</button>
            </div>`,
        )
        .join("")
    : empty("No experience records yet.");
  list.querySelectorAll("[data-delete-experience]").forEach((button) => {
    button.addEventListener("click", async () => {
      if (!window.confirm("Delete this experience record?")) return;
      try {
        await api(`/api/v1/me/experiences/${button.dataset.deleteExperience}`, {
          method: "DELETE",
        });
        notify("Experience record deleted.");
        await loadExperience();
      } catch (error) {
        notify(error.message, "error");
      }
    });
  });
}

async function initExperience() {
  const form = document.querySelector("#experienceForm");
  document
    .querySelector("#toggleExperienceForm")
    .addEventListener("click", () => {
      form.hidden = !form.hidden;
    });
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const body = formData(form);
      body.endDate = body.endDate || null;
      await api("/api/v1/me/experiences", json("POST", body));
      form.reset();
      form.hidden = true;
      notify("Experience record added.");
      await loadExperience();
    } catch (error) {
      notify(error.message, "error");
    }
  });
  await loadExperience();
}

async function loadDocuments() {
  const rows = await api("/api/v1/me/documents");
  document.querySelector("#documentList").innerHTML = rows.length
    ? rows
        .map(
          (row) => `
            <div class="list-item">
              <div>
                <h3>${display(column(row, "document_type"))}</h3>
                <p>${display(column(row, "original_name"))} · ${Math.ceil(Number(column(row, "size_bytes") || 0) / 1024)} KB</p>
              </div>
              <span class="badge">${display(column(row, "validation_status"))}</span>
            </div>`,
        )
        .join("")
    : empty("No documents uploaded yet.");
}

async function initDocuments() {
  const form = document.querySelector("#documentForm");
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const result = await api("/api/v1/me/documents", {
        method: "POST",
        body: new FormData(form),
      });
      notify(`${result.documentType} uploaded and validated.`);
      form.reset();
      await loadDocuments();
    } catch (error) {
      notify(error.message, "error");
    }
  });
  await loadDocuments();
}

function jobCard(job) {
  const id = column(job, "job_id");
  return `
    <article class="card job-card">
      <div>
        <span class="badge">${display(column(job, "job_code"))}</span>
        <h3>${display(column(job, "job_title"))}</h3>
        <p class="job-meta">
          ${display(column(job, "employment_type"))} ·
          ${display(column(job, "vacancy_count"))} vacancies ·
          Apply by ${formatDate(column(job, "application_end_at"), true)}
        </p>
      </div>
      <a class="btn btn-primary" href="/portal/jobs/${id}">View position</a>
    </article>`;
}

async function loadJobs() {
  const rows = await api("/api/v1/jobs");
  document.querySelector("#jobList").innerHTML = rows.length
    ? rows.map(jobCard).join("")
    : empty("There are no published positions right now.");
}

async function initJobs() {
  document.querySelector("#refreshJobs").addEventListener("click", loadJobs);
  await loadJobs();
}

async function createDraft(jobId) {
  const draft = await api(`/api/v1/jobs/${jobId}/applications`, {
    method: "POST",
  });
  const panel = document.querySelector("#draftPanel");
  panel.hidden = false;
  if (!draft.canSubmit) {
    panel.innerHTML = `
      <div>
        <span class="page-kicker">Draft saved</span>
        <h2>Complete your profile before submission</h2>
        <p>Missing sections: ${draft.missingSections.map(escapeHtml).join(", ")}.</p>
      </div>
      <a class="btn btn-secondary" href="/portal/profile/personal">Complete profile</a>`;
    notify(
      "Your draft was saved, but the application is not ready to submit.",
      "error",
    );
    return;
  }
  panel.innerHTML = `
    <div>
      <span class="page-kicker">Ready to submit</span>
      <h2>Your application draft is complete</h2>
      <p>Submission freezes a snapshot of your current CV and cannot be undone.</p>
    </div>
    <button class="btn btn-primary" id="confirmSubmission" type="button">Submit application</button>`;
  document
    .querySelector("#confirmSubmission")
    .addEventListener("click", async () => {
      if (!window.confirm("Submit this application now?")) return;
      try {
        const result = await api(
          `/api/v1/me/applications/${draft.applicationId}/submit`,
          { method: "POST" },
        );
        notify(`Application submitted: ${result.trackingNumber}`);
        window.location.assign(`/portal/applications/${draft.applicationId}`);
      } catch (error) {
        notify(error.message, "error");
      }
    });
}

async function initJobDetails() {
  const jobId = pathId("jobs");
  const job = await api(`/api/v1/jobs/${jobId}`);
  setText("#jobCode", column(job, "job_code"));
  setText("#jobTitle", column(job, "job_title"));
  setText(
    "#jobSummary",
    `${column(job, "employment_type")} opportunity · ${column(job, "vacancy_count")} vacancies`,
  );
  document.querySelector("#jobFacts").innerHTML = [
    detail("Employment type", column(job, "employment_type")),
    detail("Vacancies", column(job, "vacancy_count")),
    detail(
      "Applications open",
      formatDate(column(job, "application_start_at"), true),
    ),
    detail(
      "Application deadline",
      formatDate(column(job, "application_end_at"), true),
    ),
    detail("Age reference date", formatDate(column(job, "age_reference_date"))),
    detail("Status", column(job, "status")),
  ].join("");
  setText(
    "#jobDescription",
    column(job, "job_description") || "No description provided.",
  );
  setText(
    "#jobResponsibilities",
    column(job, "responsibilities") ||
      "Responsibilities will be discussed during recruitment.",
  );
  const button = document.querySelector("#applyForJob");
  button.disabled = false;
  button.addEventListener("click", async () => {
    button.disabled = true;
    try {
      await createDraft(jobId);
    } catch (error) {
      notify(error.message, "error");
    } finally {
      button.disabled = false;
    }
  });
}

async function initApplications() {
  const rows = await api("/api/v1/me/applications");
  const table = document.querySelector("#applicationTable");
  table.innerHTML = rows.length
    ? rows
        .map(
          (row) => `
            <tr>
              <td><strong>${display(column(row, "job_title"))}</strong><br><span class="hint">${display(column(row, "job_code"))}</span></td>
              <td>${display(column(row, "tracking_number"), "Draft")}</td>
              <td><span class="badge">${display(column(row, "status"))}</span></td>
              <td>${formatDate(column(row, "submitted_at"), true)}</td>
              <td><a class="text-button" href="/portal/applications/${column(row, "application_id")}">View</a></td>
            </tr>`,
        )
        .join("")
    : `<tr><td class="table-empty" colspan="5">You have not started an application yet.</td></tr>`;
}

function snapshotItem(title, subtitle) {
  return `<div class="list-item"><div><h3>${display(title)}</h3><p>${display(subtitle)}</p></div></div>`;
}

async function initApplicationDetails() {
  const applicationId = pathId("applications");
  const application = await api(`/api/v1/me/applications/${applicationId}`);
  setText("#applicationCode", column(application, "job_code"));
  setText("#applicationTitle", column(application, "job_title"));
  setText(
    "#applicationTracking",
    column(application, "tracking_number") ||
      "Draft application — no tracking number yet",
  );
  document.querySelector("#applicationFacts").innerHTML = [
    detail("Status", column(application, "status")),
    detail("Eligibility", column(application, "eligibility_status")),
    detail("CV number", column(application, "cv_number")),
    detail(
      "Submitted at",
      formatDate(column(application, "submitted_at"), true),
    ),
  ].join("");
  document.querySelector("#profileSnapshot").innerHTML = [
    detail("Full name", column(application, "full_name")),
    detail("Father's name", column(application, "father_name")),
    detail("Mother's name", column(application, "mother_name")),
    detail("Date of birth", formatDate(column(application, "date_of_birth"))),
    detail("Gender", column(application, "gender")),
    detail("NID number", column(application, "nid_number")),
  ].join("");

  const educations = application.educations || [];
  document.querySelector("#snapshotEducation").innerHTML = educations.length
    ? educations
        .map((row) =>
          snapshotItem(
            column(row, "institution_name") ||
              `Qualification #${column(row, "qualification_id")}`,
            `${column(row, "result_type")} ${column(row, "result_value") ?? column(row, "result_grade") ?? ""} · ${column(row, "passing_year")}`,
          ),
        )
        .join("")
    : empty(
        column(application, "status") === "DRAFT"
          ? "Snapshots are created when you submit."
          : "No education snapshot found.",
      );
  const experiences = application.experiences || [];
  document.querySelector("#snapshotExperience").innerHTML = experiences.length
    ? experiences
        .map((row) =>
          snapshotItem(
            column(row, "designation"),
            `${column(row, "employer_name")} · ${formatDate(column(row, "start_date"))} to ${column(row, "end_date") ? formatDate(column(row, "end_date")) : "Present"}`,
          ),
        )
        .join("")
    : empty("No experience snapshot found.");
  const documents = application.documents || [];
  document.querySelector("#snapshotDocuments").innerHTML = documents.length
    ? documents
        .map((row) =>
          snapshotItem(
            column(row, "document_type"),
            `${column(row, "original_name")} · ${column(row, "validation_status")}`,
          ),
        )
        .join("")
    : empty("No document snapshot found.");

  if (column(application, "status") === "DRAFT") {
    const submitButton = document.querySelector("#submitApplication");
    submitButton.hidden = false;
    submitButton.addEventListener("click", async () => {
      if (!window.confirm("Submit this application now?")) return;
      try {
        const result = await api(
          `/api/v1/me/applications/${applicationId}/submit`,
          { method: "POST" },
        );
        notify(`Application submitted: ${result.trackingNumber}`);
        window.location.reload();
      } catch (error) {
        notify(error.message, "error");
      }
    });
  }
}

const initializers = {
  dashboard: initDashboard,
  personal: initPersonal,
  addresses: initAddresses,
  education: initEducation,
  experience: initExperience,
  documents: initDocuments,
  jobs: initJobs,
  "job-details": initJobDetails,
  applications: initApplications,
  "application-details": initApplicationDetails,
};

document.querySelector("#applicantLogout")?.addEventListener("click", logout);

(async () => {
  try {
    const profile = await loadHeaderProfile();
    await initializers[page]?.(profile);
  } catch (error) {
    notify(error.message, "error");
  }
})();
