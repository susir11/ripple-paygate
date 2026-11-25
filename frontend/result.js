// Reads ?status=success|pending|failed&txn=<id> set by payment-service after it
// checked the payment with eSewa. textContent (not innerHTML) so nothing in the
// URL can inject HTML into the page.

const MESSAGES = {
  success: { cls: "success", title: "Payment successful" },
  // eSewa couldn't confirm yet (unreachable or still processing). The payment
  // stays PENDING in our records — it is NOT marked failed.
  pending: { cls: "pending", title: "Payment not confirmed yet — please don't pay again" },
  failed:  { cls: "error",   title: "Payment failed" }
};

const params = new URLSearchParams(window.location.search);
const outcome = MESSAGES[params.get("status")] || MESSAGES.failed;

document.getElementById("result").classList.add(outcome.cls);
document.getElementById("result-title").textContent = outcome.title;
document.getElementById("result-txn").textContent = params.get("txn") || "—";
