const GATEWAY = "http://localhost:8080";

// We store the JWT token in a plain variable.
// It lives only as long as the browser tab is open —
// when you close the tab, it's gone and you must log in again.
// This is intentional — no localStorage, keeping it simple and safe.
let jwtToken = null;

// ─── Screen switching ────────────────────────────────────────────────────────

function showCard(id) {
  document.querySelectorAll(".card").forEach(c => c.classList.add("hidden"));
  document.getElementById(id).classList.remove("hidden");
}

document.getElementById("go-register").addEventListener("click", (e) => {
  e.preventDefault();
  showCard("register-card");
});

document.getElementById("go-login").addEventListener("click", (e) => {
  e.preventDefault();
  showCard("login-card");
});

// ─── Register ────────────────────────────────────────────────────────────────

document.getElementById("register-form").addEventListener("submit", async (e) => {
  e.preventDefault();

  const username = document.getElementById("reg-username").value;
  const password = document.getElementById("reg-password").value;

  try {
    const response = await fetch(`${GATEWAY}/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password })
    });

    if (!response.ok) throw new Error(await errorMessage(response, "Registration failed"));

    // Registration returns a token immediately — log them in right away
    jwtToken = await response.text();
    onLoginSuccess(username);

  } catch (error) {
    showError("register-error", error.message);
  }
});

// ─── Login ───────────────────────────────────────────────────────────────────

document.getElementById("login-form").addEventListener("submit", async (e) => {
  e.preventDefault();

  const username = document.getElementById("username").value;
  const password = document.getElementById("password").value;

  try {
    const response = await fetch(`${GATEWAY}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password })
    });

    if (!response.ok) throw new Error(await errorMessage(response, "Login failed"));

    // Store the token — this is what gets sent with every payment request
    jwtToken = await response.text();
    onLoginSuccess(username);

  } catch (error) {
    showError("login-error", error.message);
  }
});

function onLoginSuccess(username) {
  document.getElementById("welcome-msg").textContent =
    `Logged in as: ${username}`;
  showCard("payment-card");
  loadHistory();
}

// ─── Payment history ─────────────────────────────────────────────────────────

// GET /api/payments/mine returns only THIS user's payments: the gateway reads
// the username from the JWT, so there's no way to ask for someone else's.
async function loadHistory() {
  const tbody = document.querySelector("#history tbody");
  tbody.replaceChildren();
  try {
    const response = await fetch(`${GATEWAY}/api/payments/mine`, {
      headers: { "Authorization": `Bearer ${jwtToken}` }
    });
    if (!response.ok) return;
    const payments = await response.json();

    document.getElementById("history").classList.toggle("hidden", payments.length === 0);
    document.getElementById("history-empty").classList.toggle("hidden", payments.length > 0);

    for (const p of payments) {
      const row = tbody.insertRow();
      // textContent, never innerHTML, so data can't inject HTML into the page
      row.insertCell().textContent = p.createdAt ? new Date(p.createdAt).toLocaleString() : "—";
      row.insertCell().textContent = `Rs. ${Number(p.amount).toFixed(2)}`;
      const status = row.insertCell();
      status.textContent = p.status;
      status.className = `status-${p.status.toLowerCase()}`;
    }
  } catch (e) {
    // History is a nice-to-have; paying still works without it.
  }
}

// ─── Logout ──────────────────────────────────────────────────────────────────

document.getElementById("logout-btn").addEventListener("click", () => {
  // Clear the token — user must log in again to get a new one.
  // In remittance terms: like ending a banking session.
  jwtToken = null;
  showCard("login-card");
});

// ─── Payment ─────────────────────────────────────────────────────────────────

// Paying with eSewa happens in two steps:
//  1. Ask our backend to create a PENDING payment and give us a signed form.
//     (The signature is made on the server — the eSewa secret never reaches the browser.)
//  2. Post that form to eSewa. The browser leaves our site; eSewa sends the user
//     back to the gateway afterwards, which verifies the payment and shows result.html.

document.getElementById("payment-form").addEventListener("submit", async (e) => {
  e.preventDefault();

  const amount = document.getElementById("amount").value;
  const resultBox = document.getElementById("result");
  const payBtn = document.getElementById("pay-btn");

  payBtn.disabled = true;
  payBtn.textContent = "Redirecting to eSewa…";

  try {
    const response = await fetch(`${GATEWAY}/api/payments/esewa/initiate`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        // The gateway checks this JWT before forwarding to payment-service.
        "Authorization": `Bearer ${jwtToken}`
      },
      body: JSON.stringify({ amount: parseFloat(amount) })
    });

    if (!response.ok) throw new Error(`Could not start payment (status ${response.status})`);

    const { formUrl, fields } = await response.json();
    submitToEsewa(formUrl, fields);

  } catch (error) {
    payBtn.disabled = false;
    payBtn.textContent = "Pay with eSewa";
    resultBox.classList.remove("hidden", "success");
    resultBox.classList.add("error");
    resultBox.textContent = `Payment failed: ${error.message}`;
  }
});

// eSewa expects a normal HTML form POST (not fetch/JSON), so we build one
// with a hidden input per field and submit it — the browser navigates to eSewa.
function submitToEsewa(formUrl, fields) {
  const form = document.createElement("form");
  form.method = "POST";
  form.action = formUrl;

  for (const [name, value] of Object.entries(fields)) {
    const input = document.createElement("input");
    input.type = "hidden";
    input.name = name;
    input.value = value;
    form.appendChild(input);
  }

  document.body.appendChild(form);
  form.submit();
}

// ─── Helper ──────────────────────────────────────────────────────────────────

// auth-service sends errors as {"error": "...", "message": "..."} — show its message.
async function errorMessage(response, fallback) {
  try {
    const body = await response.json();
    if (body && body.message) return body.message;
  } catch (e) { /* not JSON */ }
  return fallback;
}

function showError(elementId, message) {
  const el = document.getElementById(elementId);
  el.textContent = message;
  el.classList.remove("hidden");
}