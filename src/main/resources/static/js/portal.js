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

function dateInputValue(value) {
  if (!value) return "";
  if (Array.isArray(value) && value.length >= 3) {
    const [year, month, day] = value;
    return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
  }
  const match = String(value).match(/^(\d{4}-\d{2}-\d{2})/);
  return match ? match[1] : "";
}

function employmentDuration(startValue, endValue) {
  if (!startValue) return "";
  const start = new Date(`${dateInputValue(startValue)}T00:00:00Z`);
  const end = endValue
    ? new Date(`${dateInputValue(endValue)}T00:00:00Z`)
    : new Date();
  if (
    Number.isNaN(start.getTime()) ||
    Number.isNaN(end.getTime()) ||
    end < start
  )
    return "";

  let months =
    (end.getUTCFullYear() - start.getUTCFullYear()) * 12 +
    end.getUTCMonth() -
    start.getUTCMonth();
  if (end.getUTCDate() < start.getUTCDate()) months -= 1;
  months = Math.max(0, months);

  const years = Math.floor(months / 12);
  const remainingMonths = months % 12;
  const parts = [];
  if (years) parts.push(`${years} ${years === 1 ? "year" : "years"}`);
  if (remainingMonths)
    parts.push(
      `${remainingMonths} ${remainingMonths === 1 ? "month" : "months"}`,
    );
  return parts.length ? parts.join(" ") : "Less than 1 month";
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

async function loadAuthenticatedImage(image, url) {
  const response = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
    cache: "no-store",
  });
  if (response.status === 401) {
    localStorage.removeItem("careerPortalToken");
    localStorage.removeItem("careerPortalRoles");
    window.location.replace("/login");
    throw new Error("Your session has expired.");
  }
  if (!response.ok) throw new Error("The uploaded image could not be loaded.");
  const blob = await response.blob();
  if (!blob.type.startsWith("image/"))
    throw new Error("The stored file is not a valid image.");
  if (image.dataset.objectUrl) URL.revokeObjectURL(image.dataset.objectUrl);
  const objectUrl = URL.createObjectURL(blob);
  image.dataset.objectUrl = objectUrl;
  image.src = objectUrl;
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
  const [cv, applications] = await Promise.all([
    api("/api/v1/me/cv"),
    api("/api/v1/me/applications"),
  ]);
  const educations = cv.educations || [];
  const experiences = cv.experiences || [];
  const cvProfile = cv.profile || profile;
  setText(
    "#welcomeName",
    (column(cvProfile, "full_name") || "Applicant").split(" ")[0],
  );
  setText("#cvNumber", column(cvProfile, "cv_number") || "—");
  setText("#educationCount", educations.length);
  setText("#experienceCount", experiences.length);

  const readiness = document.querySelector("#cvReadiness");
  readiness.className = `cv-status ${cv.complete ? "complete" : "incomplete"}`;
  readiness.innerHTML = cv.complete
    ? `<strong>CV complete</strong><span>You can now apply to open positions.</span>`
    : `<strong>CV incomplete</strong><span>Complete the fields below before applying.</span>`;
  document.querySelector("#missingFieldList").innerHTML = cv.complete
    ? ""
    : cv.missingFields
        .map(
          (item) =>
            `<a class="missing-field" href="${escapeHtml(item.url)}"><span>${escapeHtml(item.label)}</span><small>${escapeHtml(item.section)} · Not filled up</small></a>`,
        )
        .join("");
  const browseButton = document.querySelector("#browseJobsButton");
  if (browseButton) {
    browseButton.classList.toggle("btn-primary", cv.complete);
    browseButton.classList.toggle("btn-secondary", !cv.complete);
    browseButton.textContent = cv.complete
      ? "Browse open positions"
      : "Complete CV to apply";
    browseButton.href = cv.complete
      ? "/portal/jobs"
      : cv.missingFields[0]?.url || "/portal/profile/personal";
  }

  const notFilled = '<span class="not-filled">Not filled up</span>';
  const value = (raw) =>
    raw === null || raw === undefined || raw === "" ? notFilled : display(raw);
  document.querySelector("#cvPersonal").innerHTML = [
    ["Full name", column(cvProfile, "full_name")],
    ["Father's name", column(cvProfile, "father_name")],
    ["Mother's name", column(cvProfile, "mother_name")],
    ["Date of birth", column(cvProfile, "date_of_birth") ? formatDate(column(cvProfile, "date_of_birth")) : null],
    ["Gender", column(cvProfile, "gender")],
    ["Marital status", column(cvProfile, "marital_status")],
    ["Nationality", column(cvProfile, "nationality")],
    ["NID number", column(cvProfile, "nid_number")],
    ["Passport number", column(cvProfile, "passport_number")],
    ["Email", column(cvProfile, "email")],
    ["Mobile", column(cvProfile, "mobile")],
  ]
    .map(
      ([label, raw]) =>
        `<div class="detail-item"><span>${escapeHtml(label)}</span><strong>${value(raw)}</strong></div>`,
    )
    .join("");

  const renderCollection = (selector, rows, renderer) => {
    document.querySelector(selector).innerHTML = rows.length
      ? rows.map(renderer).join("")
      : notFilled;
  };
  renderCollection("#cvAddresses", cv.addresses || [], (row) => `
    <div class="cv-entry"><h4>${display(column(row, "address_type"))} address</h4>
    <p>${value(column(row, "address_line"))}</p>
    <p>${[column(row, "upazila_name"), column(row, "district_name"), column(row, "division_name"), column(row, "postcode")].filter(Boolean).map(escapeHtml).join(", ") || notFilled}</p></div>`);
  renderCollection("#cvEducations", educations, (row) => `
    <div class="cv-entry"><h4>${value(column(row, "qualification_name"))}</h4>
    <p>${value(column(row, "subject_name"))} · ${value(column(row, "institution_display_name"))}</p>
    <p>${value(column(row, "result_type"))}: ${value(column(row, "result_value") ?? column(row, "result_grade"))} · Passing year: ${value(column(row, "passing_year"))}</p></div>`);
  renderCollection("#cvExperiences", experiences, (row) => `
    <div class="cv-entry"><h4>${value(column(row, "designation"))}</h4>
    <p>${value(column(row, "employer_name"))}</p>
    <p>${formatDate(column(row, "start_date"))} – ${column(row, "is_current") ? "Present" : formatDate(column(row, "end_date"))} · ${employmentDuration(column(row, "start_date"), column(row, "end_date"))}</p></div>`);
  renderCollection("#cvTrainings", cv.trainings || [], (row) => `
    <div class="cv-entry"><h4>${value(column(row, "training_title"))}</h4>
    <p>${value(column(row, "training_summary"))} · ${column(row, "duration_months") ? `${column(row, "duration_months")} months` : notFilled}</p></div>`);
  renderCollection("#cvLanguages", cv.languages || [], (row) => `
    <div class="cv-entry"><h4>${value(column(row, "language_name"))}</h4>
    <p>Speaking: ${value(column(row, "speaking"))} · Writing: ${value(column(row, "writing"))} · Listening: ${value(column(row, "listening"))} · Reading: ${value(column(row, "reading"))}</p></div>`);
  renderCollection("#cvActivities", cv.activities || [], (row) => `
    <div class="cv-entry"><h4>${value(column(row, "activity_name"))}</h4>
    <p>${value(column(row, "role_name"))} · ${value(column(row, "organization"))}</p>
    <p>${value(column(row, "activity_summary"))}</p></div>`);
  renderCollection("#cvReferences", cv.references || [], (row) => `
    <div class="cv-entry"><h4>${value(column(row, "full_name"))}</h4>
    <p>${value(column(row, "designation"))} · ${value(column(row, "organization"))}</p>
    <p>${value(column(row, "email") ?? column(row, "mobile"))}</p></div>`);

  const documents = new Map(
    (cv.documents || []).map((row) => [column(row, "document_type"), row]),
  );
  await Promise.all(
    [["PHOTO", "#cvPhoto", "#cvPhotoMissing"], ["SIGNATURE", "#cvSignature", "#cvSignatureMissing"]]
      .map(async ([type, imageSelector, missingSelector]) => {
      const image = document.querySelector(imageSelector);
      const missing = document.querySelector(missingSelector);
      const row = documents.get(type);
      image.hidden = !row;
      missing.hidden = Boolean(row);
      if (row) {
        try {
          await loadAuthenticatedImage(
            image,
            `/api/v1/me/documents/${type}/content?v=${column(row, "file_id")}`,
          );
        } catch (error) {
          image.hidden = true;
          missing.hidden = false;
          missing.textContent = error.message;
        }
      }
    }),
  );
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
    passportNumber: "passport_number",
    email: "email",
    mobile: "mobile",
  };
  Object.entries(fields).forEach(([name, source]) => {
    if (form.elements[name]) {
      const value = column(profile, source);
      form.elements[name].value =
        name === "dateOfBirth" ? dateInputValue(value) : (value ?? "");
    }
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
  const forms = [...document.querySelectorAll(".address-form")];
  for (const form of forms) {
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

  }

  const present = forms.find((form) => form.dataset.type === "PRESENT");
  const permanent = forms.find((form) => form.dataset.type === "PERMANENT");
  const sameAsPresent = document.querySelector("#sameAsPresent");
  const saveButton = document.querySelector("#saveAddresses");

  const saveAddress = async (form) => {
    const body = numberFields(formData(form), [
      "divisionId",
      "districtId",
      "upazilaId",
    ]);
    await api(
      `/api/v1/me/addresses/${form.dataset.type}`,
      json("PUT", body),
    );
  };

  if (!present || !permanent || !sameAsPresent || !saveButton) {
    for (const form of forms) {
      form.addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
          await saveAddress(form);
          notify(`${form.dataset.type.toLowerCase()} address saved.`);
        } catch (error) {
          notify(error.message, "error");
        }
      });
    }
    return;
  }

  const copyPresentAddress = async () => {
    permanent.elements.addressLine.value = present.elements.addressLine.value;
    permanent.elements.postcode.value = present.elements.postcode.value;
    permanent.elements.divisionId.value = present.elements.divisionId.value;
    permanent.elements.districtId.innerHTML = option("", "Select");
    permanent.elements.upazilaId.innerHTML = option("", "Select");

    if (present.elements.divisionId.value) {
      await fillSelect(
        permanent.elements.districtId,
        `/api/v1/master-data/districts?divisionId=${present.elements.divisionId.value}`,
      );
      permanent.elements.districtId.value = present.elements.districtId.value;
    }
    if (present.elements.districtId.value) {
      await fillSelect(
        permanent.elements.upazilaId,
        `/api/v1/master-data/upazilas?districtId=${present.elements.districtId.value}`,
      );
      permanent.elements.upazilaId.value = present.elements.upazilaId.value;
    }
  };

  sameAsPresent.addEventListener("change", async () => {
    if (sameAsPresent.checked) await copyPresentAddress();
  });

  forms.forEach((form) =>
    form.addEventListener("submit", (event) => event.preventDefault()),
  );

  saveButton.addEventListener("click", async () => {
    try {
      if (sameAsPresent.checked) await copyPresentAddress();
      if (!present.reportValidity() || !permanent.reportValidity()) return;

      for (const form of forms) {
        await saveAddress(form);
      }
      notify("Present and permanent addresses saved.");
    } catch (error) {
      notify(error.message, "error");
    }
  });
}

