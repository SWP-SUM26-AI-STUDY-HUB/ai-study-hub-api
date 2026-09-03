# Functional & Non-Functional Requirements: AI Study Hub

This document specifies the refined, unambiguous, and measurable Software Requirements Specification (SRS) for the **AI Study Hub** platform. **Parts 1–6** define the Functional Requirements (`F-*` identifiers, per the canonical scheme). **Part 7** defines the Non-Functional Requirements (quality attributes).

Every value, enum, limit, and endpoint in this document is aligned with the project's canonical contract and the verified codebase baseline: Spring Boot 4 / Java 21 LTS, PostgreSQL 16 + pgvector, Redis 7, AWS S3, an external FastAPI RAG microservice (Google Gemini), OpenAI Moderation API, and VNPay payments.

---

## Part 1: Authentication & Profile (F-AUTH)

### 1. Account Registration (F-AUTH-01)
- **F-AUTH-01.1:** The system shall present a registration form accepting **Email Address**, **Password**, and **Full Name**.
- **F-AUTH-01.2:** The system shall query the database to verify whether the provided Email Address already exists.
- **F-AUTH-01.3:** The system shall block the registration and return an "Email already in use" error if the Email Address is found in the database.
- **F-AUTH-01.4:** The system shall hash the password using **bcrypt**, persist a new user record with `user_status = 'inactive'`, and assign the default **`Free`** plan (plan id = 1, 2 GB storage, 15 AI requests/day).
- **F-AUTH-01.5:** The system shall generate a one-time password (OTP), persist it to Redis under key `otp:<email>` with a Time-To-Live of **300 seconds (5 minutes)**, and transmit it to the user's registered Email Address.

### 2. Email Verification (F-AUTH-02)
- **F-AUTH-02.1:** The system shall accept an OTP input or a URL token parameter via a GET request.
- **F-AUTH-02.2:** The system shall verify the validity and expiration status of the OTP/token against Redis (valid within the 300 s TTL).
- **F-AUTH-02.3:** The system shall update `user_status` to `'active'` if the OTP/token is valid and unexpired, enabling login access.

### 3. User Authentication / Login (F-AUTH-03)
- **F-AUTH-03.1:** The system shall authenticate users via Email/Password credentials or Google OAuth2.
- **F-AUTH-03.2:** The system shall compare the user-entered password hash (bcrypt) with the stored hash during credential login.
- **F-AUTH-03.3:** The system shall verify the Google OAuth2 ID Token, extract the Google ID, and check its existence in the database.
- **F-AUTH-03.4:** The system shall **automatically register** a new user account — `user_status = 'active'`, default `Free` plan, syncing Full Name and Email from Google — if the Google ID does not yet exist in the database.
- **F-AUTH-03.5:** The system shall block authentication and return an account-locked message if `user_status = 'banned'`.
- **F-AUTH-03.6:** The system shall issue a **JWT pair** upon successful validation: an **access token (1 hour / 3,600,000 ms)** and a **refresh token (7 days / 604,800,000 ms)**, both carrying user ID, role, and expiration. Refresh requests (`POST /auth/refresh`) are subject to **refresh-token rotation**.

### 4. User Session Termination / Logout (F-AUTH-04)
- **F-AUTH-04.1:** The system shall invalidate the active session when a logout request (`POST /auth/logout`) is received.
- **F-AUTH-04.2:** The system shall write the incoming access token to a Redis blacklist with a TTL equal to the token's **remaining lifetime** to prevent reuse. The **refresh token is also blacklisted** (same remaining-lifetime TTL).
- **F-AUTH-04.3:** The system shall redirect the user's browser view to the landing page.

### 5. Forgot Password Recovery (F-AUTH-05)
- **F-AUTH-05.1:** The system shall accept an Email Address on the password-recovery interface.
- **F-AUTH-05.2:** The system shall verify the email's existence, generate a reset token (UUID) stored in Redis under key `reset_token:<email>` (own `reset_token:` prefix, **separate** from the registration-OTP `otp:<email>` key; the token UUID is the stored value and is looked up by email), **TTL 900 s / 15 min**, and email a unique reset URL containing the token (and the email).
- **F-AUTH-05.3:** The system shall accept a new password and validation token from the reset interface.
- **F-AUTH-05.4:** The system shall validate the token, hash the new password (bcrypt), update the password field, clear the reset token, and redirect the user to the login screen.

