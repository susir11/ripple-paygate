# eSewa Sandbox Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a logged-in user pay with an eSewa sandbox wallet, with the payment verified server-side and shown on a result page.

**Architecture:** payment-service gains an `EsewaService` (sign, verify callback, status check) and an `EsewaController` (initiate / success / failure). The gateway lets the two eSewa callback paths bypass the JWT check. The vanilla frontend gets a "Pay with eSewa" button that auto-submits a signed form, plus `result.html`.

**Tech Stack:** Java 21, Spring Boot 4.0.1 (Jackson 3 → `tools.jackson.databind`), Spring `RestClient`, JUnit 5 + Mockito + MockMvc (standalone), plain HTML/JS on Nginx.

**Spec:** `docs/superpowers/specs/2026-11-25-esewa-sandbox-design.md`

## Global Constraints

- Work on branch `feature/esewa-sandbox`, never directly on `master`.
- The eSewa secret only ever lives in payment-service config; it must never appear in frontend code or in any HTTP response.
- Sandbox defaults: product code `EPAYTEST`; secret `8gBm/:&EnhH.1/q`; form URL `https://rc-epay.esewa.com.np/api/epay/main/v2/form`; status URL `https://rc.esewa.com.np/api/epay/transaction/status/`.
- Payment status strings are exactly `PENDING`, `SUCCESS`, `FAILED`.
- Signature = Base64(HMAC-SHA256(secret, message)), message = `name=value` pairs joined by `,` in the order given by `signed_field_names`.
- The amount string that is signed must be byte-identical to the `total_amount` field sent.
- A payment is only ever changed while it is `PENDING`.
- Callback URLs (gateway): `http://localhost:8080/api/payments/esewa/success`, `http://localhost:8080/api/payments/esewa/failure`. Result page: `http://localhost:3000/result.html`.
- payment-service Dockerfile copies `target/*.jar`, so run `./mvnw -q package -DskipTests` in a service before `docker compose up --build`.

## Review Focus

1. **eSewa sends `total_amount` with a thousands separator or as a JSON number (`1,000.0` vs `1000.0`)** — signature verification must use the raw text value, and the amount comparison must strip commas. → Task 1 test `verifyCallback_usesRawTextOfNumbers` and Task 3 test `success_amountWithComma_matches`.
2. **The success URL is hit twice (refresh/back button)** — the second hit must not re-verify or change state; it just redirects with the current status. → Task 3 test `success_alreadySuccess_doesNotChange`.
3. **Someone hand-crafts `?data=` with a forged signature** — must not mark SUCCESS. → Task 3 test `success_badSignature_marksFailed`.
4. **Signature valid but the status-check API says not `COMPLETE` (or the call throws)** — mark FAILED, still redirect, no 500. → Task 3 test `success_statusCheckNotComplete_marksFailed`.
5. **Amount ≤ 0 or missing on initiate** — 400, no row saved. → Task 3 test `initiate_rejectsNonPositiveAmount`.

---

### Task 0: Branch and verify eSewa values

- [ ] **Step 1:** `git checkout -b feature/esewa-sandbox`
- [ ] **Step 2:** Open `https://developer.esewa.com.np/pages/Epay` in Chrome (the page is JS-rendered; plain fetch returns "Loading…"). Confirm the form URL, status URL, field names, `signed_field_names` for request and response, the success-redirect format (`?data=<base64 JSON>`), and the documented signature example. If any value differs from Global Constraints or the Task 1 test vector, update this plan and the spec before continuing.

### Task 1: EsewaService — signing and callback verification

**Files:**
- Create: `payment-service/src/main/java/com/susir/paygate/payment/esewa/EsewaProperties.java`
- Create: `payment-service/src/main/java/com/susir/paygate/payment/esewa/EsewaService.java`
- Create: `payment-service/src/main/java/com/susir/paygate/payment/esewa/EsewaCallback.java`
- Modify: `payment-service/src/main/java/com/susir/paygate/payment/PaymentServiceApplication.java` (add `@ConfigurationPropertiesScan`)
- Modify: `payment-service/src/main/resources/application.yml` (add `esewa:` block)
- Test: `payment-service/src/test/java/com/susir/paygate/payment/esewa/EsewaServiceTest.java`

