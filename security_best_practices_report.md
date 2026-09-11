# Security Best Practices Report

## Executive summary

Security review of `print-calculator` (Spring Boot Java backend and Angular/TypeScript frontend), focused on authentication/authorization, data exposure, file uploads, hardening, and resilience.

Result: **6 findings** in total.

- **Critical**: 1
- **High**: 3
- **Medium**: 2

Primary risk: public UUID-based APIs without ownership or token checks that allow PII disclosure and business actions on orders.

## Scope and method

- Code reviewed: backend (`backend/src/main/java`, `backend/src/main/resources`), frontend (`frontend/src/app`), and deployment configuration (`deploy/`, `docker-compose*.yml`).
- Skill reference used: `javascript-general-web-frontend-security.md`.
- Note: the skill set has no Java/Spring-specific reference; established Spring and security-engineering practices were applied to the backend.

## Critical findings

### SBP-001 - Broken access control on public order APIs (PII disclosure and state actions)

- **Severity**: Critical
- **Impact**: Anyone who obtains an `orderId` can read personal order data and invoke business operations without authentication.
- **Evidence**:
  - `backend/src/main/java/com/printcalculator/config/SecurityConfig.java:36` (`.anyRequest().permitAll()`).
  - `backend/src/main/java/com/printcalculator/controller/OrderController.java:131` (`GET /api/orders/{orderId}`).
  - `backend/src/main/java/com/printcalculator/controller/OrderController.java:141` (`POST /api/orders/{orderId}/payments/report`).
  - `backend/src/main/java/com/printcalculator/controller/OrderController.java:93` (`POST /api/orders/{orderId}/items/{orderItemId}/file`).
  - `backend/src/main/java/com/printcalculator/controller/OrderController.java:295`-`337` (complete PII in the DTO: email, phone number, billing/shipping addresses).
  - `backend/src/main/java/com/printcalculator/service/PaymentService.java:53`-`75` (cambio stato pagamento a `REPORTED`).
- **Technical risk**:
  - No application-level authentication/authorization on order endpoints.
  - A “capability by UUID” model without a secondary token, expiry, or user binding.
- **Recommended fix**:
  - Introduce a high-entropy random `order_access_token` (>=128 bits), store it hashed, and require it on public order endpoints.
  - Separate public endpoints (minimal data set) from internal/admin endpoints.
  - Remove `orderItemId` and sensitive details from public DTOs, or use expiring signed URLs for upload/download.
  - Consider lightweight customer authentication (magic-link OTP) for viewing or changing an order.

## High findings

### SBP-002 - PII exposure on public custom-quote-request endpoint

- **Severity**: High
- **Evidence**:
  - `backend/src/main/java/com/printcalculator/controller/CustomQuoteRequestController.java:188`-`193` (`GET /api/custom-quote-requests/{id}` senza auth).
  - `backend/src/main/java/com/printcalculator/entity/CustomQuoteRequest.java:24`-`40` (campi PII e messaggio cliente).
  - `backend/src/main/java/com/printcalculator/config/SecurityConfig.java:36` (`.anyRequest().permitAll()`).
- **Technical risk**:
  - A “lookup by UUID” endpoint returns a complete object containing personal data.
- **Recommended fix**:
  - Protect the endpoint with a separate access token per request, not just a UUID.
  - Return a redacted/minimal view from public endpoints.
  - Remove the endpoint if the frontend does not use it.

### SBP-003 - Fail-open antivirus and disabled scanner by default

- **Severity**: High
- **Evidence**:
  - `backend/src/main/resources/application.properties:27` (`clamav.enabled=${CLAMAV_ENABLED:false}`).
  - `backend/src/main/java/com/printcalculator/service/ClamAVService.java:42`-`43` (scanner disabilitato => ritorna `true`).
  - `backend/src/main/java/com/printcalculator/service/ClamAVService.java:54`-`61` (errori scanner => `FAIL-OPEN`).
  - `backend/src/main/java/com/printcalculator/service/FileSystemStorageService.java:59`-`60` (scanner exceptions ignored and file retained).
- **Technical risk**:
  - Malicious files can be accepted when the scanner is down or unconfigured.
- **Recommended fix**:
  - Use a fail-closed policy in non-development environments (`reject on scan error`).
  - Make `CLAMAV_ENABLED=true` the deployment-runtime default and prevent startup if a required scanner is unreachable.
  - Add telemetry and alerts for scan bypasses and failure rates.

### SBP-004 - Expensive endpoints exposed without throttling/rate limiting (application DoS)

- **Severity**: High
- **Evidence**:
  - `backend/src/main/java/com/printcalculator/config/SecurityConfig.java:36` (endpoint pubblici permessi globalmente).
  - `backend/src/main/java/com/printcalculator/controller/QuoteController.java:38`-`39` (`POST /api/quote` pubblico).
  - `backend/src/main/java/com/printcalculator/controller/QuoteSessionController.java:114`-`120` (`POST /api/quote-sessions/{id}/line-items` pubblico).
  - `backend/src/main/java/com/printcalculator/controller/QuoteSessionController.java:228`-`235` (invocazione slicing).
  - `backend/src/main/java/com/printcalculator/service/SlicerService.java:156`-`163` (job fino a 5 minuti).
- **Technical risk**:
  - Mass upload/slicing can saturate CPU, I/O, and worker threads.
- **Recommended fix**:
  - Rate-limit by IP, fingerprint, or session, including at the reverse proxy.
  - Use an asynchronous queue with concurrency limits and tighter timeouts.
  - Apply user/session quotas and a request limit per time window.
  - Add CAPTCHA or proof of work to high-cost anonymous endpoints.

## Medium findings

### SBP-005 - Weak secret/default credentials in configuration code

- **Severity**: Medium
- **Evidence**:
  - `backend/src/main/resources/application.properties:7` (`DB_PASSWORD` fallback `printcalc_secret`).
  - `backend/src/main/resources/application-local.properties:7`-`8` (admin password/secret hard-coded for the local profile).
- **Technical risk**:
  - Predictable values are used if the environment is misconfigured or a profile is used incorrectly.
- **Recommended fix**:
  - Remove sensitive fallbacks and require secrets at startup.
  - Move local credentials to an untracked file (`.env.local`, `.gitignore`) with a placeholder template.
  - Adopt a periodic secret-rotation policy.

### SBP-006 - CSRF disabled globally with cookie-based admin authentication

- **Severity**: Medium
- **Evidence**:
  - `backend/src/main/java/com/printcalculator/config/SecurityConfig.java:24` (CSRF disabilitato globalmente).
  - `backend/src/main/java/com/printcalculator/security/AdminSessionService.java:129`-`136` (cookie sessione admin).
  - `backend/src/main/java/com/printcalculator/security/AdminSessionService.java:133`-`134` (`Secure` + `SameSite=Strict` presenti, mitigazione parziale).
- **Technical risk**:
  - With cookie-based authentication, CSRF protection should be retained on state-changing admin endpoints; `SameSite=Strict` reduces but does not eliminate every vector.
- **Recommended fix**:
  - Re-enable CSRF at least on `/api/admin/**` and use a CSRF token (double-submit or synchronizer token).
  - Retain `SameSite=Strict` as an additional defence.

## Notes and assumptions

- Some public endpoints appear designed for an anonymous customer flow; the finding remains valid because there is no second proof of possession beyond the UUID.
- No external DAST or black-box penetration test was performed; the review used static analysis of code and repository configuration.