### 6. Profile Customization (F-AUTH-06)
- **F-AUTH-06.1:** The system shall accept display-name updates and profile-image uploads on the profile interface.
- **F-AUTH-06.2:** The system shall validate that the uploaded avatar is a real **JPEG or PNG** (magic-byte check) and does not exceed **2 MB**.
- **F-AUTH-06.3:** The system shall upload validated avatars to AWS S3, update the user record with the new display name and S3 URL, and return the updated fields.

---

## Part 2: Document Management (F-DOC)

### 7. Document Upload (F-DOC-01)
- **F-DOC-01.1:** The system shall accept file uploads in **`.pdf`, `.docx`, `.txt`, and `.md`** formats only. PowerPoint/`.pptx`, audio, video, and archives are rejected.
- **F-DOC-01.2:** The system shall reject uploads from users whose `user_status = 'overlimitstorage'` (this guard blocks **upload only**; all other features remain available).
- **F-DOC-01.3:** The system shall verify that `storage_used + file_size ≤ storage_limit` defined by the active plan (**Free 2 GB / Premium 10 GB**).
- **F-DOC-01.4:** The system shall enforce a **50 MB single-file business cap** (`app.upload.max-file-size-bytes = 52,428,800`). A multipart ceiling of 60 MB ensures oversized requests return a clean HTTP 400 rather than a 500.
- **F-DOC-01.5:** The system shall compute a content hash of the uploaded file and, if a document with the same hash already exists for the user, return **HTTP 409 Conflict** (content-hash deduplication).
- **F-DOC-01.6:** The system shall upload the file to AWS S3 and create a document record with initial status `UPLOADING`, then transition to **`PROCESSING`** (private) or **`PENDING`** (public). Multipart fields accepted: `file`, `title`, `tags[]`, `description`, `visibility`.
- **F-DOC-01.7:** For **private** documents, the RAG `/internal/documents/callback` (`/process`) extracts and indexes content → status `COMPLETED` (no moderation).
- **F-DOC-01.8:** For **public** documents, the RAG `/extract` callback (`EXTRACTED`) enqueues the document onto the auto-moderation stream (see F-MOD-01).
- **F-DOC-01.9:** The system shall increment `storage_used` by the exact file size in bytes.

### 8. Document Tagging (F-DOC-02)
- **F-DOC-02.1:** The system shall allow users to attach existing public tags, their own private tags, or create new private tags (**label ≤ 100 characters**, DB constraint) during upload or profile-edit flows.
- **F-DOC-02.2:** The system shall query whether tag text exists as a public tag or the user's private tag. If not, it shall create a new private tag owned by the user. When an Admin creates a new public tag, any matching user private tags are automatically merged into it.
- **F-DOC-02.3:** The system shall write association records to the `document_tags` mapping table.

### 9. Personal Library Access (F-DOC-03)
- **F-DOC-03.1:** The system shall query and return the user's active documents (`deleted_at IS NULL`) — private, public, and pending — via `GET /documents/personal`.
- **F-DOC-03.2:** Users with `user_status = 'overlimitstorage'` **can still list and read their own documents**. `overlimitstorage` blocks **upload only** and does **NOT** lock the personal document library.
- **F-DOC-03.3:** The system shall provide a trash view (`GET /documents/trash`) of the user's soft-deleted documents.