**Interfaces:**
- Produces:
  - `record EsewaProperties(String productCode, String secretKey, String formUrl, String statusUrl, String successUrl, String failureUrl, String resultPageUrl)` — `@ConfigurationProperties(prefix = "esewa")`.
  - `record EsewaCallback(boolean signatureValid, String status, String transactionUuid, String totalAmount, String transactionCode)`
  - `EsewaService(EsewaProperties props, RestClient.Builder restClientBuilder)`
  - `String sign(String message)`
  - `static String formatAmount(Double amount)` → `BigDecimal.valueOf(amount).toPlainString()` (e.g. `100.0` → `"100.0"`)
  - `Map<String,String> buildFormFields(String transactionUuid, Double amount)` — `LinkedHashMap` with keys `amount`, `tax_amount`(`"0"`), `total_amount`, `transaction_uuid`, `product_code`, `product_service_charge`(`"0"`), `product_delivery_charge`(`"0"`), `success_url`, `failure_url`, `signed_field_names`(`"total_amount,transaction_uuid,product_code"`), `signature`. `amount` and `total_amount` both = `formatAmount(amount)`. `failure_url` = `props.failureUrl() + "?txn=" + transactionUuid`.
  - `EsewaCallback parseCallback(String base64Data)` — decode Base64 → JSON tree; rebuild message from `signed_field_names` using each field's raw text value; compare with `signature`. Malformed input → `signatureValid=false`, other fields null (never throws).

`application.yml` block (quote values — the secret contains `:` and `&`):
```yaml
esewa:
  product-code: "${ESEWA_PRODUCT_CODE:EPAYTEST}"
  secret-key: "${ESEWA_SECRET_KEY:8gBm/:&EnhH.1/q}"
  form-url: "${ESEWA_FORM_URL:https://rc-epay.esewa.com.np/api/epay/main/v2/form}"
  status-url: "${ESEWA_STATUS_URL:https://rc.esewa.com.np/api/epay/transaction/status/}"
  success-url: "${ESEWA_SUCCESS_URL:http://localhost:8080/api/payments/esewa/success}"
  failure-url: "${ESEWA_FAILURE_URL:http://localhost:8080/api/payments/esewa/failure}"
  result-page-url: "${ESEWA_RESULT_PAGE_URL:http://localhost:3000/result.html}"
```

- [ ] **Step 1: Write failing tests** (construct `EsewaService` directly with sandbox `EsewaProperties` and `RestClient.builder()`)

```java
@Test void sign_matchesEsewaDocExample() {
    assertEquals("4Ov7pCI1zIOdwtV2BRMUNjz1upIlT/COTxfLhWvVurE=",
        service.sign("total_amount=100,transaction_uuid=11-201-13,product_code=EPAYTEST"));
}
@Test void buildFormFields_signsTotalUuidProductCode() {
    Map<String,String> f = service.buildFormFields("abc-123", 100.0);
    assertEquals("100.0", f.get("total_amount"));
    assertEquals("100.0", f.get("amount"));
    assertEquals("EPAYTEST", f.get("product_code"));
    assertEquals("http://localhost:8080/api/payments/esewa/failure?txn=abc-123", f.get("failure_url"));
    assertEquals(service.sign("total_amount=100.0,transaction_uuid=abc-123,product_code=EPAYTEST"), f.get("signature"));
    assertFalse(f.containsValue("8gBm/:&EnhH.1/q"));
}
@Test void verifyCallback_validSignature() { /* build JSON with string fields, sign via service.sign over
    signed_field_names "transaction_code,status,total_amount,transaction_uuid,product_code,signed_field_names",
    base64 it → parseCallback: signatureValid true, status "COMPLETE", transactionUuid, transactionCode */ }
@Test void verifyCallback_usesRawTextOfNumbers() { /* total_amount as JSON number 1000.0 and, separately,
    as string "1,000.0"; signature computed over "total_amount=1000.0" / "total_amount=1,000.0" → valid */ }
@Test void verifyCallback_tamperedAmount_invalid() { /* sign, then change total_amount → signatureValid false */ }
@Test void verifyCallback_garbage_invalidNoThrow() {
    assertFalse(service.parseCallback("not-base64!!").signatureValid());
}
```

- [ ] **Step 2:** `cd payment-service && ./mvnw -q test -Dtest=EsewaServiceTest` → FAIL (classes missing).
- [ ] **Step 3:** Implement the files above. Signing: `javax.crypto.Mac.getInstance("HmacSHA256")`, UTF-8 bytes, `Base64.getEncoder()`. JSON: inject nothing — create a Jackson 3 `JsonMapper` locally.
- [ ] **Step 4:** Re-run → PASS.
- [ ] **Step 5:** `git add` the files above; `git commit -m "feat(payment): eSewa signing and callback verification"`

