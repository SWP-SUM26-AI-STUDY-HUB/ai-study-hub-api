# Business Rule — Business Rules

This document defines the **rules, policies, and invariants** of the AI Study Hub system — that is, "constraints on **what** must be true." The `business_process.md` document describes the **sequence/order** of execution ("**how it is performed**"), while this document focuses on the **thresholds, limits, valid states, and business conditions** that every flow must follow.

All values (enums, limits, thresholds) in this document are consistent with the **Canonical Contract**. Each rule has a stable code `BR-<AREA>-NN` for cross-referencing from other documents (Functional Requirements, User Stories, Acceptance Criteria).

**Table convention:**

| Rule code | Description | Value / Condition |
|---|---|---|

---

## 1. Authentication & session rules (BR-AUTH-NN)

| Rule code | Description | Value / Condition |
|---|---|---|
| BR-AUTH-01 | User passwords must be stored hashed, never in plaintext. | **bcrypt** algorithm (integrated with Spring Security). |
| BR-AUTH-02 | The account activation OTP has a finite lifetime, stored in Redis. | TTL = **300 s (5 minutes)**; Redis key `otp:<email>`. |
| BR-AUTH-03 | The password reset token has its own lifetime, separate from the registration OTP. | TTL = **900 s (15 minutes)**; Redis key `otp:reset:<uuid>` (same `otp:` prefix). |
| BR-AUTH-04 | Login sessions use a JWT access + refresh pair, with separate short/long lifetimes. | Access = **1 hour** (3 600 000 ms); Refresh = **7 days** (604 800 000 ms). |
| BR-AUTH-05 | The refresh token must be **rotated** on every use. | Each `POST /auth/refresh` returns a new refresh token; the old refresh token is no longer valid. |
| BR-AUTH-06 | On logout, the current access token must be invalidated immediately (stateless + blacklist). | Token added to the **Redis blacklist**; TTL = **remaining time** until the access token expires naturally. |
| BR-AUTH-07 | A `banned` account must not be able to log in; active sessions must be revoked. | Block `POST /auth/login`; **mass-blacklist** all still-valid access tokens of that user (force the user out of the session immediately). |
| BR-AUTH-08 | Internal endpoints `/internal/**` must be protected by a shared secret. | Header `X-Internal-Secret` must match `app.internal.secret`; mismatch → **403**. |

---

## 2. Account & status rules (BR-USER-NN)

| Rule code | Description | Value / Condition |
|---|---|---|
| BR-USER-01 | `user_status` only takes one of the standard values. | `user_status` ∈ {`inactive`, `active`, `banned`, `overlimitstorage`}. |
| BR-USER-02 | Every new account (normal registration or Google OAuth) is assigned the default plan. | `plan_id` = **1 (Free)**; Free = **2 GB** storage, **15** AI uses/day. |
| BR-USER-03 | The `overlimitstorage` status **only blocks upload actions**, it does not lock other features. | The user may still **list/read their own documents**, use AI chat, review, pay, etc. (Fixes an older document version that claimed it also locked "My Documents".) |
| BR-USER-04 | Transition state `active → overlimitstorage` when the storage quota is exceeded. | Triggered when `storage_used > storage_limit` of the current plan. |
| BR-USER-05 | When deleting a document makes `storage_used` drop below the quota, the status must be restored. | `storage_used ≤ storage_limit` of the Free plan → `overlimitstorage → active`. |
| BR-USER-06 | Only Admin may ban/warn/reactivate another user's account. | Endpoint `/admin/users/{id}/ban|warn|reactivate`; `warn` requires a `reason`. |
| BR-USER-07 | Google OAuth may create an `active` account immediately (without OTP) if no matching `google_id` exists yet. | Create a new account with `status = active`, `plan_id = 1` when no match is found. |

---

## 3. Document & upload rules (BR-DOC-NN)