### 10. Document Preview and Download (F-DOC-04)
- **F-DOC-04.1:** The system shall serve previews via an **AWS S3 presigned GET URL** (valid 10 minutes) for in-browser rendering.
- **F-DOC-04.2:** The download endpoint (`GET /documents/{id}/download`) shall increment the document's `download_count` and return a presigned URL.
- **F-DOC-04.3:** **Guest (unauthenticated) users are restricted to a 30 % preview** of public and shared documents; full download requires authentication.
- **F-DOC-04.4:** The system shall restrict preview/download on documents that are `private`, `PENDING`, or `REJECTED` to the document owner and admins.
- **F-DOC-04.5:** The system shall render previews within **3.0 seconds**.

### 11. Public Search (F-DOC-05)
- **F-DOC-05.1:** The system shall execute keyword search (`GET /documents/search?keyword=`) matching the document **title and description** via SQL `ILIKE` (case-insensitive), scoped to documents that are `visibility = public` **AND** `status = COMPLETED` **AND** `deleted_at IS NULL`.
- **F-DOC-05.2:** The system shall return matching results in **less than 1.5 seconds**.
- **F-DOC-05.3:** Public search matches **title + description only** — it does **NOT** scan extracted document text. Full-content / semantic search over document bodies is provided exclusively by the **RAG chat** feature (F-AI-01); there is no full-text index over document bodies in this API.

### 12. Share Link Generation (F-DOC-06)
- **F-DOC-06.1:** The system shall generate a unique cryptographic token (UUID/hash) for a document upon user request (`POST /documents/{id}/share`).
- **F-DOC-06.2:** The system shall write the token to the document's `link_share` field.
- **F-DOC-06.3:** The system shall expose a public read-only preview URL (`GET /documents/shared/{token}`, permitAll).

### 13. Document Metadata Modification (F-DOC-07)
- **F-DOC-07.1:** The system shall permit owners to edit title, tags, and visibility of their documents (`PUT /documents/{id}`).
- **F-DOC-07.2:** The system shall set status to `PENDING` and enqueue the document for moderation (see F-MOD-01) when an owner changes visibility from **Private → Public**.

### 14. Document Soft-Deletion (F-DOC-08)
- **F-DOC-08.1:** The system shall display a confirmation modal prior to deleting a document.
- **F-DOC-08.2:** The system shall set `status = 'DELETED'`, record `deleted_at = now`, store `status_before_deletion`, null out `link_share`, and subtract the file size from the owner's `storage_used`.
- **F-DOC-08.3:** The system shall **NOT** immediately purge S3 objects or RAG vectors on soft-delete.
- **F-DOC-08.4:** The system shall hard-delete soft-deleted documents (purge S3 + DB + RAG vectors) only after the retention window via the `DocumentPurgeScheduler` (daily 03:00, `app.document.retention-days = 30`).

### 15. Restore from Trash (F-DOC-09)
- **F-DOC-09.1:** The system shall allow a document **owner** to restore a soft-deleted document (`POST /documents/{id}/restore`).
- **F-DOC-09.2:** The system shall restore the document `status` to the stored `status_before_deletion` value and clear `deleted_at`.
- **F-DOC-09.3:** The system shall add the file size back to the owner's `storage_used`.
- **F-DOC-09.4:** The system shall mint a **fresh `link_share`** if the restored status was `PUBLIC` **and** `COMPLETED`.
- **F-DOC-09.5:** The system shall **NOT** invoke the RAG service on restore (vectors are not re-fetched).
- **F-DOC-09.6:** The system shall **forbid restoration** of documents that were admin-deleted (`deleted_by_admin = true`).

### 16. Bookmark / Save (F-DOC-10)
- **F-DOC-10.1:** The system shall allow an authenticated user to **save** (`POST /documents/{id}/save`) and **unsave** (`DELETE /documents/{id}/unsave`) public documents.
- **F-DOC-10.2:** The system shall enforce a unique bookmark per `(user, document)` pair — duplicate saves are rejected.
- **F-DOC-10.3:** The system shall return the user's saved documents via `GET /documents/saved`.

### 17. Recommendations (F-DOC-11)
- **F-DOC-11.1:** The system shall return recommended documents (`GET /documents/recommendations`) based on the user's `preferred_tag_ids` (captured during onboarding via `POST /users/preferred-tags`).
- **F-DOC-11.2:** The system shall cap the candidate tag-id set at **100 ids**.
- **F-DOC-11.3:** The system shall return recommendation results **paginated**.

