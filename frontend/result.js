// Reads ?status=success|failed&txn=<id> set by payment-service after it verified
// the payment with eSewa. textContent (not innerHTML) so nothing in the URL can
// inject HTML into the page.

const params = new URLSearchParams(window.location.search);
const success = params.get("status") === "success";

const box = document.getElementById("result");
box.classList.add(success ? "success" : "error");

document.getElementById("result-title").textContent =
  success ? "Payment successful" : "Payment failed";
document.getElementById("result-txn").textContent = params.get("txn") || "—";