### Task 2: EsewaService — status check

**Files:**
- Modify: `payment-service/src/main/java/com/susir/paygate/payment/esewa/EsewaService.java`
- Test: `payment-service/src/test/java/com/susir/paygate/payment/esewa/EsewaServiceStatusTest.java`

**Interfaces:**
- Produces: `String checkStatus(String transactionUuid, String totalAmount)` — GET `statusUrl?product_code=..&total_amount=..&transaction_uuid=..`, returns the response's `status` (e.g. `"COMPLETE"`, `"PENDING"`, `"NOT_FOUND"`); any exception → `"ERROR"`.

- [ ] **Step 1: Failing tests** using `RestClient.Builder` bound to `MockRestServiceServer.bindTo(builder).build()`:
  - `checkStatus_complete`: expect GET to `https://rc.esewa.com.np/api/epay/transaction/status/?product_code=EPAYTEST&total_amount=100.0&transaction_uuid=abc-123`, respond `{"status":"COMPLETE","ref_id":"R1"}` → `"COMPLETE"`.
  - `checkStatus_serverError_returnsError`: respond 500 → `"ERROR"`.
- [ ] **Step 2:** `./mvnw -q test -Dtest=EsewaServiceStatusTest` → FAIL.
- [ ] **Step 3:** Implement `checkStatus`.
- [ ] **Step 4:** Re-run both test classes → PASS.
- [ ] **Step 5:** Commit `feat(payment): eSewa status check`.

### Task 3: Payment entity + EsewaController

**Files:**
- Modify: `payment-service/src/main/java/com/susir/paygate/payment/entity/Payment.java` (add `private String refId;`)
- Modify: `payment-service/src/main/java/com/susir/paygate/payment/repository/PaymentRepository.java` (add `Optional<Payment> findByTransactionId(String transactionId)`)
- Create: `payment-service/src/main/java/com/susir/paygate/payment/esewa/EsewaController.java`
- Create: `payment-service/src/main/java/com/susir/paygate/payment/esewa/EsewaInitiateRequest.java` — `record(Double amount)`
- Create: `payment-service/src/main/java/com/susir/paygate/payment/esewa/EsewaInitiateResponse.java` — `record(String formUrl, Map<String,String> fields)`
- Test: `payment-service/src/test/java/com/susir/paygate/payment/esewa/EsewaControllerTest.java`

**Interfaces:**
- Consumes: Task 1 & 2 `EsewaService`, `EsewaProperties`, `EsewaCallback`.
- Produces (all under `@RequestMapping("/api/payments/esewa")`):
  - `POST /initiate` body `EsewaInitiateRequest` → 200 `EsewaInitiateResponse`; saves `Payment(transactionId=UUID, amount, status=PENDING)`. Amount null or ≤ 0 → 400, nothing saved.
  - `GET /success?data=` → always `302` to `resultPageUrl + "?status=<success|failed>&txn=<uuid>"`.
  - `GET /failure?txn=` → marks `FAILED` if `PENDING`; `302` to `resultPageUrl?status=failed&txn=<txn>`.

Success logic, in order:
1. `parseCallback(data)`; look up payment by `transactionUuid` — not found → redirect `status=failed` (txn = value from callback or empty).
2. Payment not `PENDING` → redirect with its current status (`SUCCESS`→`success`, else `failed`); no other calls.
3. Mark `SUCCESS` (and `refId = transactionCode`) only if: `signatureValid`, `status == "COMPLETE"`, callback `totalAmount` with commas removed equals `formatAmount(payment.amount)` numerically, and `checkStatus(uuid, formatAmount(payment.amount))` is `"COMPLETE"`. Otherwise mark `FAILED`. Save, redirect.