---

## Part 3: Social Learning & Interaction (F-SOC)

### 18. Review and Rating (F-SOC-01)
- **F-SOC-01.1:** The system shall allow authenticated users to submit a numerical rating (integer 1–5) and a comment on public documents (`POST /documents/{id}/reviews`).
- **F-SOC-01.2:** The system shall write the review record and recalculate the document's average rating. A `NEW_REVIEW` notification is written for the document owner (see F-NOT-01).

### 19. Abuse & Content Reporting (F-SOC-02)
- **F-SOC-02.1:** The system shall allow authenticated users to submit a violation report describing copyright or Terms-of-Service violations (`POST /documents/{id}/reports`).
- **F-SOC-02.2:** The system shall insert a report record with `report_status = 'pending'` and write a `REPORT_SUBMITTED` notification to the admin dashboard (see F-NOT-01).

---

## Part 4: AI Capabilities (F-AI)

### 20. Multi-Document Contextual Chat (F-AI-01)
- **F-AI-01.1:** The system shall accept user chat queries and an array of target document IDs (`POST /chat`).
- **F-AI-01.2:** The system shall enforce the daily AI quota **before** any RAG call, per F-MON-03 (Redis key `user:ai_limit:{userId}:{date}`, atomic `INCR`). On overflow the system returns **HTTP 429** and does **not** call the LLM.
- **F-AI-01.3:** The system shall initialize a new chat session containing the selected documents if no session id is provided.
- **F-AI-01.4:** The system shall delegate retrieval-augmented generation to the external FastAPI RAG microservice (Google **Gemini** LLM, `gemini-embedding-001` @ 1536 dims, BM25 + pgvector hybrid retrieval, Jina reranker top-5).
- **F-AI-01.5:** The system shall return the response, page citations, and file references in **less than 5 seconds** (configured `chat-timeout = 30 s`).
- **F-AI-01.6:** The system shall record query, response, and citations as structured JSON in the chat messages table.

### 21. Single-Document Contextual Chat (F-AI-02)
- **F-AI-02.1:** The system shall execute single-document queries/summarizations applying the same quota checks (F-MON-03), restricting the semantic search context window to the specified document ID.
- **F-AI-02.2:** The system shall return the response and persist the message under the shared quota counter.

### 22. Chat Session Management (F-AI-03)
- **F-AI-03.1:** The system shall return the user's active chat sessions where `deleted_at IS NULL` (`GET /chat/sessions`).
- **F-AI-03.2:** The system shall fetch and display chronological message history for a selected session (`GET /chat/sessions/{id}/messages`).
- **F-AI-03.3:** The system shall allow users to rename sessions (`PATCH /chat/sessions/{id}`) or soft-delete them (`DELETE /chat/sessions/{id}`).

### 23. Study-Material Generation (F-AI-04)
- **F-AI-04.1:** The system shall generate **document-scoped** quizzes (`POST /study-materials/quiz`) and **flashcards** (`POST /study-materials/flashcard`) via the RAG microservice over a single specified document.
- **F-AI-04.2:** Study-material generation counts against the **shared daily AI quota** (chat + quiz + flashcard; Free 15/day, Premium 60/day), and the quota counter is incremented **before** the RAG call is dispatched.
- **F-AI-04.3:** On quota overflow, the system shall return **HTTP 429**, **not** increment the counter beyond the limit, and **not** call the RAG service.
- **F-AI-04.4:** On model refusal, the system shall return **HTTP 200** with an **empty list** and a human-readable **reason in the `message` field** (refusal is not an error).
- **F-AI-04.5:** The system shall enforce a **60-second timeout** on study-material generation.

---

## Part 5: Content Moderation, Admin Dashboard & Notifications (F-MOD, F-ADM, F-NOT)