| Rule code | Description | Value / Condition |
|---|---|---|
| BR-DOC-01 | Allowed upload file formats form a closed set. | `.pdf`, `.docx`, `.txt`, `.md` **only**. Not accepted: `.pptx`, audio, video, zip. |
| BR-DOC-02 | Each file has a maximum size according to the business limit. | ≤ **50 MB** (`app.upload.max-file-size-bytes = 52 428 800`); multipart ceiling 60 MB to return a clean **400** instead of 500. |
| BR-DOC-03 | Uploading duplicate content is not allowed (based on content, not file name). | Compute the **content-hash** of the file; matching content hash → **HTTP 409 Conflict**. |
| BR-DOC-04 | Document status (`document_status`) and visibility (`visibility`) are **two independent columns**. | `document_status` ∈ {`UPLOADING, PROCESSING, PENDING, REJECTED, DELETED, FAILED, COMPLETED`} (7 values, uppercase); `visibility` ∈ {`private`, `public`}. |
| BR-DOC-05 | The initial status depends on the visibility at upload time. | `visibility=private` → `PROCESSING` (index immediately, no moderation); `visibility=public` → `PENDING` (enters the moderation queue). |
| BR-DOC-06 | When a document is soft-deleted, the share link must be disabled. | `link_share = null` when `deleted_at IS NOT NULL`. |
| BR-DOC-07 | Soft-delete **releases storage immediately** but does **not** delete physical data. | Subtract `file_size_bytes` from `storage_used` immediately; **DO NOT** purge S3 / RAG vectors at this step. |
| BR-DOC-08 | Soft-deleted files are kept for a period of time before being hard-deleted. | **Retention 30 days** (`app.document.retention-days`); after that, `DocumentPurgeScheduler` (03:00) purges S3 + DB + RAG. |
| BR-DOC-09 | Restore is only for the **owner**, not for documents hard-deleted by admin. | `POST /documents/{id}/restore`: `DELETED → status_before_deletion`; issue a new `link_share` if `PUBLIC + COMPLETED`. |
| BR-DOC-10 | When changing `visibility` from `private → public`, the document must enter the moderation queue (not be published immediately). | `updateDocument` PRIVATE→PUBLIC → `PENDING`, enqueue `stream:moderation` (see §7). |
| BR-DOC-11 | Download counts are recorded to feed trending. | `GET /documents/{id}/download` returns a presigned URL (TTL 10 minutes) **and** increments `download_count`. |

---

## 4. Tag rules (BR-TAG-NN)

| Rule code | Description | Value / Condition |
|---|---|---|
| BR-TAG-01 | Tags have two visibility levels. | `tag_visibility` ∈ {`public`, `private`}. |
| BR-TAG-02 | **Public** tag labels must be unique across the entire system. | Unique `(label)` WHERE `visibility = public`. |
| BR-TAG-03 | **Private** tag labels are unique per (creator, label) pair. | Unique `(label, created_by)` WHERE `visibility = private`. |
| BR-TAG-04 | When an Admin creates a public tag whose label duplicates an existing private tag, the system **merges** them instead of raising an error. | The private tag is merged into the public tag; `document_tags` repoint to the public tag. |
| BR-TAG-05 | Tags are assigned to documents through a join table. | Table `document_tags` (`document_id`, `tag_id`). |
| BR-TAG-06 | A user's private tags are **never** shown to others when viewing a shared document. | Only `public` tags are displayed in share-link mode. |
| BR-TAG-07 | The tag label length is limited. | `label` ≤ **100 characters** (DB constraint). |

---

## 5. Access rules (BR-ACL-NN)

Authorization is **path-based**, not using `@PreAuthorize`:

- `/admin/**` → requires **ROLE_ADMIN**.
- `/auth/**`, public search/preview/shared, `GET /documents/user/*`, `/internal/**`, `/actuator/health`, swagger → **permitAll**.
- All remaining endpoints → **authentication required**.

### Access matrix by role

| Resource / Action | Guest | User | Admin |
|---|---|---|---|
| View `public` documents (COMPLETED) | ✅ | ✅ | ✅ |
| View `private` / `pending` / `rejected` documents | ❌ | ✅ (owner only) | ✅ |
| Preview `public` / shared documents | ✅ (limited to **30 %** of content) | ✅ (full) | ✅ |
| Download file | ❌ (login required) | ✅ | ✅ |
| Create / use share link (`link_share`) | ✅ (view only, read-only) | ✅ | ✅ |
| Public search (`GET /documents/search`) | ✅ | ✅ | ✅ |
| AI Chat (`POST /chat`) & Quiz/Flashcard | ❌ | ✅ (with quota) | ✅ |
| `/admin/**` endpoints | ❌ | ❌ | ✅ |

| Rule code | Description | Value / Condition |
|---|---|---|
| BR-ACL-01 | Public search returns only documents that are public and not deleted. | `status = COMPLETED` AND `visibility = public` AND `deleted_at IS NULL`; `ILIKE` match on **title + description** (no full-content scan). |
| BR-ACL-02 | Guests may only view **30 %** of the content when previewing a `public` or shared document. | Guest preview → cut off at ~30 %; download / other actions → login required. |
| BR-ACL-03 | A user has full rights only to documents **they own** (besides public documents). | `documents.uploader_id = current_user_id` to edit/restore/delete private documents. |
| BR-ACL-04 | Admin endpoints are locked for every role that is not Admin. | `/admin/**` → **403** if `role != admin`. |

---

## 6. AI & quota rules (BR-AI-NN)

