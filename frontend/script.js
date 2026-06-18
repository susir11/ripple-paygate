// This is the API Gateway's address, not payment-service directly.
// The browser only ever talks to the gateway — the gateway is the only
// thing allowed to reach payment-service, by design (that's GatewayFilter).
const GATEWAY_URL = "http://localhost:8080/api/payments/process";

const form = document.getElementById("payment-form");
const resultBox = document.getElementById("result");

form.addEventListener("submit", async (event) => {
  // Stop the browser's default "reload the whole page" form behavior,
  // since we want to handle the submission ourselves with JavaScript.
  event.preventDefault();

  const amount = document.getElementById("amount").value;

  try {
    const response = await fetch(GATEWAY_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ amount: parseFloat(amount) })
    });

    if (!response.ok) {
      throw new Error(`Server responded with status ${response.status}`);
    }

    const data = await response.json();
    showResult(data, true);

  } catch (error) {
    showResult({ message: error.message }, false);
  }
});

function showResult(data, success) {
  resultBox.classList.remove("hidden", "success", "error");
  resultBox.classList.add(success ? "success" : "error");

  resultBox.innerHTML = success
    ? `<strong>Payment successful</strong><br>
       Transaction ID: ${data.transactionId}<br>
       Amount: ${data.amount}<br>
       Status: ${data.status}`
    : `<strong>Something went wrong</strong><br>${data.message}`;
}