### 24. Auto-Moderation (F-MOD-01)
- **F-MOD-01.1:** The system shall auto-triage `PUBLIC` documents using the **OpenAI Moderation API** (`omni-moderation-latest`) on a **durable Redis Streams** queue (`stream:moderation`, consumer group `moderation-cg`, manual ACK).
- **F-MOD-01.2:** Moderation is enqueued from two sites: (a) the RAG `EXTRACTED` callback for fresh public uploads, and (b) `updateDocument` on a **Private → Public** visibility change.
- **F-MOD-01.3:** The consumer shall read text chunks (read-only from `document_chunks`) and extract embedded images from the S3 original (PDF via PDFBox, DOCX via POI), encoding images as base64 `image_url` inputs.
- **F-MOD-01.4:** The system shall call the Moderation API in text batches (≤ 30) and smaller image batches, taking the **maximum category score across all chunks and images**.
- **F-MOD-01.5:** The system shall apply thresholds: score **≥ 0.80 → auto-reject**; **< 0.40 → auto-approve**; **0.40–0.80 → leave `PENDING`** for manual admin review.
- **F-MOD-01.6:** The process shall be **idempotent** — it is a no-op when the document status is not `PENDING`.
- **F-MOD-01.7:** The system shall move a message to a **dead-letter queue (DLQ) after 5 failed attempts**. A pending-entries-list (PEL) reclaim job (60 s) re-claims idle stream messages.
- **F-MOD-01.8:** The system shall skip (stay `PENDING`) when `openai.api-key` is empty or `mock_key`, or when chunks are empty. Image-flow failure defers to `PENDING` (auto-approve requires `imagesChecked`); text-moderation failure propagates (unacked → retried → DLQ).

### 25. Manual Moderation (F-MOD-02)
- **F-MOD-02.1:** The system shall allow an admin to approve or reject `PENDING` documents (`POST /admin/documents/{id}/approve` | `POST /admin/documents/{id}/reject` with a `rejectionReason` body).
- **F-MOD-02.2:** On **approve**, the system shall set `visibility = public`, trigger indexing (`POST /index` to embed pending chunks), and transition the document to `COMPLETED` (visible and searchable).
- **F-MOD-02.3:** On **reject**, the system shall set `status = REJECTED` and purge the document's RAG vectors (`DELETE /documents/{id}`, `@Async deleteVectors`).

### 26. Content Moderation UI / Pending Queue (F-ADM-01)
- **F-ADM-01.1:** The system shall present `PENDING` public documents on the admin interface (`GET /admin/documents/pending`). These include auto-moderation **yellow-zone (0.40–0.80)** documents plus any documents deferred to manual review (F-MOD-01).
- **F-ADM-01.2:** The admin shall invoke manual approve/reject per F-MOD-02 on surfaced documents.
- **F-ADM-01.3:** The system shall write a notification to the document owner indicating the moderation outcome (`DOCUMENT_APPROVED` / `DOCUMENT_REJECTED`) — see F-NOT-01.

### 27. Violation Review (F-ADM-02)
- **F-ADM-02.1:** The system shall present pending reports on the admin interface (`GET /admin/reports/documents`).
- **F-ADM-02.2:** On report confirmation (`POST /admin/reports/{id}/resolve`), the system shall set `report_status = 'resolved'`, change the associated document to `REJECTED` or `DELETED`, and log a violation entry (`DOCUMENT_VIOLATION_DELETED`) against the uploader.
- **F-ADM-02.3:** On report dismissal (`POST /admin/reports/{id}/reject`), the system shall set `report_status = 'rejected'` and leave the document's public visibility unchanged.

### 28. Account Warnings & Sanctions (F-ADM-03)
- **F-ADM-03.1:** The system shall allow administrators to issue warning alerts (`POST /admin/users/{id}/warn`, `reason` required) or apply account bans (`POST /admin/users/{id}/ban`).
- **F-ADM-03.2:** On ban, the system shall set `user_status = 'banned'`, add the user's active JWTs to the Redis blacklist (invalidating the session immediately), and write an `ACCOUNT_BANNED` notification.
- **F-ADM-03.3:** The system shall allow an admin to reactivate a previously sanctioned account (`POST /admin/users/{id}/reactivate`), writing an `ACCOUNT_ACTIVATED` notification.

