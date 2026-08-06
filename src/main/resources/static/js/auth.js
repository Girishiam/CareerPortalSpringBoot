const alertBox = document.querySelector("#alert");

function beginSubmission(form) {
  if (form.dataset.submitting === "true") return false;
  form.dataset.submitting = "true";
  return true;
}

function endSubmission(form) {
  delete form.dataset.submitting;
}
function show(message, type = "error") {
  alertBox.textContent = message;
  alertBox.className = `alert show ${type}`;
}
async function request(url, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok)
    throw new Error(data.message || "Request could not be completed.");
  return data;
}
document
  .querySelector("#loginForm")
  ?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    if (!beginSubmission(form)) return;
    const button = form.querySelector("button");
    button.disabled = true;
    try {
      const data = await request(
        form.dataset.loginEndpoint || "/api/v1/auth/applicants/login",
        Object.fromEntries(new FormData(form)),
      );
      localStorage.setItem("careerPortalToken", data.accessToken);
      localStorage.setItem(
        "careerPortalRoles",
        JSON.stringify(data.roles || []),
      );
      location.href = data.destination || "/portal";
    } catch (error) {
      show(error.message);
    } finally {
      button.disabled = false;
      endSubmission(form);
    }
  });
document
  .querySelector("#registerForm")
  ?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const password = form.elements.password;
    const confirmation = form.elements.confirmPassword;
    confirmation.setCustomValidity(
      password.value === confirmation.value
        ? ""
        : "Password and confirm password must match.",
    );
    if (!form.reportValidity()) return;
    if (!beginSubmission(form)) return;
    const button = form.querySelector("button");
    button.disabled = true;
    try {
      const data = await request(
        "/api/v1/auth/applicants/register",
        Object.fromEntries(new FormData(form)),
      );
      show(
        `Account created. Your CV number is ${data.cvNumber}. Please sign in to continue.`,
        "success",
      );
      form.reset();
      setTimeout(() => (location.href = "/login"), 1800);
    } catch (error) {
      show(error.message);
    } finally {
      button.disabled = false;
      endSubmission(form);
    }
  });

const registrationForm = document.querySelector("#registerForm");
if (registrationForm) {
  const password = registrationForm.elements.password;
  const confirmation = registrationForm.elements.confirmPassword;
  const validatePasswordConfirmation = () =>
    confirmation.setCustomValidity(
      !confirmation.value || password.value === confirmation.value
        ? ""
        : "Password and confirm password must match.",
    );
  password.addEventListener("input", validatePasswordConfirmation);
  confirmation.addEventListener("input", validatePasswordConfirmation);
}
