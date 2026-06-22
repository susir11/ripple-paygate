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

    if (!response.ok) throw new Error("Registration failed");

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

    if (!response.ok) throw new Error("Invalid username or password");

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
}

// ─── Logout ──────────────────────────────────────────────────────────────────

document.getElementById("logout-btn").addEventListener("click", () => {
  // Clear the token — user must log in again to get a new one.
  // In remittance terms: like ending a banking session.
  jwtToken = null;
  showCard("login-card");
});

// ─── Payment ─────────────────────────────────────────────────────────────────

document.getElementById("payment-form").addEventListener("submit", async (e) => {
  e.preventDefault();

  const amount = document.getElementById("amount").value;
  const resultBox = document.getElementById("result");

  try {
    const response = await fetch(`${GATEWAY}/api/payments/process`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        // This is the key line — every payment request now carries
        // the JWT token in the Authorization header.
        // The gateway reads this, verifies the signature,
        // and only then forwards the request to payment-service.
        "Authorization": `Bearer ${jwtToken}`
      },
      body: JSON.stringify({ amount: parseFloat(amount) })
    });

    if (!response.ok) throw new Error(`Request failed with status ${response.status}`);

    const data = await response.json();

    resultBox.classList.remove("hidden", "error");
    resultBox.classList.add("success");
    resultBox.innerHTML =
      `<strong>Payment successful</strong><br>
       Transaction ID: ${data.transactionId}<br>
       Amount: ${data.amount}<br>
       Status: ${data.status}`;

  } catch (error) {
    resultBox.classList.remove("hidden", "success");
    resultBox.classList.add("error");
    resultBox.innerHTML = `<strong>Payment failed</strong><br>${error.message}`;
  }
});

// ─── Helper ──────────────────────────────────────────────────────────────────

function showError(elementId, message) {
  const el = document.getElementById(elementId);
  el.textContent = message;
  el.classList.remove("hidden");
}