### 29. Aggregation and Stats (F-ADM-04)
- **F-ADM-04.1:** The system shall aggregate and display system metrics via `GET /admin/dashboard/stats` (user signups, successful uploads, total storage usage, monthly invoice revenues) using SQL aggregation.

### 30. In-App Notifications (F-NOT-01)
- **F-NOT-01.1:** The system shall write synchronous notification rows on document-lifecycle events (`DOCUMENT_PENDING`, `DOCUMENT_APPROVED`, `DOCUMENT_REJECTED`, `DOCUMENT_RESTORED`), reviews (`NEW_REVIEW`), reports (`REPORT_SUBMITTED`, `DOCUMENT_VIOLATION_DELETED`), plan events (`PLAN_UPGRADED`, `PLAN_EXPIRING`), and account sanctions (`ACCOUNT_BANNED`, `ACCOUNT_WARNING`, `ACCOUNT_ACTIVATED`).
- **F-NOT-01.2:** The system shall allow a user to list their notifications (`GET /notifications`) and mark a notification as read (`PUT /notifications/{id}/read`).

---

## Part 6: Subscription & Payment (F-MON)

### 31. Subscription Purchase Flow (F-MON-01)
- **F-MON-01.1:** The system shall create an invoice record with `invoice_status = 'pending'` containing the target plan id when an upgrade is initiated.
- **F-MON-01.2:** The system shall query the **VNPay** payment gateway (`POST /payments/create-payment`) — signed via **HMAC-SHA512** (`VNPayUtil`) — and return a VNPay sandbox URL containing the invoice amount and reference.

### 32. Payment Webhook Automation (F-MON-02)
- **F-MON-02.1:** The system shall expose a server-to-server **VNPay IPN** endpoint (`GET /payments/vnpay-ipn`) and **verify the HMAC-SHA512 signature** of the payload, rejecting any tampered callback.
- **F-MON-02.2:** The system shall expose a browser **VNPay callback** endpoint (`GET /payments/vnpay-callback`) that issues a **302 redirect** to the frontend.
- **F-MON-02.3:** On a verified payment, the system shall — within a single atomic database transaction — set `invoice_status = 'success'`, change the user's plan to **Premium**, set `plan_expires_at = now + 30 days`, and restore `user_status = 'active'` (clearing any prior `overlimitstorage` state), then write a `PLAN_UPGRADED` notification.

### 33. Daily AI Quota — Redis Counter (F-MON-03)
- **F-MON-03.1:** The system shall intercept AI requests (chat, quiz, flashcard) and enforce the daily quota using Redis key `user:ai_limit:{userId}:{yyyy-MM-dd}`.
- **F-MON-03.2:** The system shall increment the counter atomically via `INCR`.
- **F-MON-03.3:** The system shall set a **24-hour TTL** on the key when it is first created for the day.
- **F-MON-03.4:** The quota is a **shared counter** across chat + quiz + flashcard (**Free 15/day, Premium 60/day**). There is **no midnight cron** — reset is lazy/Redis-based via the daily-rotating key.
- **F-MON-03.5:** On overflow, the system shall return **HTTP 429**, **not** increment the counter, and **not** invoke the LLM/RAG service.

### 34. Subscription Expiry — Scheduled & Lazy Downgrade (F-MON-04)
- **F-MON-04.1:** The system shall run the `PlanDowngradeScheduler` **daily at 08:00** to proactively downgrade users whose `plan_expires_at < now` from Premium to the **Free** plan.
- **F-MON-04.2:** The system shall run the `PlanExpirationScheduler` **daily at 08:00** to send `PLAN_EXPIRING` notifications to users whose plan expires within 3 days.
- **F-MON-04.3:** The system shall perform a **lazy downgrade** on any API activity: if the plan has expired and is not Free, downgrade to Free and set `plan_expires_at = null`.
- **F-MON-04.4:** The system shall compare `storage_used` against the **Free** tier limit (**2 GB**); if exceeded, it shall set `user_status = 'overlimitstorage'`, which blocks **upload only** (per F-DOC-03) — all other features remain available.

