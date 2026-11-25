# eSewa Sandbox Integration — Design

Date: 2026-11-25
Status: Approved

## Intent

Ripple PayGate is a learning project. Add real (sandbox) payments through eSewa ePay v2
so a logged-in user can pay from an eSewa test wallet, with a small frontend to drive it.
In eSewa's model, Ripple PayGate acts as the **merchant**, using eSewa's shared sandbox
merchant (`EPAYTEST`).

## Goals

- User logs in, enters an amount, clicks "Pay with eSewa", pays on the eSewa sandbox page,
  and lands on a result page showing success or failure.
- Signing happens server-side only; the secret never reaches the browser.
- eSewa's callback is verified (signature + status-check API) before marking a payment SUCCESS.
- Payment records move `PENDING` → `SUCCESS` / `FAILED`.

## Non-goals

- Going live / real merchant account.
- Linking payments to users (would require fixing the `mutate()` result being discarded in
  `JwtAuthFilter`).
- Payment history, refunds, React/SPA frontend.

## Anti-goals

- Putting the eSewa secret or signing logic in the frontend.
- Trusting the success redirect without verification.

## Approaches considered

- **A. eSewa logic in existing payment-service + extend existing vanilla frontend (chosen).**
  Smallest change, reuses current architecture, no new tooling.
- B. Same backend, new React (Vite) frontend. Nicer UI but adds a build step and shifts focus
  away from the payment flow.
- C. Separate `esewa-service` microservice. More microservice practice, but two services
  owning the same payment records — more moving parts than needed.
- Rejected: frontend-only integration (secret exposed in browser).

## Flow

1. Frontend `POST /api/payments/esewa/initiate` `{amount}` with JWT.
2. payment-service saves `Payment(PENDING, transactionId=UUID)`, builds ePay v2 form fields,
   signs `total_amount=<v>,transaction_uuid=<v>,product_code=<v>` with HMAC-SHA256 (base64),
   returns `{ formUrl, fields }`.
3. Frontend builds a hidden form and auto-submits it to the eSewa sandbox form URL.
4. User pays with a sandbox test wallet.
5. eSewa redirects browser to `success_url?data=<base64 JSON>` (through the gateway).
6. payment-service decodes `data`, recomputes signature over `signed_field_names`, checks
   `status == COMPLETE`, confirms via the status-check API, updates only if still `PENDING`,
   stores eSewa `ref_id`, then 302-redirects to `http://localhost:3000/result.html?status=...&txn=...`.
7. On failure, eSewa redirects to `failure_url?txn=<uuid>`; payment marked `FAILED`, redirect
   to result page.

## Changes

### payment-service
- `Payment`: add `refId`; status values `PENDING` / `SUCCESS` / `FAILED`.
- `application.yml`: `esewa.*` block (product-code, secret, form-url, status-url, success-url,
  failure-url, frontend-result-url), env-overridable; sandbox values as defaults.
- `EsewaService`: sign, verify callback, status check (JDK `Mac` + Spring `RestClient`).
- Endpoints: `POST /api/payments/esewa/initiate`, `GET /api/payments/esewa/success`,
  `GET /api/payments/esewa/failure`.
- Unit test for signature generation against eSewa's documented example.

### api-gateway
- `JwtAuthFilter`: skip JWT check for `/api/payments/esewa/success` and `/failure`.

### frontend
- Replace "Process Payment" with "Pay with eSewa" (initiate + auto-submit form).
- Add `result.html` showing outcome; same plain styling.

### docs
- README section "Testing with eSewa sandbox" (test wallet IDs, password, token).

## Sandbox values (verified against developer.esewa.com.np/pages/Epay on 2026-11-25)

- Form URL: `https://rc-epay.esewa.com.np/api/epay/main/v2/form`
- Status URL: `https://rc.esewa.com.np/api/epay/transaction/status/`
- Product code: `EPAYTEST`; secret: `8gBm/:&EnhH.1/q`
- Test wallets: `9711111111`, `9711111112`, `9711111113`, password `Nepal@123`, MPIN `1122`, token `123456`

## Left to the builder

- Exact DTO shapes, class/package names, error-message wording, result page styling.
- Amount formatting, as long as the exact string signed is the string sent.