| Rule code | Description | Value / Condition |
|---|---|---|
| BR-AI-01 | Each plan has a daily AI quota. | Free = **15 / day**; Premium = **500 / day**. |
| BR-AI-02 | The AI counter is **shared** across chat + quiz + flashcard. | One chat, one quiz generation, one flashcard generation all deduct from **the same quota**. |
| BR-AI-03 | The counter is stored in Redis with **atomic** operations. | Key `user:ai_limit:{userId}:{yyyy-MM-dd}`; uses the `INCR` command (atomic). |
| BR-AI-04 | The quota key TTL is set on the first use of the day. | TTL = **24 hours** (set when `INCR` returns 1). |
| BR-AI-05 | When the quota is exceeded, the request is blocked at the guard middleware. | Return **HTTP 429**; **DO NOT** increment the counter; **DO NOT** call the LLM/RAG. |
| BR-AI-06 | Quota reset is **lazy**, based on the Redis key TTL, with no midnight cron job. | When `INCR` runs on the new day's key (key does not yet exist) → count = 1, reset TTL to 24 h. |
| BR-AI-07 | Generating study material (quiz/flashcard) may be refused by the LLM (refusal) — this is not treated as a system error. | Refusal → **HTTP 200** + empty list + `reason` in `message`; quota is still deducted. |
| BR-AI-08 | RAG retrieval is limited to the set of context documents the user selected (or a single document when chatting in single-view mode). | The vector search scope is limited by `session_documents` / `document_id`. |

---

## 7. Content moderation rules (BR-MOD-NN)

| Rule code | Description | Value / Condition |
|---|---|---|
| BR-MOD-01 | Automatic moderation **applies only to PUBLIC documents**. | `private` documents do not go through the moderation queue. |
| BR-MOD-02 | Moderation uses a **durable** Redis Streams queue, ensuring at-least-once delivery. | Stream `stream:moderation`; consumer group `moderation-cg`; manual ACK; PEL reclaim 60 s. |
| BR-MOD-03 | Moderation processing must be **idempotent**. | No-op when `status != PENDING` (does not re-moderate already-moderated documents). |
| BR-MOD-04 | Triage is based on the highest score across **all text chunks + embedded images**. | `max(score)` over chunks and images; model `omni-moderation-latest`. |
| BR-MOD-05 | Automatic decision thresholds. | **≥ 0.80** → auto-reject; **< 0.40** → auto-approve; **0.40–0.80** → keep `PENDING` for manual Admin review. |
| BR-MOD-06 | Images embedded in PDF/DOCX must be passed through the same moderation endpoint. | `DocumentImageExtractor` (PDFBox/POI) → base64 → `image_url`; images are included in `max(score)`. |
| BR-MOD-07 | When the **image** moderation flow fails, the document must be deferred to `PENDING` (not auto-approved based on text alone). | Auto-approve requires `imagesChecked`; image failure → defer `PENDING`. |
| BR-MOD-08 | When the **text** moderation flow fails, the error is propagated for retry. | No ACK → message is redelivered; after **5 failures** → **DLQ**. |
| BR-MOD-09 | Skip moderation (keep `PENDING`) when there is no valid OpenAI configuration or no chunks. | `openai.api-key` empty or equal to `mock_key`, or no text chunks → no-op, stays in `PENDING`. |
| BR-MOD-10 | Admin can still approve/reject manually regardless of the automatic result. | `POST /admin/documents/{id}/approve|reject` (`reject` includes `rejectionReason`). |

---

## 8. Payment rules (BR-PAY-NN)

| Rule code | Description | Value / Condition |
|---|---|---|
| BR-PAY-01 | The only integrated payment gateway is VNPay, with a secure signature. | **HMAC-SHA512** signature via `VNPayUtil`; configuration `vnpay.*`. (Fixes an older document version mentioning MoMo/VietQR/PayOS.) |
| BR-PAY-02 | The invoice lifecycle has standard states. | `invoice_status` ∈ {`pending`, `success`, `failed`}; successful payment: `pending → success`. |
| BR-PAY-03 | IPN (server-to-server) must verify the signature before processing. | `GET /payments/vnpay-ipn` verifies HMAC-SHA512; invalid signature → rejected. |
| BR-PAY-04 | The browser callback is a redirect, not the source of truth. | `GET /payments/vnpay-callback` returns **302** to the frontend; final status comes from IPN. |
| BR-PAY-05 | On a successful transaction, the plan upgrade must be an **atomic transaction**. | Within the same DB transaction: change `plan_id`; set `plan_expires_at = NOW() + duration_hours` (default **720** = 30 days, configurable via `app.plan.duration-hours`); `overlimitstorage → active`; create `notifications` (`PLAN_UPGRADED`). |
| BR-PAY-06 | Initiating payment for a free plan is not allowed. | Plan with `price ≤ 0` → reject payment creation (`POST /payments/create-payment`). |
| BR-PAY-07 | Users can look up their own payment history. | `GET /payments/history` filtered by `user_id = current_user_id`. |