- [ ] **Step 1: Failing tests** — `MockMvcBuilders.standaloneSetup(new EsewaController(...))` with mocked `EsewaService`, `PaymentRepository`, real sandbox `EsewaProperties`:
  - `initiate_savesPendingAndReturnsFields`: amount 100.0 → 200, `$.formUrl` is the sandbox form URL, `$.fields.signature` present; captured saved Payment has status `PENDING` and non-null transactionId.
  - `initiate_rejectsNonPositiveAmount`: amounts `0`, `-5`, missing → 400; `verify(repo, never()).save(any())`.
  - `success_validAndComplete_marksSuccess`: callback valid/COMPLETE/`"100.0"`, checkStatus `COMPLETE` → payment SUCCESS, refId set, redirect `http://localhost:3000/result.html?status=success&txn=abc-123`.
  - `success_amountWithComma_matches`: payment amount 1000.0, callback totalAmount `"1,000.0"` → SUCCESS.
  - `success_badSignature_marksFailed`: signatureValid false → FAILED, redirect `status=failed`, `checkStatus` never called.
  - `success_statusCheckNotComplete_marksFailed`: checkStatus returns `"ERROR"` → FAILED.
  - `success_amountMismatch_marksFailed`: callback `"1.0"`, payment 100.0 → FAILED.
  - `success_alreadySuccess_doesNotChange`: payment already SUCCESS → redirect `status=success`, `parseCallback` result used only for lookup, `save` and `checkStatus` never called.
  - `success_unknownTransaction_redirectsFailed`.
  - `failure_marksPendingFailed` and `failure_doesNotOverwriteSuccess`.
- [ ] **Step 2:** `./mvnw -q test -Dtest=EsewaControllerTest` → FAIL.
- [ ] **Step 3:** Implement. Leave the existing `/api/payments/process` endpoint untouched.
- [ ] **Step 4:** `./mvnw -q test -Dtest='Esewa*'` → PASS.
- [ ] **Step 5:** Commit `feat(payment): eSewa initiate and callback endpoints`.

### Task 4: Gateway — public eSewa callback paths

**Files:**
- Modify: `api-gateway/src/main/java/com/susir/paygate/api_gateway/JwtAuthFilter.java`
- Test: `api-gateway/src/test/java/com/susir/paygate/api_gateway/JwtAuthFilterTest.java`

**Interfaces:**
- Produces: `static boolean isPublicPath(String path)` — true for paths starting with `/auth/`, and exactly `/api/payments/esewa/success` or `/api/payments/esewa/failure`. `filter(...)` uses it in place of the current `/auth/` check.

- [ ] **Step 1: Failing test** `isPublicPath`: true for `/auth/login`, `/api/payments/esewa/success`, `/api/payments/esewa/failure`; false for `/api/payments/esewa/initiate`, `/api/payments/process`, `/api/payments/esewa/success/extra`.
- [ ] **Step 2:** `cd api-gateway && ./mvnw -q test -Dtest=JwtAuthFilterTest` → FAIL.
- [ ] **Step 3:** Implement; update the comment above the check.
- [ ] **Step 4:** Re-run → PASS.
- [ ] **Step 5:** Commit `feat(gateway): allow eSewa callbacks without JWT`.

### Task 5: Frontend, README, end-to-end sandbox run

**Files:**
- Modify: `frontend/index.html` (button text → "Pay with eSewa"; same form id)
- Modify: `frontend/script.js` (payment submit handler)
- Create: `frontend/result.html`, `frontend/result.js`
- Modify: `frontend/style.css` (only if result page needs a class)
- Modify: `README.md` (new section "Testing with eSewa sandbox")

**Interfaces:**
- Consumes: `POST {GATEWAY}/api/payments/esewa/initiate` → `{formUrl, fields}`; result page query `?status=success|failed&txn=<uuid>`.

- [ ] **Step 1:** Payment submit handler: POST `{amount}` with `Authorization: Bearer`; on non-OK show the existing error box; on OK create a `<form method="POST" action=formUrl>` with one hidden `<input>` per field, append to body, `submit()`.
- [ ] **Step 2:** `result.html` + `result.js`: read `status` and `txn` from `URLSearchParams`; show "Payment successful" (success class) or "Payment failed" (error class) and the transaction ID using `textContent` only; a "Back to Ripple PayGate" link to `/` noting the user must log in again.
- [ ] **Step 3:** README section: start-up order (`./mvnw -q package -DskipTests` in each service, then `docker compose up --build`), flow summary, sandbox test wallets `9711111111`, `9711111112`, `9711111113`, password `Nepal@123`, MPIN `1122`, token `123456`.
- [ ] **Step 4: End-to-end:** build and start everything; at `http://localhost:3000` register, pay 100 with eSewa, log in with a test wallet, complete payment. Expected: land on `result.html?status=success&txn=…`; `docker exec postgres-db psql -U <user> -d <db> -c "select transaction_id,amount,status,ref_id from payments order by id desc limit 1;"` shows `SUCCESS` with a ref_id. Repeat and cancel on eSewa → `status=failed`, row `FAILED`.
- [ ] **Step 5:** Commit `feat(frontend): pay with eSewa and result page; docs`.