async function loadEducation(onEdit) {
  const rows = await api("/api/v1/me/educations");
  const list = document.querySelector("#educationList");
  list.innerHTML = rows.length
    ? rows
        .map(
          (row) => `
            <div class="list-item">
              <div>
                <h3>${display(column(row, "qualification_name"))}</h3>
                <p>${display(column(row, "subject_name"), "No subject / group")} · ${display(column(row, "institution_display_name"), "No university / board")}</p>
                <p>
                  ${display(column(row, "result_type"))}
                  ${display(column(row, "result_value") ?? column(row, "result_grade"), "")}
                  · Passed ${display(column(row, "passing_year"))}
                </p>
              </div>
              <div class="button-row">
                <button class="btn btn-secondary" data-edit-education="${column(row, "education_id")}" type="button">Edit</button>
                <button class="btn btn-danger" data-delete-education="${column(row, "education_id")}" type="button">Delete</button>
              </div>
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
        await loadEducation(onEdit);
      } catch (error) {
        notify(error.message, "error");
      }
    });
  });
  list.querySelectorAll("[data-edit-education]").forEach((button) => {
    button.addEventListener("click", () => {
      const row = rows.find(
        (item) =>
          String(column(item, "education_id")) === button.dataset.editEducation,
      );
      if (row) onEdit(row);
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
      "Select subject / group",
    ),
    fillSelect(
      form.elements.institutionId,
      "/api/v1/master-data/institutions",
      "Select university / board",
    ),
  ]);
  const qualification = form.elements.qualificationId;
  const subject = form.elements.subjectId;
  const loadSubjects = async () => {
    const url = qualification.value
      ? `/api/v1/master-data/subjects?qualificationId=${qualification.value}`
      : "/api/v1/master-data/subjects";
    await fillSelect(subject, url, "Select subject / group");
  };
  qualification.addEventListener("change", loadSubjects);
  if (qualification.value) await loadSubjects();
  const saveButton = document.querySelector("#saveEducation");
  const cancelButton = document.querySelector("#cancelEducationEdit");
  const resetEducationForm = () => {
    form.reset();
    delete form.dataset.educationId;
    saveButton.textContent = "Save education";
    cancelButton.hidden = true;
    subject.innerHTML = option("", "Select subject / group");
  };
  const editEducation = async (row) => {
    form.hidden = false;
    form.dataset.educationId = column(row, "education_id");
    qualification.value = column(row, "qualification_id") ?? "";
    await loadSubjects();
    subject.value = column(row, "subject_id") ?? "";
    form.elements.institutionId.value = column(row, "institution_id") ?? "";
    form.elements.institutionName.value =
      column(row, "institution_name") ?? "";
    form.elements.resultType.value = column(row, "result_type") ?? "CGPA";
    form.elements.resultValue.value = column(row, "result_value") ?? "";
    form.elements.resultScale.value = column(row, "result_scale") ?? "";
    form.elements.resultGrade.value = column(row, "result_grade") ?? "";
    form.elements.passingYear.value = column(row, "passing_year") ?? "";
    form.elements.isHighestDegree.checked = Boolean(
      column(row, "is_highest_degree"),
    );
    saveButton.textContent = "Update education";
    cancelButton.hidden = false;
    form.scrollIntoView({ behavior: "smooth", block: "start" });
  };
  cancelButton.addEventListener("click", () => {
    resetEducationForm();
    form.hidden = true;
  });
  document
    .querySelector("#toggleEducationForm")
    .addEventListener("click", () => {
      if (!form.hidden && form.dataset.educationId) resetEducationForm();
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
      const educationId = form.dataset.educationId;
      await api(
        educationId
          ? `/api/v1/me/educations/${educationId}`
          : "/api/v1/me/educations",
        json(educationId ? "PUT" : "POST", body),
      );
      resetEducationForm();
      form.hidden = true;
      notify(
        educationId ? "Education record updated." : "Education record added.",
      );
      await loadEducation(editEducation);
    } catch (error) {
      notify(error.message, "error");
    }
  });
  await loadEducation(editEducation);
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
                <p>${display(employmentDuration(column(row, "start_date"), column(row, "end_date")), "")}</p>
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
  const current = form.elements.isCurrent;
  const endDate = form.elements.endDate;
  const syncCurrentEmployment = () => {
    if (current.checked) endDate.value = "";
    endDate.disabled = current.checked;
  };
  current.addEventListener("change", syncCurrentEmployment);
  syncCurrentEmployment();
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
      syncCurrentEmployment();
      form.hidden = true;
      notify("Experience record added.");
      await loadExperience();
    } catch (error) {
      notify(error.message, "error");
    }
  });
  await loadExperience();
}

async function additionalCrud(config) {
  const form = document.querySelector(config.form);
  const list = document.querySelector(config.list);
  const submitButton = form?.querySelector(
    'button[type="submit"], button:not([type])',
  );
  if (!form || !list || !submitButton) return;
  let rows = [];

  const load = async () => {
    rows = await api(config.url);
    list.innerHTML = rows.length
      ? rows
          .map(
            (row) => `
              <div class="list-item">
                <div>
                  <h3>${display(column(row, config.title))}</h3>
                  <p>${config.summary(row)}</p>
                </div>
                <div class="button-row">
                  <button class="btn btn-secondary" data-edit="${column(row, config.id)}" type="button">Edit</button>
                  <button class="btn btn-danger" data-delete="${column(row, config.id)}" type="button">Delete</button>
                </div>
              </div>`,
          )
          .join("")
      : empty(config.empty);

    list.querySelectorAll("[data-edit]").forEach((button) => {
      button.addEventListener("click", () => {
        const row = rows.find(
          (item) => String(column(item, config.id)) === button.dataset.edit,
        );
        if (!row) return;
        config.fields.forEach(([field, source]) => {
          form.elements[field].value = column(row, source) ?? "";
        });
        form.dataset.recordId = button.dataset.edit;
        submitButton.textContent = config.updateLabel;
        form.scrollIntoView({ behavior: "smooth", block: "start" });
      });
    });
    list.querySelectorAll("[data-delete]").forEach((button) => {
      button.addEventListener("click", async () => {
        if (!window.confirm("Delete this record?")) return;
        try {
          await api(`${config.url}/${button.dataset.delete}`, {
            method: "DELETE",
          });
          notify("Record deleted.");
          await load();
        } catch (error) {
          notify(error.message, "error");
        }
      });
    });
  };

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
      const id = form.dataset.recordId;
      const body = formData(form);
      config.numbers?.forEach((field) => {
        body[field] = body[field] === "" ? null : Number(body[field]);
      });
      await api(id ? `${config.url}/${id}` : config.url, json(id ? "PUT" : "POST", body));
      form.reset();
      delete form.dataset.recordId;
      submitButton.textContent = config.createLabel;
      notify(id ? "Record updated." : "Record added.");
      await load();
    } catch (error) {
      notify(error.message, "error");
    }
  });
  await load();
}

async function initAdditional() {
  const proficiencyOptions =
    option("", "Select level") +
    option("LOW", "Low") +
    option("MEDIUM", "Medium") +
    option("HIGH", "High");
  ["speaking", "writing", "listening", "reading"].forEach((name) => {
    document.querySelector("#languageForm").elements[name].innerHTML =
      proficiencyOptions;
  });

  await Promise.all([
    additionalCrud({
      form: "#trainingForm",
      list: "#trainingList",
      url: "/api/v1/me/trainings",
      id: "training_id",
      title: "training_title",
      fields: [
        ["trainingTitle", "training_title"],
        ["trainingSummary", "training_summary"],
        ["durationMonths", "duration_months"],
      ],
      numbers: ["durationMonths"],
      summary: (row) =>
        [
          column(row, "duration_months")
            ? `${column(row, "duration_months")} months`
            : null,
          column(row, "training_summary"),
        ]
          .filter(Boolean)
          .join(" · ") || "No additional details",
      empty: "No professional training added.",
      createLabel: "Save training",
      updateLabel: "Update training",
    }),
    additionalCrud({
      form: "#languageForm",
      list: "#languageList",
      url: "/api/v1/me/languages",
      id: "language_id",
      title: "language_name",
      fields: [
        ["languageName", "language_name"],
        ["speaking", "speaking"],
        ["writing", "writing"],
        ["listening", "listening"],
        ["reading", "reading"],
      ],
      summary: (row) =>
        [
          ["Speaking", column(row, "speaking")],
          ["Writing", column(row, "writing")],
          ["Listening", column(row, "listening")],
          ["Reading", column(row, "reading")],
        ]
          .filter(([, value]) => value)
          .map(([label, value]) => `${label}: ${value}`)
          .join(" · ") || "No proficiency levels selected",
      empty: "No language proficiency added.",
      createLabel: "Save language",
      updateLabel: "Update language",
    }),
    additionalCrud({
      form: "#activityForm",
      list: "#activityList",
      url: "/api/v1/me/activities",
      id: "activity_id",
      title: "activity_name",
      fields: [
        ["activityName", "activity_name"],
        ["organization", "organization"],
        ["roleName", "role_name"],
        ["activitySummary", "activity_summary"],
        ["achievement", "achievement"],
      ],
      summary: (row) =>
        [
          column(row, "role_name"),
          column(row, "organization"),
          column(row, "achievement"),
          column(row, "activity_summary"),
        ]
          .filter(Boolean)
          .join(" · ") || "No additional details",
      empty: "No extra-curricular activities added.",
      createLabel: "Save activity",
      updateLabel: "Update activity",
    }),
    additionalCrud({
      form: "#referenceForm",
      list: "#referenceList",
      url: "/api/v1/me/references",
      id: "reference_id",
      title: "full_name",
      fields: [
        ["fullName", "full_name"],
        ["organization", "organization"],
        ["designation", "designation"],
        ["relationship", "relationship"],
        ["email", "email"],
        ["mobile", "mobile"],
      ],
      summary: (row) =>
        `${display(column(row, "designation"))}, ${display(column(row, "organization"))} · ${display(column(row, "email") ?? column(row, "mobile"))}`,
      empty: "No references added.",
      createLabel: "Save reference",
      updateLabel: "Update reference",
    }),
  ]);
}

async function loadDocuments() {
  const rows = await api("/api/v1/me/documents");
  const activeTypes = new Map(
    rows.map((row) => [column(row, "document_type"), row]),
  );
  await Promise.all([
    ["PHOTO", "#photoPreview", "#photoPreviewEmpty"],
    ["SIGNATURE", "#signaturePreview", "#signaturePreviewEmpty"],
  ].map(async ([type, imageSelector, emptySelector]) => {
    const image = document.querySelector(imageSelector);
    const emptyState = document.querySelector(emptySelector);
    if (!image || !emptyState) return;
    const row = activeTypes.get(type);
    image.hidden = !row;
    emptyState.hidden = Boolean(row);
    if (row) {
      try {
        await loadAuthenticatedImage(
          image,
          `/api/v1/me/documents/${type}/content?v=${column(row, "file_id")}`,
        );
      } catch (error) {
        image.hidden = true;
        emptyState.hidden = false;
        emptyState.textContent = error.message;
      }
    } else {
      if (image.dataset.objectUrl) {
        URL.revokeObjectURL(image.dataset.objectUrl);
        delete image.dataset.objectUrl;
      }
      image.removeAttribute("src");
    }
  }));
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
  document.querySelectorAll("[data-document-form]").forEach((form) => {
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      try {
        const result = await api("/api/v1/me/documents", {
          method: "POST",
          body: new FormData(form),
        });
        notify(`${result.documentType} saved successfully.`);
        form.reset();
        await loadDocuments();
      } catch (error) {
        notify(error.message, "error");
      }
    });
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
          ${display(column(job, "designation"))} ·
          ${display(column(job, "employment_type"))} ·
          ${display(column(job, "vacancy_count"))} vacancies ·
          Apply by ${formatDate(column(job, "application_end_at"), true)}
        </p>
        <p class="hint">${display(column(job, "job_location"), "Location not specified")} · ${display(column(job, "salary_details"), "Salary not specified")}</p>
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
    detail("Designation", column(job, "designation")),
    detail("Employment type", column(job, "employment_type")),
    detail("Vacancies", column(job, "vacancy_count")),
    detail("Experience type", column(job, "experience_type")),
    detail("Job location", column(job, "job_location")),
    detail("Salary", column(job, "salary_details")),
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
    "#jobContext",
    column(job, "job_context") || "No additional job context provided.",
  );
  setText(
    "#jobDescription",
    column(job, "job_description") || "No description provided.",
  );
  setText(
    "#jobResponsibilities",
    column(job, "responsibilities") ||
      "Responsibilities will be discussed during recruitment.",
  );
  setText(
    "#jobAdditionalRequirements",
    column(job, "additional_requirements") ||
      "No additional requirements provided.",
  );
  setText(
    "#jobBenefits",
    column(job, "compensation_benefits") ||
      "Compensation and benefits will be communicated during recruitment.",
  );
  const circularPanel = document.querySelector("#jobCircularActions");
  if (column(job, "circular_pdf_available")) {
    circularPanel.hidden = false;
    setText("#jobCircularName", column(job, "circular_letter_name"));
    circularPanel.querySelectorAll("button").forEach((button) => {
      button.addEventListener("click", () =>
        openJobCircular(jobId, button.dataset.download === "true"),
      );
    });
  }
  const button = document.querySelector("#applyForJob");
  const cv = await api("/api/v1/me/cv");
  button.disabled = false;
  if (!cv.complete) {
    button.textContent = "Complete CV to apply";
    button.classList.remove("btn-primary");
    button.classList.add("btn-secondary");
  }
  button.addEventListener("click", async () => {
    if (!cv.complete) {
      notify(
        `Complete your CV first. Missing: ${cv.missingFields.map((field) => field.label).join(", ")}`,
        "error",
      );
      document.querySelector("#draftPanel").hidden = false;
      document.querySelector("#draftPanel").innerHTML =
        `<h2>CV is incomplete</h2><p>Complete all required information before applying.</p><a class="btn btn-primary" href="/portal/dashboard">Review your CV</a>`;
      return;
    }
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

async function initAdmitCards() {
  const cards = await api("/api/v1/me/admit-cards");
  document.querySelector("#admitCardList").innerHTML = cards.length
    ? cards.map((card) => `
      <article class="card job-card">
        <div><span class="badge">${escapeHtml(column(card, "exam_type"))}</span>
          <h2>${escapeHtml(column(card, "title"))}</h2>
          <p><strong>${escapeHtml(column(card, "job_code"))} · ${escapeHtml(column(card, "job_title"))}</strong></p>
          <p class="job-meta">Roll ${escapeHtml(column(card, "roll_number"))} · ${formatDate(column(card, "exam_start_at"), true)}</p>
          <p class="hint">${escapeHtml(column(card, "center_name"))} · Room ${escapeHtml(column(card, "room_number"))}</p>
        </div><a class="btn btn-primary" href="/portal/admit-cards/${column(card, "exam_candidate_id")}">View admit card</a>
      </article>`).join("")
    : empty("No admit card has been published for you yet.");
}

async function initAdmitCardDetails() {
  const candidateId = pathId("admit-cards");
  const card = await api(`/api/v1/me/admit-cards/${candidateId}`);
  setText("#cardExamType", `${column(card, "exam_type")} examination`);
  setText("#cardName", column(card, "full_name"));
  setText("#cardRoll", column(card, "roll_number"));
  document.querySelector("#cardDetails").innerHTML = [
    detail("Position", `${column(card, "job_code")} · ${column(card, "job_title")}`),
    detail("Exam", column(card, "title")),
    detail("Date and time", `${formatDate(column(card, "exam_start_at"), true)} – ${formatDate(column(card, "exam_end_at"), true)}`),
    detail("Reporting time", formatDate(column(card, "reporting_at"), true)),
    detail("Center", `${column(card, "center_code")} · ${column(card, "center_name")}`),
    detail("Center address", column(card, "center_address")),
    detail("Room", [column(card, "room_number"), column(card, "floor_name")].filter(Boolean).join(", ")),
    detail("Seat number", column(card, "seat_number")),
    detail("Tracking number", column(card, "tracking_number")),
    detail("CV number", column(card, "cv_number")),
  ].join("");
  const instructions = column(card, "instructions");
  document.querySelector("#cardInstructions").innerHTML = instructions
    ? `<ol>${String(instructions).split(/\r?\n/).filter(Boolean).map((line) => `<li>${escapeHtml(line.replace(/^\d+[.)]\s*/, ""))}</li>`).join("")}</ol>`
    : `<ol><li>Bring a printed copy of this admit card and an original photo identity document.</li>
      <li>Report at the center by the stated reporting time.</li>
      <li>Mobile phones, smart watches, bags and communication devices are prohibited.</li>
      <li>Use the same signature as submitted with your application.</li></ol>`;
  await Promise.all([
    loadAuthenticatedImage(document.querySelector("#cardPhoto"), `/api/v1/me/admit-cards/${candidateId}/documents/PHOTO`),
    loadAuthenticatedImage(document.querySelector("#cardSignature"), `/api/v1/me/admit-cards/${candidateId}/documents/SIGNATURE`),
  ]);
  document.querySelector("#printAdmitCard").addEventListener("click", () => window.print());
}

const initializers = {
  dashboard: initDashboard,
  personal: initPersonal,
  addresses: initAddresses,
  education: initEducation,
  experience: initExperience,
  additional: initAdditional,
  documents: initDocuments,
  jobs: initJobs,
  "job-details": initJobDetails,
  applications: initApplications,
  "application-details": initApplicationDetails,
  "admit-cards": initAdmitCards,
  "admit-card-details": initAdmitCardDetails,
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