---

## 9. Plan expiration & downgrade rules (BR-PLAN-NN)

| Rule code | Description | Value / Condition |
|---|---|---|
| BR-PLAN-01 | There is a scheduled task that **notifies in advance** when a Premium plan is about to expire. | `PlanExpirationScheduler` runs daily at **08:00**; sends `PLAN_EXPIRING` to users whose `plan_expires_at` is within the next **3 days**. |
| BR-PLAN-02 | There is a scheduled task that **proactively downgrades** expired plans. | `PlanDowngradeScheduler` runs daily at **08:00**; downgrades users with `plan_expires_at < NOW()` to Free. |
| BR-PLAN-03 | Downgrade also happens **lazily** when the user interacts. | On each request, if `plan_id != Free` AND `plan_expires_at < NOW()` → downgrade immediately; set `plan_expires_at = NULL`. |
| BR-PLAN-04 | After downgrading to Free, the system rechecks storage against the Free quota to decide the status. | `storage_used > 2 GB` (Free limit) → `overlimitstorage`; otherwise keep `active`. |
| BR-PLAN-05 | The Premium plan offers a higher quota than Free. | Premium = **10 GB** storage, **500** AI uses/day. |
| BR-PLAN-06 | Hard-delete of soft-deleted documents only runs on the maintenance schedule, independent of plan status. | `DocumentPurgeScheduler` at **03:00**; documents with `deleted_at` older than **30 days** → purge S3 + DB + RAG. |

---

## 10. Community interaction rules (BR-SOC-NN)

| Rule code | Description | Value / Condition |
|---|---|---|
| BR-SOC-01 | Reviews apply only to public documents, with a constrained star-rating scale. | Only review on `visibility = public`; `rating` ∈ **1–5** stars; comment optional. |
| BR-SOC-02 | A document's average rating is recalculated to feed Trending. | `avg(rating)` over the document's reviews. |
| BR-SOC-03 | A report has a default status and a standard lifecycle. | `report_status` defaults to `pending`; Admin resolve → `resolved`; Admin reject → `rejected`. |
| BR-SOC-04 | When a valid violation is reported, the document is demoted **and** a violation history is recorded for the uploader. | Resolve → `documents.status` becomes `rejected`/`deleted` + create a `violation_histories` record. |
| BR-SOC-05 | When a report is wrong, the document keeps its public status unchanged. | Reject report → `report_status = rejected`; `documents.status` unchanged. |
| BR-SOC-06 | Bookmarking (saving a document) is unique per user–document pair. | Unique `(user_id, document_id)` in the `saved_documents` table; `POST /{id}/save` & `DELETE /{id}/unsave`. |
| BR-SOC-07 | Document recommendations require the user to have set up preferred tags. | `GET /documents/recommendations` requires `preferred_tag_ids`; suggestions are based on tags. |

---

## 11. Notification rules (BR-NOT-NN)

| Rule code | Description | Value / Condition |
|---|---|---|
| BR-NOT-01 | Notifications are **written synchronously** (not through an asynchronous queue). | Written directly to the `notifications` table within the business processing flow. |
| BR-NOT-02 | Notification types form a closed set, tied to business events. | `type` ∈ {`DOCUMENT_PENDING, DOCUMENT_APPROVED, DOCUMENT_REJECTED, DOCUMENT_RESTORED, NEW_REVIEW, REPORT_SUBMITTED, DOCUMENT_VIOLATION_DELETED, PLAN_UPGRADED, PLAN_EXPIRING, ACCOUNT_BANNED, ACCOUNT_WARNING, ACCOUNT_ACTIVATED`}. |
| BR-NOT-03 | Users may only read their **own** notifications. | `GET /notifications/` filtered by `user_id = current_user_id`. |
| BR-NOT-04 | Marking as read is an intentional action, not automatic. | `PUT /notifications/{id}/read`; no implicit bulk mark-read operation. |
| BR-NOT-05 | Upgrade / downgrade / moderation / report events must emit the corresponding notification. | Event → `type` mapping per § enum (e.g., approve document → `DOCUMENT_APPROVED`; ban user → `ACCOUNT_BANNED`). |

---

## Cross-reference

- The `BR-*` codes reference the feature codes `FEAT-*` (Vision & Scope) and the requirement codes `F-*` (Functional Requirements) within the same document set.
- Detailed values (enums, limits, thresholds) are taken from the **Canonical Contract** §3 (enums), §4 (limits), §7 (document lifecycle), §8 (moderation flow).
- The execution sequence (steps) is in `docs/business_process.md`; this document only states the **constraints** applied to those steps.