---

## Part 7: Non-Functional Requirements (NFR)

### 7.1 Performance
- AI chat responses complete in **< 5 s** (configured `chat-timeout = 30 s`).
- Study-material generation enforces a **60 s** timeout.
- Document preview renders in **< 3 s**.
- Keyword (public) search returns in **< 1.5 s**.
- S3 presigned GET URLs are valid for **10 minutes**.

### 7.2 Scalability
- The API is **stateless** (JWT-based, no server-side session) and scales **horizontally** behind a load balancer.
- **Virtual threads** are enabled (`spring.threads.virtual.enabled`) for blocking I/O.
- Redis caching is used with **externalized per-cache TTLs** (trending list 10 m, public-tags 30 m).
- Each backend instance must use a **distinct Redis Streams consumer-name** for moderation so that messages are load-balanced across instances.

### 7.3 Reliability & Availability
- Moderation uses a **durable at-least-once** Redis Streams queue with manual ACK and a **DLQ after 5 attempts**.
- Moderation processing is **idempotent** (no-op unless status is `PENDING`).
- **External calls** (email, OpenAI Moderation, RAG microservice) are **best-effort** — failures degrade gracefully and never corrupt persisted state.
- The async executor performs a **graceful shutdown (60 s)** and applies **caller-runs back-pressure** when the work queue is saturated.

### 7.4 Security
- **HTTPS/TLS** for all transport.
- **bcrypt** password hashing.
- **Short-lived JWT access (1 h)** with **rotating refresh (7 d)**.
- **Redis blacklist** for both access and refresh tokens (TTL = remaining token lifetime).
- `overlimitstorage` is a **content-guard** that blocks **upload only** (never read access).
- **AWS S3 presigned URLs** mediate all file access (no direct bucket exposure).
- **Path-based authorization** (no `@PreAuthorize`): `/admin/**` requires `ROLE_ADMIN`; an explicit public allow-list covers `/auth/**`, public document search/preview/shared, `GET /documents/user/*`, `/internal/**`, `/actuator/health`, and Swagger; everything else requires authentication.
- `/internal/**` endpoints require an **`X-Internal-Secret`** header matching `app.internal.secret` (HTTP 403 on mismatch).
- **VNPay** callbacks are verified via **HMAC-SHA512** signature.
- **Actuator** is locked to `ADMIN` except `/actuator/health` and `/actuator/info`, which are publicly exposed.

### 7.5 Observability
- **Structured logging** throughout the application.
- Actuator **health/info** endpoints exposed for monitoring.
- **Externalized** per-cache TTLs, timeouts, and thresholds via `${ENV}` configuration.

### 7.6 Maintainability & Portability
- **Layered architecture**: controller → service → repository.
- **Manual DTO ↔ entity mapping** using Lombok builders (**no MapStruct**).
- **Externalized configuration** via `${ENV}` placeholders (`application.yaml`).
- **Docker multi-stage build** and **docker-compose** orchestration (PostgreSQL + pgvector, Redis 7 alpine, backend) on an external network.
- **CI pipeline**: build → test → deploy, with deploys gated on `mvn test`.

### 7.7 Compatibility
- **Java 21 LTS** runtime.
- **PostgreSQL 16 + pgvector** with a `vector(1536)` embedding column and HNSW cosine index.
- **Redis 7**.
- Embeddings are fixed at **1536 dimensions** (`gemini-embedding-001`).

### 7.8 Constraints
- **No online/in-place document editing** — documents are immutable once uploaded.
- **No audio, video, archive (`.zip`), or `.pptx` uploads**; accepted formats are `.pdf`, `.docx`, `.txt`, `.md` only.
- **AI features are quota-gated** by the shared daily counter (F-MON-03).
- **Upload is storage-gated** by the active plan limit; `overlimitstorage` blocks upload only.
