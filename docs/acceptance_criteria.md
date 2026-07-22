# Detailed Acceptance Criteria (AC) - AI Study Hub

This document defines the detailed Acceptance Criteria (AC) in the form of Behavior-Driven Development (BDD) Gherkin scenarios for the **AI Study Hub** platform. Every scenario is mapped to a functional requirement (`F-*`) from the Functional Requirements document. All values (limits, lifetimes, enums, endpoints, HTTP status codes) are aligned with the canonical system contract: Spring Boot 4 / Java 21 backend, PostgreSQL 16 + pgvector, Redis 7, AWS S3, external FastAPI RAG microservice, OpenAI Moderation API, and **VNPay** billing.

---

## 📌 BDD Gherkin Conventions

Test scenarios are written using standard Gherkin keywords:
- **Given:** The initial state, preconditions, or system context.
- **When:** An action triggered by the user or a system event.
- **Then:** The expected, measurable, and verifiable outcome.
- **And:** A combination of additional conditions or outcomes.

Each scenario carries its owning functional-requirement ID (`F-*`) in the section heading.

---

## 📑 Subscription Limits Summary Table

For testing edge cases related to permissions and storage limits. These limits are the single source of truth referenced throughout this document.

| Feature / Limit | Free Plan (Default, plan id = 1) | Premium Plan |
| :--- | :--- | :--- |
| **Max Cloud Storage** | **2 GB** | **10 GB** |
| **Daily AI Request Limit** (shared counter: chat + quiz + flashcard) | **15 / day** | **60 / day** |
| **Allowed File Formats** | `.pdf`, `.docx`, `.txt`, `.md` | `.pdf`, `.docx`, `.txt`, `.md` |
| **Max File Upload Size** | **50 MB** (`52428800` bytes) | **50 MB** (`52428800` bytes) |
| JWT access / refresh token | 1 hour / 7 days | 1 hour / 7 days |
| OTP lifetime | 300 s (5 min) | — |
| Reset-password token | 900 s (15 min) | — |
| Soft-delete retention before permanent purge | 30 days | 30 days |

---

## 🚪 PART 1: AUTHENTICATION & PROFILE (F-AUTH)

### 1. Account Registration (F-AUTH-01)

> [!NOTE]
> Passwords must be hashed using the `bcrypt` algorithm before being saved to the database. The initial account status upon successful registration must be `'inactive'`, and the default plan must be set to `'Free'`. The registration OTP is valid for **300 seconds (5 minutes)**.

#### Scenario 1: Successful account registration (Happy Path)
- **Given** The guest is on the Account Registration page.
- **When** The guest enters an email address that does not exist in the system, a valid full name, and a secure password.
- **And** The guest clicks the "Register" button.
- **Then** The system queries the database to verify the email address is not in use.
- **And** The system hashes the password using bcrypt.
- **And** The system creates a new user record in the database with status `'inactive'` and the default plan set to `'Free'`.
- **And** The system generates a secure one-time password (OTP) with an expiration of **300 seconds** and saves it to Redis under key `otp:<email>`.
- **And** The system sends an email containing the OTP to the user's registered email address.
- **And** The system redirects the user to the Email Verification page with a success message.

#### Scenario 2: Registration failed due to email already in use (Alternative Path)
- **Given** The guest is on the Account Registration page.
- **When** The guest enters an email address that already exists and is active in the database.
- **And** The guest fills in all other fields and clicks the "Register" button.
- **Then** The system queries the database and detects that the email already exists.
- **And** The system blocks the registration process.
- **And** The system displays the error message: "Email already in use".
- **And** The system does not generate an OTP or write a new user record.

#### Scenario 3: Registration failed due to invalid input data (Edge Case)
- **Given** The guest is on the Account Registration page.
- **When** The guest clicks the "Register" button while leaving required fields empty, entering an invalid email format (e.g., `abc@`), or entering a weak password.
- **Then** The system performs client-side and server-side validation.
- **And** The system blocks the registration request from hitting the database.
- **And** The system displays corresponding error messages under each invalid field (e.g., "Invalid email address format", "Password must be at least 8 characters long").

---

### 2. Email Verification (F-AUTH-02)

> [!NOTE]
> Verification is performed by `POST /api/v1/auth/verify`, sending `email` and `otp` as request parameters. The OTP is valid for **300 seconds (5 minutes)**.

#### Scenario 1: Successful account activation via OTP (Happy Path)
- **Given** The user has just registered and is on the Email Verification page.
- **When** The user enters the correct OTP received in their email.
- **And** The user clicks the "Verify" button, sending `email` and `otp` as request parameters to `POST /api/v1/auth/verify`.
- **Then** The system queries Redis (`otp:<email>`) to verify the validity and expiration status of the OTP.
- **And** The system confirms that the OTP is valid and unexpired (within **300 seconds**).
- **And** The system updates the user's status in the database to `'active'`.
- **And** The system deletes the consumed OTP from Redis.
- **And** The system displays a success message and redirects the user to the Login page.

#### Scenario 2: Verification failed due to expired OTP (Edge Case)
- **Given** The user enters the OTP more than **300 seconds** after registration.
- **When** The user submits the verification request.
- **Then** The system checks Redis and finds that the OTP has expired or does not exist.
- **And** The system keeps the user's status as `'inactive'` in the database.
- **And** The system displays an error message: "Verification code has expired or is invalid. Please request a new code."

#### Scenario 3: Verification failed due to incorrect OTP (Edge Case)
- **Given** The user enters an OTP that does not match the value stored in Redis.
- **When** The user submits the verification request.
- **Then** The system detects the OTP mismatch.
- **And** The system keeps the user's status as `'inactive'`.
- **And** The system displays an error message: "Invalid verification code".
- **And** The user may request a new OTP via `POST /api/v1/auth/resend-otp`.

---

### 3. User Authentication / Login (F-AUTH-03)

> [!NOTE]
> On successful authentication the system issues a **JWT pair**: a short-lived **access token (1 hour)** and a long-lived **refresh token (7 days)**. Refresh-token rotation is used: each refresh mints a new pair.

#### Scenario 1: Successful login via traditional credentials (Happy Path)
- **Given** The user has an active account (status `'active'`).
- **When** The user enters their correct Email and Password on the Login page and clicks "Login".
- **Then** The system compares the hash of the entered password with the hashed password stored in the database.
- **And** The system confirms the hashes match.
- **And** The system generates a JWT **access token with a 1-hour expiration** and a **refresh token with a 7-day expiration**.
- **And** The access token contains `user_id` and `user_role`.
- **And** The system returns the access/refresh token pair to the client.
- **And** The system redirects the user to the dashboard.

#### Scenario 2: Successful login via Google OAuth2 - Existing User (Happy Path)
- **Given** The user has an active account linked to their Google account.
- **When** The user clicks "Login with Google" and completes authentication.
- **Then** The system receives and verifies the Google ID Token via the Google callback endpoint.
- **And** The system finds a matching Google ID in the database.
- **And** The system confirms the user status is active.
- **And** The system issues a new JWT pair (access token **1 hour**, refresh token **7 days**) and logs the user into the dashboard.

#### Scenario 3: Google OAuth2 login - Automatic Registration (Alternative Path)
- **Given** The visitor does not have an account in the system database.
- **When** The visitor clicks "Login with Google" and completes authentication.
- **Then** The system verifies the Google ID Token and finds no matching Google ID in the database.
- **And** The system automatically registers a new user record with status `'active'`, plan `'Free'`, and syncs the full name and email from Google.
- **And** The system issues a new JWT pair (access token **1 hour**, refresh token **7 days**) and logs the user into the dashboard.

#### Scenario 4: Login blocked for banned account (Edge Case)
- **Given** The user's account has a status of `'banned'` in the database.
- **When** The user tries to log in using traditional credentials or Google OAuth2.
- **Then** The system verifies the credentials but detects that the user status is `'banned'`.
- **And** The system blocks access.
- **And** The system returns the error message: "Your account has been locked due to violations of our Terms of Service".
- **And** The system does not issue a JWT.

#### Scenario 5: Access-token renewal via refresh-token rotation (Happy Path)
- **Given** The user holds a valid, non-rotated refresh token (issued within the last 7 days).
- **When** The client calls `POST /api/v1/auth/refresh` with the refresh token.
- **Then** The system validates the refresh token.
- **And** The system issues a **new access token (1 hour)** and a **new refresh token (7 days)**.
- **And** The previous refresh token is invalidated (rotation).
- **And** The system returns `200 OK` with the new token pair.

#### Scenario 6: Refresh rejected for an invalid or already-rotated token (Edge Case)
- **Given** The client presents a refresh token that is expired, malformed, or already used in a prior rotation.
- **When** The client calls `POST /api/v1/auth/refresh`.
- **Then** The system rejects the request.
- **And** The system returns `401 Unauthorized` and does not issue any new tokens.

---

### 4. User Session Termination / Logout (F-AUTH-04)

> [!IMPORTANT]
> To prevent JWT reuse after logout, the system adds the incoming **access token** to a Redis blacklist with a Time-To-Live (TTL) equal to the token's **remaining lifetime**. The associated **refresh token is also invalidated** so it can no longer be rotated.

#### Scenario 1: Successful logout (Happy Path)
- **Given** The user is logged in with a valid access token and refresh token.
- **When** The user clicks the "Logout" button on the navigation bar (sends `POST /api/v1/auth/logout`).
- **Then** The system retrieves the access token from the request header.
- **And** The system calculates the remaining time-to-live (TTL) of the access token.
- **And** The system adds the access token to the Redis blacklist with the calculated TTL.
- **And** The system invalidates the user's refresh token so it can no longer be used for rotation.
- **And** The client-side clears the tokens from LocalStorage/Cookies.
- **And** The system redirects the user's browser view to the Landing Page.

#### Scenario 2: Rejected API requests using a logged-out access token (Edge Case)
- **Given** The user has successfully logged out, and their access token is blacklisted in Redis.
- **When** An API request is sent using the blacklisted access token.
- **Then** The auth middleware checks Redis and identifies the access token as blacklisted.
- **And** The system rejects the request immediately.
- **And** The system returns HTTP `401 Unauthorized` with a message stating the session has expired.

#### Scenario 3: Refresh token rejected after logout (Edge Case)
- **Given** The user has logged out and their refresh token was invalidated.
- **When** The client attempts to call `POST /api/v1/auth/refresh` with the invalidated refresh token.
- **Then** The system rejects the rotation request.
- **And** The system returns `401 Unauthorized` and issues no new tokens.

---

### 5. Forgot Password Recovery (F-AUTH-05)

> [!NOTE]
> Reset-password tokens are stored in Redis under key `otp:reset:<uuid>` with a **900-second (15-minute)** TTL.

#### Scenario 1: Successful password reset request (Happy Path)
- **Given** The user does not remember their password and is on the "Forgot Password" page.
- **When** The user enters their registered Email address and clicks "Submit" (`POST /api/v1/auth/forgot-password`).
- **Then** The system confirms the email exists in the database.
- **And** The system generates a temporary password-reset token stored in Redis (`otp:reset:<uuid>`) with a **900-second** TTL.
- **And** The system sends an email containing a unique reset URL with the token (e.g., `/reset-password?token=xxxx`).
- **And** The system displays the message: "A password reset link has been sent to your email".

#### Scenario 2: Successful password reset (Happy Path)
- **Given** The user clicks the password reset link from their email and is on the Password Reset page.
- **When** The user enters a new valid password and clicks "Update Password" (`POST /api/v1/auth/reset-password`).
- **Then** The system validates that the token is valid and has not expired (within **900 seconds**).
- **And** The system hashes the new password using bcrypt.
- **And** The system updates the user's password record in the database.
- **And** The system deletes the reset token from Redis to prevent reuse.
- **And** The system redirects the user to the login screen with a success message.

#### Scenario 3: Reset failed due to expired or invalid token (Edge Case)
- **Given** The user clicks the reset link more than **900 seconds** after it was issued, or uses an already-consumed token.
- **When** The user submits a new password.
- **Then** The system finds the token is missing or expired in Redis.
- **And** The system rejects the reset.
- **And** The system displays: "Reset token has expired or is invalid. Please request a new link".
- **And** The user's password in the database remains unchanged.

---

### 6. Profile Customization (F-AUTH-06)

#### Scenario 1: Successful profile and avatar update (Happy Path)
- **Given** The user is authenticated and is on the Profile Edit page.
- **When** The user enters a new display name and uploads an image file (`avatar.jpg`, size 1.2 MB) to `POST /api/v1/users/edit-profile/avatar`.
- **And** The user clicks "Save Changes".
- **Then** The system validates that the uploaded file is a real PNG/JPEG (magic bytes) and its size is ≤ 2 MB.
- **And** The system uploads the avatar image to AWS S3.
- **And** The system updates the display name and S3 URL in the database.
- **And** The system returns the updated fields to the client and displays a success message.

#### Scenario 2: Profile update failed due to file size exceeding limit (Edge Case)
- **Given** The user is editing their profile.
- **When** The user attempts to upload an avatar image that is 2.5 MB (exceeding the 2 MB limit).
- **And** The user clicks "Save Changes".
- **Then** The system blocks the upload immediately.
- **And** The system displays the error: "Uploaded file size exceeds the 2 MB limit. Please choose another file".
- **And** The user's profile database records remain unchanged.

#### Scenario 3: Profile update failed due to unsupported file format (Edge Case)
- **Given** The user is editing their profile.
- **When** The user selects a `.gif` image or a `.pdf` file.
- **And** The user clicks "Save Changes".
- **Then** The system detects an invalid file format (magic-byte mismatch).
- **And** The system blocks the upload and returns the error: "Unsupported file format. Only JPEG and PNG are allowed".

---

## 📂 PART 2: DOCUMENT MANAGEMENT (F-DOC)

### 7. Document Upload (F-DOC-01)

> [!WARNING]
> Storage limits must be checked strictly. The system verifies that the user's current storage usage (`storage_used`) plus the size of the uploaded file does not exceed the limit specified by their subscription plan (**2 GB Free / 10 GB Premium**). Accepted extensions are **`.pdf`, `.docx`, `.txt`, `.md`** only; the business cap is **50 MB** per file. A **content-hash** check deduplicates uploads — a file whose content hash already exists for the user returns **409 Conflict**.

#### Scenario 1: Successful private document upload within storage quota (Happy Path)
- **Given** The user is active, on the Free plan (limit **2 GB**, currently using 1.0 GB).
- **When** The user uploads `advanced_math.pdf` (10 MB) and selects "Private" visibility.
- **And** The user clicks "Upload".
- **Then** The system verifies the user is active (not `'overlimitstorage'`).
- **And** The system calculates that the combined storage (~1.01 GB) is ≤ 2 GB.
- **And** The system uploads the file to AWS S3.
- **And** The system saves the document record with visibility `'private'` and status `'processing'`.
- **And** The system dispatches the document to the RAG microservice for text extraction and indexing.
- **And** The system adds exactly 10,485,760 bytes (10 MB) to the user's `storage_used` in the database.
- **And** On the `/internal/documents/callback` `SUCCESS` status, the system transitions the document to `'completed'`.
- **And** The system displays the file in the user's personal document library.

#### Scenario 2: Successful public document upload routed to moderation queue (Happy Path)
- **Given** The user is active and has sufficient storage quota.
- **When** The user uploads `lecture_notes.docx` (5 MB), selects "Public" visibility, and clicks "Upload".
- **Then** The system verifies storage quota and uploads the file to AWS S3.
- **And** The system saves the document record with visibility `'public'` and status `'pending'`.
- **And** The system dispatches the document to the RAG microservice for extraction; on the `EXTRACTED` callback the document is enqueued onto the Redis moderation stream (`stream:moderation`).
- **And** The system increases the user's `storage_used` by 5 MB.
- **And** The system displays: "Your document is pending review before it can become public".

#### Scenario 3: Upload blocked due to 'overlimitstorage' account status (Edge Case)
- **Given** The user's account has a status of `'overlimitstorage'`.
- **When** The user attempts to upload any file.
- **Then** The system blocks the upload request immediately.
- **And** The system returns the warning: "Your storage has exceeded the plan limit. Please delete files or upgrade your plan to upload".
- **And** The user can still list and read their existing documents (the lock applies to upload only).

#### Scenario 4: Upload blocked because new file exceeds remaining storage quota (Edge Case)
- **Given** The user is on the Free plan (**2 GB** limit) and has already used 1.97 GB (≈ 30 MB remaining), and the file passes the single-file **50 MB** cap check.
- **When** The user attempts to upload a document of size **40 MB** (under the 50 MB per-file cap, so it reaches the storage-quota check).
- **Then** The system calculates that the combined storage would be ~2.01 GB (exceeding the 2 GB limit).
- **And** The system rejects the upload.
- **And** The system returns the error: "Upload failed: file size exceeds remaining storage quota".

#### Scenario 5: Upload rejected due to single file exceeding the 50 MB cap (Edge Case)
- **Given** The user has ample remaining storage quota.
- **When** The user attempts to upload a file of 60 MB.
- **Then** The system detects the file exceeds the **50 MB** business cap.
- **And** The system rejects the upload with a clean `400 Bad Request` (not a server error).
- **And** The system returns the error: "File size exceeds the 50 MB upload limit".

#### Scenario 6: Upload rejected due to unsupported file format (Edge Case)
- **Given** The user attempts to upload a file with extension `.zip` or `.pptx`.
- **When** The user selects the file and clicks upload.
- **Then** The system checks the file extension.
- **And** The system blocks the upload because it is not one of `.pdf`, `.docx`, `.txt`, `.md`.
- **And** The system returns the error: "Unsupported file format".

#### Scenario 7: Upload rejected due to content-hash duplicate (Edge Case)
- **Given** The user has already uploaded `advanced_math.pdf` (content hash `H`) and it is still present in their library.
- **When** The user attempts to upload the same file again (identical content → same hash `H`).
- **Then** The system computes the content hash of the incoming file and detects a match against an existing document owned by the same user.
- **And** The system returns HTTP **409 Conflict**.
- **And** The system does not create a new document record, upload to S3, or consume additional storage.

---

### 8. Document Tagging (F-DOC-02)

#### Scenario 1: Tagging a document with both existing and new tags (Happy Path)
- **Given** The user is uploading or editing a document.
- **When** The user enters the tags `"LinearAlgebra"` (already exists as a public tag in the database) and `"MidtermExam"` (a new private tag, 11 characters).
- **And** The user saves the document.
- **Then** The system checks the database:
  - Reuses the existing public tag ID for `"LinearAlgebra"`.
  - Creates a new private tag definition record (visibility: private) for `"MidtermExam"` owned by the current user.
- **And** The system writes association records to the `document_tags` mapping table.
- **And** The system displays both tags on the document info card.

#### Scenario 2: Tag creation blocked because length exceeds 100 characters (Edge Case)
- **Given** The user is tagging a document.
- **When** The user inputs a tag whose label is longer than 100 characters.
- **And** The user tries to save the document.
- **Then** The system blocks the request.
- **And** The system displays the validation warning: "Tag length cannot exceed 100 characters".

---

### 9. Personal Library & Storage Access (F-DOC-03)

#### Scenario 1: Successfully accessing active personal document list (Happy Path)
- **Given** The user is logged in with status `'active'`.
- **When** The user opens the "My Documents" page (`GET /api/v1/documents/personal`).
- **Then** The system queries the database for all documents where `user_id` matches the user and `deleted_at` is NULL.
- **And** The system returns and displays all corresponding private, public, pending, and completed documents belonging to the user.

#### Scenario 2: 'overlimitstorage' users can still list and read their own documents (Edge Case / Behavior)
- **Given** The user's account has a status of `'overlimitstorage'`.
- **When** The user opens the "My Documents" page or requests a document preview/download they own.
- **Then** The system allows the request to proceed normally.
- **And** The system returns and displays the user's own documents.
- **And** The storage lock applies to **upload only** — listing, reading, previewing, downloading, chatting, and other features remain available.

#### Scenario 3: Successfully retrieving personal storage usage details (Happy Path)
- **Given** The user is authenticated and active.
- **When** The user requests their storage information (`GET /api/v1/users/storage`).
- **Then** The system queries the database to retrieve the user's `storage_used` (in bytes) and the matching storage plan limit (converting `storage_limit` from GB to bytes).
- **And** The system returns the subscription plan ID, plan name, storage limit, storage used, and remaining storage.

#### Scenario 4: Dynamic storage increment upon successful document upload (Happy Path)
- **Given** The user has a current storage usage of $U$ bytes and a file of size $S$ bytes is uploaded.
- **When** The document upload and processing completes successfully.
- **Then** The system increments the user's `storage_used` to $U + S$ bytes in the database.

#### Scenario 5: Dynamic storage decrement upon document deletion (Happy Path)
- **Given** The user has a current storage usage of $U$ bytes and deletes a document of size $S$ bytes.
- **When** The document soft-deletion is executed successfully.
- **Then** The system decrements the user's `storage_used` to $\max(0, U - S)$ bytes in the database.

---

### 10. Document Preview and Download (F-DOC-04)

> [!IMPORTANT]
> In-browser rendering for PDF/Word previews must complete in ≤ 3.0 seconds. **Guests** previewing a public or shared document see only the first **30%** of the content; the full document requires authentication or ownership. Downloads are served via a **presigned S3 URL** and increment the document's `download_count`.

#### Scenario 1: Guest viewing a public or shared document preview (Happy Path)
- **Given** A document has visibility `'public'` and status `'completed'`, or a valid `link_share` token exists.
- **When** A guest user clicks on the document to preview it (public view, or `GET /api/v1/documents/shared/{token}`).
- **Then** The system renders the document preview in the browser within 3.0 seconds without a full download.
- **And** The guest receives only the **first 30%** of the document content.
- **And** The response data includes the document ID, title, file type, file size in bytes, created timestamp (`created_at`), and description.

#### Scenario 2: Authenticated owner or permitted user viewing the full document (Alternative Path)
- **Given** The authenticated user is the owner of the document, or is viewing a public document while signed in.
- **When** The user opens the document preview (`GET /api/v1/documents/{id}/preview`).
- **Then** The system renders the **full** document content (no 30% restriction).
- **And** The response includes the S3 presigned URL for in-browser rendering.

#### Scenario 3: Download increments the document's download counter (Happy Path)
- **Given** An authenticated user is viewing a document they may download.
- **When** The user clicks "Download" (`GET /api/v1/documents/{id}/download`).
- **Then** The system generates a presigned S3 URL (valid 10 minutes).
- **And** The system atomically increments the document's `download_count` by 1.
- **And** The system returns the presigned URL and document metadata to the client.

#### Scenario 4: Non-owners blocked from accessing private, pending, or rejected files (Edge Case)
- **Given** Document A owned by User X has visibility `'private'`, or status `'pending'` / `'rejected'`.
- **When** User Y (who is not the owner or an administrator) attempts to view or download Document A.
- **Then** The system denies access.
- **And** The system returns HTTP `403 Forbidden` or a "Document not found/unauthorized" message.

---

### 11. Search Execution (F-DOC-05)

> [!NOTE]
> Public search (`GET /api/v1/documents/search?keyword=`) matches **title + description only** via SQL `ILIKE` (case-insensitive). It returns only documents that are **public + `completed` + not soft-deleted**. Full semantic search over document content happens inside the **RAG chat** (BM25 + pgvector), not this endpoint.

#### Scenario 1: Fast public keyword search execution (Happy Path)
- **Given** A user or guest is on the Search page.
- **When** The user inputs the keyword `"calculus"` and presses enter.
- **Then** The system runs a case-insensitive `ILIKE` match against the `title` and `description` columns.
- **And** The system filters the results to documents with visibility `'public'`, status `'completed'`, and `deleted_at` NULL.
- **And** The system returns matching results in less than 1.5 seconds.
- **And** The response is paginated and excludes private, pending, rejected, deleted, and failed documents.

#### Scenario 2: Search returns no matches for an unknown keyword (Alternative Path)
- **Given** No public `completed` document whose title or description contains `"zzznonexistent"`.
- **When** The user searches for `"zzznonexistent"`.
- **Then** The system returns an empty paginated result set with `200 OK`.
- **And** The response time remains under 1.5 seconds.

---

### 12. Share Link Generation (F-DOC-06)

#### Scenario 1: Generating a read-only share link for a document (Happy Path)
- **Given** The user is the owner of the document `"Term Paper"`.
- **When** The user selects "Generate Share Link" from the settings menu (`POST /api/v1/documents/{id}/share`).
- **Then** The system mints a unique share token and stores it in the document's `link_share` column.
- **And** The system returns a public share URL (e.g., `aistudyhub.com/shared/{token}`).
- **And** Anyone accessing this URL (`GET /api/v1/documents/shared/{token}`, `permitAll`) can preview the document in read-only mode, regardless of the document's original visibility.

#### Scenario 2: Share link cleared on soft-delete (Edge Case)
- **Given** The owner has generated a share link for a document.
- **When** The owner soft-deletes the document.
- **Then** The system sets the document's `link_share` to NULL.
- **And** The previously shared URL no longer resolves to the document.

---

### 13. Document Metadata Modification (F-DOC-07)

#### Scenario 1: Changing document visibility from private to public triggers moderation (Happy Path)
- **Given** The user owns a document that is currently `'private'` and `'completed'` (already indexed).
- **When** The user edits the document, changes visibility to "Public", and clicks save (`PUT /api/v1/documents/{id}`).
- **Then** The system updates visibility to `'public'`.
- **And** The system sets the document status to `'pending'` and enqueues it onto the Redis moderation stream (`stream:moderation`) without a new `/extract` round-trip.
- **And** The system displays: "Your changes have been saved. The document has been submitted for review before it can become public".

---

### 14. Document Soft-Deletion (F-DOC-08)

> [!NOTE]
> Soft-delete moves a document to the **Trash**: status becomes `'DELETED'`, `deleted_at` is set, `link_share` is nulled, `status_before_deletion` is preserved, and the file size is subtracted from `storage_used`. The S3 object and database row are **not** immediately purged — retention is 30 days, after which `DocumentPurgeScheduler` hard-deletes them.

#### Scenario 1: Soft-deleting a file and updating user storage (Happy Path)
- **Given** The user owns `slides.pdf` (20 MB) and their current storage usage is 1.2 GB.
- **When** The user clicks the "Delete" button (`DELETE /api/v1/documents/{id}`).
- **Then** The system displays a Confirmation Modal.
- **And** When the user confirms the action.
- **And** The system writes the current timestamp to the document's `deleted_at` field.
- **And** The system preserves the previous status in `status_before_deletion`.
- **And** The system sets the document status to `'DELETED'`.
- **And** The system sets `link_share` to NULL.
- **And** The system subtracts 20 MB from the user's `storage_used` and updates the database record.
- **And** The document moves to the Trash (`GET /api/v1/documents/trash`) and is hidden from the personal library and public search.

#### Scenario 2: Soft-deleted documents are retained, not purged (Behavior)
- **Given** A document was soft-deleted 5 days ago.
- **When** The owner opens the Trash.
- **Then** The document is still listed in the Trash with its `deleted_at` timestamp and `status_before_deletion`.
- **And** The S3 object and database row still exist (no immediate purge).
- **And** The owner may restore it (see F-DOC-09) until the 30-day retention window expires.

---

### 15. Restore Document from Trash (F-DOC-09)

> [!NOTE]
> An owner can restore a soft-deleted document via `POST /api/v1/documents/{id}/restore` within the **30-day** retention window. Restoration reverts the document to its `status_before_deletion`, re-adds its size to `storage_used`, and mints a fresh `link_share` only if the document is both `'public'` and `'completed'`. No RAG re-indexing occurs on restore.

#### Scenario 1: Owner restores a soft-deleted document within the retention window (Happy Path)
- **Given** The owner soft-deleted `notes.pdf` (10 MB) 5 days ago; `status_before_deletion` is `'completed'`, visibility is `'private'`.
- **When** The owner clicks "Restore" in the Trash (`POST /api/v1/documents/{id}/restore`).
- **Then** The system sets the document status back to its `status_before_deletion` (`'completed'`).
- **And** The system clears `deleted_at`.
- **And** The system re-adds 10 MB to the owner's `storage_used`.
- **And** The system does **not** re-run RAG extraction/indexing (vectors are preserved).
- **And** The document reappears in the personal library.
- **And** The system returns `200 OK`.

#### Scenario 2: Restoring a public completed document mints a fresh share link (Happy Path)
- **Given** A document with visibility `'public'` and `status_before_deletion` `'completed'` was soft-deleted.
- **When** The owner restores it.
- **Then** The system restores the status to `'completed'`.
- **And** The system generates a **fresh** `link_share` token (the old one was nulled on deletion).
- **And** The document is again eligible for public search and shared preview.

#### Scenario 3: Restore refused due to insufficient storage (Edge Case)
- **Given** The owner's `storage_used` plus the document size would exceed the current plan limit.
- **When** The owner attempts to restore the document.
- **Then** The system rejects the restore.
- **And** The system returns an error indicating insufficient storage, and the document remains in the Trash.

#### Scenario 4: Admin-deleted or hard-purged document cannot be restored (Edge Case)
- **Given** A document was hard-purged by an administrator (e.g., rejected and purged from RAG) or removed via a violation resolution.
- **When** The owner attempts `POST /api/v1/documents/{id}/restore`.
- **Then** The system cannot find a restorable (soft-deleted) record.
- **And** The system returns `404 Not Found` (or `403 Forbidden`) and performs no restore.

#### Scenario 5: Document past the 30-day retention window is hard-deleted and not restorable (Edge Case)
- **Given** A document was soft-deleted more than 30 days ago and `DocumentPurgeScheduler` (runs 03:00 daily) has purged it.
- **When** The owner attempts to restore it.
- **Then** The system finds no record (S3 object, database row, and RAG vectors all removed).
- **And** The system returns `404 Not Found` and the document is permanently gone.

---

### 16. Bookmark / Save Document (F-DOC-10)

> [!NOTE]
> Authenticated users can bookmark any document via `POST /api/v1/documents/{id}/save`, remove a bookmark via `DELETE /api/v1/documents/{id}/unsave`, and list their saved documents via `GET /api/v1/documents/saved`. A bookmark is **unique per user + document**.

#### Scenario 1: Saving a document bookmark (Happy Path)
- **Given** An authenticated user is viewing a document they do not yet have bookmarked.
- **When** The user clicks "Save" (`POST /api/v1/documents/{id}/save`).
- **Then** The system creates a row in `saved_documents` linking the user and the document.
- **And** The system returns `200 OK` (or `201 Created`) with a success message.
- **And** The document appears in the user's Saved list.

#### Scenario 2: Re-saving an already-saved document is idempotent (Edge Case)
- **Given** The user has already bookmarked the document.
- **When** The user clicks "Save" again.
- **Then** The system detects the existing (user, document) bookmark.
- **And** The system performs no duplicate insert.
- **And** The system returns a success response without error (idempotent).

#### Scenario 3: Removing a bookmark (Happy Path)
- **Given** The user has a document bookmarked.
- **When** The user clicks "Unsave" (`DELETE /api/v1/documents/{id}/unsave`).
- **Then** The system deletes the matching row from `saved_documents`.
- **And** The system returns `200 OK`.
- **And** The document no longer appears in the user's Saved list.

#### Scenario 4: Listing saved documents (Happy Path)
- **Given** The authenticated user has previously bookmarked several documents.
- **When** The user opens the Saved page (`GET /api/v1/documents/saved`).
- **Then** The system queries `saved_documents` for the current user.
- **And** The system returns the paginated list of bookmarked documents.
- **And** The response returns `200 OK`.

#### Scenario 5: Bookmark actions blocked for guests (Edge Case / Security)
- **Given** An unauthenticated guest visitor.
- **When** The visitor attempts to call `POST /api/v1/documents/{id}/save` or `GET /api/v1/documents/saved`.
- **Then** The security filter chain blocks the request.
- **And** The system returns `401 Unauthorized`.

---

### 17. Onboarding Survey & Recommendations (F-DOC-11)

> [!NOTE]
> To personalize the experience, users select **1–3 public tags** as preferences (stored in `UserEntity.preferred_tag_ids`). The system then recommends **paginated public `completed` documents** matching these tags via `GET /api/v1/documents/recommendations`, sorted by: (1) match count descending, (2) average rating descending (non-reviewed last), (3) creation date descending.

#### Scenario 1: User saves preferred tags successfully (Happy Path)
- **Given** The user is authenticated.
- **When** The user submits a POST request to `/api/v1/users/preferred-tags` with a list of 1 to 3 public tag IDs (e.g., `[1, 2, 3]`).
- **Then** The system saves the tag IDs as the user's `preferred_tag_ids` in the database.
- **And** The system returns HTTP `200 OK` with a success message.

#### Scenario 2: Saving preferred tags failed due to limit validation (Edge Case)
- **Given** The user is authenticated.
- **When** The user attempts to submit a list containing more than 3 tag IDs (e.g., `[1, 2, 3, 4]`).
- **Then** The system validation blocks the request.
- **And** The system returns HTTP `400 Bad Request` with an error message indicating a maximum of 3 tags.

#### Scenario 3: Retrieving recommended documents sorted by preference ranking (Happy Path)
- **Given** The user is authenticated and has saved preferred tag IDs (e.g., `[101, 102]`).
- **And** The database contains public `completed` documents matching these preferred tags.
- **When** The user requests recommended documents (`GET /api/v1/documents/recommendations`).
- **Then** The system retrieves the matching public, non-deleted, `completed` documents.
- **And** The system sorts them by match count (descending), average rating (descending), and creation date (descending).
- **And** The system returns a **paginated** result set.
- **And** The response returns HTTP `200 OK`.

#### Scenario 4: Retrieving recommended documents with no preferred tags (Alternative Path)
- **Given** The user is authenticated but has not completed the onboarding survey (`preferred_tag_ids` is empty).
- **When** The user requests recommended documents.
- **Then** The system returns an empty list immediately.
- **And** The response returns HTTP `200 OK`.

#### Scenario 5: Access to recommendations blocked for guests (Edge Case / Security)
- **Given** An unauthenticated guest visitor.
- **When** The visitor attempts to call `GET /api/v1/documents/recommendations`.
- **Then** The security chain blocks the request.
- **And** The system returns HTTP `401 Unauthorized`.

---

## 🤝 PART 3: SOCIAL LEARNING & INTERACTION (F-SOC)

### 18. Review and Rating (F-SOC-01)

#### Scenario 1: Rating and commenting on a public document (Happy Path)
- **Given** An authenticated user is viewing an approved (status `'completed'`, visibility `'public'`) document.
- **When** The user selects a `5` star rating and enters the comment `"Great notes, thanks!"` (`POST /api/v1/documents/{id}/reviews`).
- **Then** The system saves the review in the database.
- **And** The system recalculates the average rating of the document.
- **And** The system updates the average rating on the document record.
- **And** The system displays the review and the updated rating score on the page.

#### Scenario 2: Blocked rating submission due to score out of bounds (Edge Case)
- **Given** A user attempts to bypass the client UI and submits a rating score of `0` or `6`.
- **When** The server receives the request.
- **Then** Server validation detects the rating score is out of the 1 to 5 range.
- **And** The system rejects the request and returns a `400 Bad Request` validation error.

---

### 19. Abuse & Content Reporting (F-SOC-02)

#### Scenario 1: Submitting an abuse report (Happy Path)
- **Given** An authenticated user is viewing a public document.
- **When** The user clicks "Report", selects "Copyright Infringement", adds details `"Copied textbook content"`, and clicks submit (`POST /api/v1/documents/{id}/reports`).
- **Then** The system saves the report record with a default status of `'pending'`.
- **And** The system surfaces the report on the Admin Dashboard.
- **And** The system displays: "Thank you. Your report has been submitted for administrator review".

---

## 🤖 PART 4: AI ASSISTANT & STUDY MATERIALS (F-AI)

### 20. Multi-Document Contextual Chat (F-AI-01)

> [!NOTE]
> The AI Guard intercepts chat requests and enforces the plan's **daily AI quota** (shared across chat + quiz + flashcard). Quota is tracked atomically in Redis key `user:ai_limit:{userId}:{yyyy-MM-dd}` via `INCR` with a 24 h TTL set on first use. Overflow returns **HTTP 429**, makes **no LLM call**, and does **not** increment the counter.

#### Scenario 1: Multi-document chat within quota (Happy Path)
- **Given** The user is Premium, and has used 10 out of their 60 daily AI requests.
- **When** The user selects `macroeconomics.pdf` and `exam_notes.docx` and enters the query `"Compare demand-pull and cost-push inflation?"` (`POST /api/v1/chat`, no session ID provided).
- **Then** The AI Guard middleware verifies the Redis counter is within quota.
- **And** The system initializes a new Chat Session.
- **And** The RAG microservice generates the query embedding and runs hybrid retrieval (BM25 + pgvector, reranked) restricted to the selected documents.
- **And** The system returns the answer, citations, and file references within 5.0 seconds.
- **And** The system saves the query, response, and citations in the messages table.
- **And** The system atomically increments the user's daily AI counter by 1.

#### Scenario 2: Request blocked due to daily quota exceeded → HTTP 429 (Edge Case)
- **Given** A Free plan user has already used all 15 daily AI requests today.
- **When** The user submits a new chat query.
- **Then** The AI Guard middleware detects the daily quota has been reached.
- **And** The system blocks the request **without calling the LLM/RAG API**.
- **And** The system returns **HTTP `429 Too Many Requests`** with a message such as "Daily AI request limit reached. Please upgrade to Premium for more requests".
- **And** The Redis counter is **not** incremented.

---

### 21. Single Document Contextual Chat (F-AI-02)

#### Scenario 1: Requesting a summary of an open document (Happy Path)
- **Given** The user is viewing a document and is within their daily AI quota.
- **When** The user clicks "Summarize Document".
- **Then** The system verifies the user's daily AI quota.
- **And** The RAG microservice restricts the retrieval context to the current document ID.
- **And** The system returns the generated summary on the chat interface within 5.0 seconds.
- **And** The system atomically increments the user's daily AI counter by 1.

#### Scenario 2: Single-document chat blocked when quota exceeded → HTTP 429 (Edge Case)
- **Given** The user has exhausted their daily AI quota.
- **When** The user requests a single-document summary.
- **Then** The AI Guard blocks the request.
- **And** The system returns `429 Too Many Requests`, makes no LLM call, and does not increment the counter.

---

### 22. Chat Session Management (F-AI-03)

#### Scenario 1: Viewing and renaming active chat sessions (Happy Path)
- **Given** The user has active chat histories in the database.
- **When** The user opens the Chat History sidebar (`GET /api/v1/chat/sessions`).
- **Then** The system displays all chat sessions belonging to the user where `deleted_at` is NULL.
- **And** When the user renames a session to `"Macroeconomics Review"` (`PATCH /api/v1/chat/sessions/{id}`), the system updates the title in the database and reflects the change in the UI.

#### Scenario 2: Deleting a chat session (Happy Path)
- **Given** The user is looking at their chat history.
- **When** The user clicks the "Delete" icon on a chat session (`DELETE /api/v1/chat/sessions/{id}`).
- **Then** The system sets the current timestamp in the session's `deleted_at` field in the database.
- **And** The system removes the session from the visible chat history list.

---

### 23. AI Study-Material Generation - Quiz & Flashcard (F-AI-04)

> [!NOTE]
> Study-material generation (`POST /api/v1/study-materials/quiz`, `POST /api/v1/study-materials/flashcard`) is document-scoped and **counts against the same daily AI quota** as chat. When the source document is too short or lacks extractable content, the RAG service returns a **refusal**: HTTP `200 OK` with an **empty list** and a human-readable reason in the `message` field (no error thrown). Quota overflow returns `429` with no generation and no increment.

#### Scenario 1: Generating a quiz from a document (Happy Path)
- **Given** The user is within their daily AI quota and owns/selects a sufficiently long document.
- **When** The user requests quiz generation (`POST /api/v1/study-materials/quiz` for that document).
- **Then** The AI Guard verifies the daily quota.
- **And** The RAG microservice generates quiz items scoped to the document.
- **And** The system returns `200 OK` with the generated quiz list **before** incrementing the quota counter.
- **And** The system atomically increments the shared daily AI counter by 1.

#### Scenario 2: Generating flashcards from a document (Happy Path)
- **Given** The user is within their daily AI quota and selects a sufficiently long document.
- **When** The user requests flashcard generation (`POST /api/v1/study-materials/flashcard`).
- **Then** The AI Guard verifies the daily quota.
- **And** The RAG microservice generates flashcards scoped to the document.
- **And** The system returns `200 OK` with the generated flashcards, then increments the shared daily AI counter by 1.

#### Scenario 3: Refusal when the document is too short (Alternative Path)
- **Given** The user selects a document that is too short to yield meaningful study material.
- **When** The user requests quiz or flashcard generation.
- **Then** The RAG microservice declines to generate.
- **And** The system returns **HTTP `200 OK`** with an **empty list** and a `message` such as "The document is too short to generate study materials".
- **And** No exception is thrown to the client.

#### Scenario 4: Study-material generation blocked when quota exceeded → HTTP 429 (Edge Case)
- **Given** The user has exhausted their daily AI quota.
- **When** The user requests quiz or flashcard generation.
- **Then** The AI Guard blocks the request.
- **And** The system returns `429 Too Many Requests`, makes no LLM call, and does not increment the counter.

---

## 🛡️ PART 5: CONTENT MODERATION (F-MOD)

### 24. Automated Moderation via OpenAI Moderation API (F-MOD-01)

> [!NOTE]
> Public documents are triaged **automatically**. The moderation consumer (`moderation-cg`) reads text chunks (read-only) and extracts embedded images from the S3 original, then calls the **OpenAI Moderation API** (`omni-moderation-latest`). The **maximum category score** across all text chunks and images decides the outcome: **≥ 0.80 → auto-reject**, **< 0.40 → auto-approve (only if images were checked)**, **0.40–0.80 → stays `PENDING` for manual admin review**. Image-flow failure defers to `PENDING` (no text-only auto-approve). Failed text-moderation calls are retried; after **5** attempts the message goes to the DLQ. The run is **idempotent**: a no-op when the document status is not `PENDING`.

#### Scenario 1: Auto-reject when the max moderation score is ≥ 0.80 (Happy Path)
- **Given** A public upload has reached `EXTRACTED` and is enqueued on `stream:moderation` with status `'pending'`.
- **When** The moderation consumer processes the document and the OpenAI Moderation API returns a maximum category score of `0.92`.
- **Then** The system auto-rejects the document (`rejectDocument`).
- **And** The document status becomes `'rejected'` with a generated rejection reason.
- **And** The system triggers a `DELETE /documents/{id}` call to the RAG service to purge its chunks (`@Async deleteVectors`).
- **And** The stream message is ACKed.

#### Scenario 2: Auto-approve when the max moderation score is < 0.40 and images were checked (Happy Path)
- **Given** A public upload is `PENDING` and its embedded images were successfully extracted and moderated.
- **When** The OpenAI Moderation API returns a maximum category score of `0.12`.
- **Then** The system auto-approves the document (`approveDocument`).
- **And** The system sets visibility to `'public'` and triggers `POST /index` to embed the pending chunks.
- **And** On the RAG `SUCCESS` callback the document transitions to `'completed'`.
- **And** The document becomes visible in public search.

#### Scenario 3: Score between 0.40 and 0.80 stays PENDING for manual admin review (Edge Case)
- **Given** A public upload is `PENDING`.
- **When** The OpenAI Moderation API returns a maximum category score of `0.55`.
- **Then** The system leaves the document in status `'pending'`.
- **And** The document remains on the admin moderation queue for manual review.
- **And** The stream message is ACKed (no auto decision).

#### Scenario 4: Image-flow failure defers to PENDING (no text-only auto-approve) (Edge Case)
- **Given** A public upload is `PENDING` and its text score is `0.10` (would auto-approve), but image extraction/moderation fails.
- **When** The moderation consumer runs.
- **Then** The system does **not** auto-approve (auto-approve requires `imagesChecked`).
- **And** The system leaves the document in `'pending'` for manual admin review.
- **And** The stream message is ACKed.

#### Scenario 5: Idempotent re-run is a no-op when the document is no longer PENDING (Edge Case)
- **Given** A document has already been auto-approved (status `'completed'`) and a duplicate moderation message is redelivered.
- **When** The moderation consumer processes it again.
- **Then** The system detects the status is not `'pending'`.
- **And** The system performs no moderation action (idempotent no-op).
- **And** The stream message is ACKed.

#### Scenario 6: Repeated text-moderation failures move the message to the DLQ (Edge Case)
- **Given** The OpenAI Moderation API is unreachable for text moderation.
- **When** The consumer fails to process the message.
- **Then** The message remains unacked and is retried.
- **And** After **5** failed attempts the message is moved to the Dead-Letter Queue.
- **And** The document stays in `'pending'` for manual review.

---

### 25. Manual Moderation Override (F-MOD-02)

#### Scenario 1: Admin manually approves a document left in PENDING (Happy Path)
- **Given** A public document is stuck in `'pending'` (e.g., a 0.40–0.80 score or image-flow deferral).
- **When** The admin clicks "Approve" (`POST /api/v1/admin/documents/{id}/approve`).
- **Then** The system approves the document following the same flow as auto-approve (visibility `'public'` + index + `SUCCESS` → `'completed'`).
- **And** The document becomes publicly searchable.

#### Scenario 2: Admin manually rejects a document left in PENDING (Happy Path)
- **Given** A public document is in `'pending'`.
- **When** The admin clicks "Reject" with reason `"File contains advertisements"` (`POST /api/v1/admin/documents/{id}/reject`, body `rejectionReason`).
- **Then** The system sets the status to `'rejected'` and stores the rejection reason.
- **And** The system purges the document's RAG chunks.
- **And** The document remains hidden from the public library.

---

## 🛠️ PART 6: ADMIN DASHBOARD (F-ADM)

### 26. Content Moderation UI (F-ADM-01)

#### Scenario 1: Admin approving a pending document indexes it and sets COMPLETED (Happy Path)
- **Given** The administrator is on the Admin Moderation Queue, and document `math_101.pdf` is `'pending'` with visibility `'public'`.
- **When** The admin clicks the "Approve" button (`POST /api/v1/admin/documents/{id}/approve`).
- **Then** The system sets visibility to `'public'` and triggers `POST /index` to embed the pending chunks.
- **And** On the RAG `SUCCESS` callback the document status transitions to `'completed'`.
- **And** The document becomes visible in public search results.
- **And** The system generates a `DOCUMENT_APPROVED` notification for the owner.

#### Scenario 2: Admin rejecting a pending document purges its RAG index (Happy Path)
- **Given** The administrator is on the Admin Moderation Queue.
- **When** The admin clicks "Reject", inputs the reason `"File contains advertisements"`, and submits (`POST /api/v1/admin/documents/{id}/reject`).
- **Then** The system sets the document status to `'rejected'` and saves the rejection reason.
- **And** The system calls `DELETE /documents/{id}` on the RAG service to purge the document's vector chunks (`@Async deleteVectors`).
- **And** The system creates a `DOCUMENT_REJECTED` notification for the owner with the reason.
- **And** The document remains hidden from the public library.

---

### 27. Violation Review (F-ADM-02)

#### Scenario 1: Resolving a report and deleting the document (Happy Path)
- **Given** The admin is reviewing a pending report for `cheat_sheet.pdf` uploaded by User A.
- **When** The admin clicks "Confirm Report" (`POST /api/v1/admin/reports/{id}/resolve`).
- **Then** The system updates the report status to `'resolved'`.
- **And** The system soft-deletes the document (status `'deleted'`, `deleted_at` set, `link_share` nulled, storage subtracted).
- **And** The system logs a violation entry against User A's profile.
- **And** The system sends an automated `DOCUMENT_VIOLATION_DELETED` warning notification to User A.

#### Scenario 2: Dismissing a report (Alternative Path)
- **Given** The admin is reviewing an abuse report.
- **When** The admin clicks "Dismiss Report" (`POST /api/v1/admin/reports/{id}/reject`).
- **Then** The system updates the report status to `'rejected'`.
- **And** The document remains `'completed'` and `'public'`.

---

### 28. Account Warnings & Sanctions (F-ADM-03)

> [!CAUTION]
> When an administrator bans an account, the system immediately adds the user's active access tokens to the Redis Blacklist to terminate their session, and invalidates their refresh tokens.

#### Scenario 1: Banning a user account (Happy Path)
- **Given** The admin is on the User Management page.
- **When** The admin selects User B and clicks the "Ban Account" button (`POST /api/v1/admin/users/{id}/ban`).
- **Then** The system updates User B's status to `'banned'` in the database.
- **And** The system adds User B's active access tokens to the Redis blacklist and invalidates their refresh tokens.
- **And** All subsequent API requests using User B's active session tokens are rejected immediately.
- **And** The system sends an `ACCOUNT_BANNED` notification to User B.

#### Scenario 2: Warning a user account (Happy Path)
- **Given** The admin is on the User Management page.
- **When** The admin selects User B, enters a reason, and clicks "Warn" (`POST /api/v1/admin/users/{id}/warn`, body `reason`).
- **Then** The system records the warning.
- **And** The system sends an `ACCOUNT_WARNING` notification containing the reason to User B.
- **And** User B's account status remains unchanged (still `'active'`).

#### Scenario 3: Reactivating a previously banned user (Alternative Path)
- **Given** User B's account is `'banned'`.
- **When** The admin clicks "Reactivate" (`POST /api/v1/admin/users/{id}/reactivate`).
- **Then** The system sets User B's status back to `'active'`.
- **And** The system sends an `ACCOUNT_ACTIVATED` notification to User B.

---

### 29. Aggregation and Stats (F-ADM-04)

#### Scenario 1: Loading metrics on the Admin Dashboard (Happy Path)
- **Given** The administrator opens the Admin Dashboard (`GET /api/v1/admin/dashboard/stats`).
- **When** The page finishes loading.
- **Then** The system executes SQL aggregation commands to retrieve:
  - Total signups grouped by time.
  - Total count of successful document uploads.
  - Combined storage size of files stored on AWS S3.
  - Sum of successful invoice revenues for the current month.
- **And** The system renders these metrics on charts and tables in less than 2.0 seconds.

---

### 30. Public Tag Management (F-ADM-05)

> [!NOTE]
> Creating a public tag is restricted to administrators. When a public tag is created, the system automatically finds and merges all existing user private tags with the same label to maintain consistency.

#### Scenario 1: Admin successfully creates a new public tag (Happy Path)
- **Given** The user is authenticated as an Administrator.
- **When** The admin sends a POST request to `/api/v1/admin/tags` with a valid tag label `"Calculus"`.
- **Then** The system verifies that the tag `"Calculus"` does not already exist as a public tag.
- **And** The system creates and saves a new public tag in the database.
- **And** The system evicts the public-tags cache (30 m TTL) and the trending-documents cache (10 m TTL).
- **And** The system returns HTTP `201 Created` with the newly created public tag details.

#### Scenario 2: Admin creates a public tag that merges existing private tags (Alternative Path)
- **Given** The user is authenticated as an Administrator.
- **And** There are several private tags created by users with the label `"Physics"`.
- **When** The admin sends a POST request to `/api/v1/admin/tags` with the label `"Physics"`.
- **Then** The system creates a new public tag with the label `"Physics"`.
- **And** The system retrieves all matching private tags with the label `"Physics"`.
- **And** The system reassigns all document mappings pointing to these private tags to the new public tag.
- **And** The system deletes the old private tag records and their mappings from the database.
- **And** The system evicts the cache.
- **And** The system returns HTTP `201 Created` with the public tag details.

#### Scenario 3: Tag creation rejected due to validation failure (Edge Case)
- **Given** The user is authenticated as an Administrator.
- **When** The admin attempts to create a public tag with an empty label or a label exceeding 100 characters.
- **Then** The system validation blocks the request.
- **And** The system returns HTTP `400 Bad Request` with a validation error message.

#### Scenario 4: Tag creation blocked for non-admin users (Edge Case / Authorization)
- **Given** A user authenticated as a regular user or an unauthenticated guest visitor.
- **When** The user sends a POST request to `/api/v1/admin/tags` with the label `"Chemistry"`.
- **Then** The security filter chain intercepts the request (path-based authz on `/admin/**`).
- **And** The system blocks access and returns HTTP `403 Forbidden` (for an authenticated customer) or `401 Unauthorized` (for a guest).

---

## 💳 PART 7: SUBSCRIPTION & PAYMENT (F-MON)

### 31. Subscription Purchase Flow - VNPay (F-MON-01)

> [!NOTE]
> Payment is processed exclusively via **VNPay** (HMAC-SHA512 signing through `VNPayUtil`). `POST /api/v1/payments/create-payment` creates a pending invoice and returns a **VNPay payment URL**. There is no MoMo, VietQR, or dynamic-QR flow.

#### Scenario 1: Initiating an upgrade and obtaining the VNPay payment URL (Happy Path)
- **Given** The user is on the Free plan and is on the Pricing Page.
- **When** The user selects the Premium plan and clicks "Upgrade Now" (`POST /api/v1/payments/create-payment`).
- **Then** The system creates an invoice in the database with status `'pending'`, the target subscription plan, and the price.
- **And** The system builds a VNPay payment request signed with HMAC-SHA512.
- **And** The system returns a **VNPay sandbox/payment URL** redirecting the user to the VNPay checkout.
- **And** The system displays the payment URL and instructions containing the unique invoice reference.

#### Scenario 2: VNPay browser callback redirects to the frontend (Happy Path)
- **Given** The user has completed (or cancelled) payment on the VNPay checkout page.
- **When** VNPay redirects the user's browser to `GET /api/v1/payments/vnpay-callback`.
- **Then** The system returns an HTTP **302** redirect to the appropriate frontend page (success or failure).
- **And** The actual plan upgrade is **not** performed here — it is performed by the server-to-server IPN (see F-MON-02).

---

### 32. VNPay IPN Webhook Automation (F-MON-02)

> [!IMPORTANT]
> Plan upgrade, expiration extension, and storage-limit unlock must execute within a **single atomic database transaction** to prevent inconsistencies on payment failure. The server-to-server IPN endpoint (`GET /api/v1/payments/vnpay-ipn`) verifies the **HMAC-SHA512 signature** of the payload. An invalid signature returns the IPN response `RspCode=97`; all other processing returns `RspCode=00`.

#### Scenario 1: Handling a successful VNPay IPN for a normal user (Happy Path)
- **Given** An invoice with status `'pending'` exists in the database.
- **When** VNPay sends a server-to-server IPN request to `GET /api/v1/payments/vnpay-ipn`.
- **Then** The system verifies the payload's **HMAC-SHA512 signature** against the configured VNPay hash secret.
- **And** The system runs an atomic transaction to:
  - Update the invoice status to `'success'`.
  - Set the user's plan to Premium.
  - Extend `plan_expires_at` to exactly **30 days** from the current timestamp.
- **And** The system returns the IPN acknowledgment with `RspCode=00` to VNPay.

#### Scenario 2: Successful IPN unlocks a storage-locked user (Happy Path / Unlock)
- **Given** A user with status `'overlimitstorage'` has a pending invoice.
- **When** The system receives a valid VNPay IPN confirming payment.
- **Then** The system verifies the signature.
- **And** The system executes the atomic database transaction:
  - Updates the invoice status to `'success'`.
  - Sets the plan to Premium (expanding the storage limit to **10 GB**).
  - Resets the user status to `'active'`, unlocking storage.
  - Sets `plan_expires_at` to current time + 30 days.
- **And** The system returns the IPN acknowledgment with `RspCode=00`.

#### Scenario 3: Rejecting an IPN with a bad signature (Edge Case / Security)
- **Given** An external request reaches the IPN endpoint.
- **When** The payload signature does not match the HMAC-SHA512 digest computed with the configured hash secret.
- **Then** The system detects the signature mismatch.
- **And** The system makes **no changes** to the database.
- **And** The system returns the IPN response with **`RspCode=97`** (invalid signature).

#### Scenario 4: IPN for an unknown or already-processed invoice (Edge Case)
- **Given** The IPN references an invoice that does not exist or is already `'success'`.
- **When** The system receives the (validly signed) IPN.
- **Then** The system performs no duplicate upgrade.
- **And** The system returns the IPN acknowledgment with `RspCode=00` (to stop VNPay retries) without mutating state.

---

### 33. Daily AI Quota (Redis) (F-MON-03)

> [!NOTE]
> The daily AI quota is a **shared counter** across chat, quiz, and flashcard. It is stored in Redis key `user:ai_limit:{userId}:{yyyy-MM-dd}` and incremented atomically with `INCR`; a 24 h TTL is set on first use. Reset is **lazy / Redis-based — there is no midnight cron job**: each new calendar day simply has a fresh key.

#### Scenario 1: First AI request of the day initializes the Redis key (Happy Path)
- **Given** The user has not sent any AI requests (chat, quiz, or flashcard) today.
- **When** The user submits their first request.
- **Then** The system checks Redis for `user:ai_limit:{userId}:{today}` and finds it does not exist.
- **And** The system uses the atomic `INCR` command to create the key with value `1`.
- **And** The system sets a **24-hour** TTL on the key.
- **And** The system allows the request to proceed to the LLM/RAG API.

#### Scenario 2: Request within limit increments the counter (Happy Path)
- **Given** The user has already used 5 requests, so the Redis key value is `5`.
- **When** The user submits a new chat, quiz, or flashcard request.
- **Then** The system increments the Redis counter to `6`.
- **And** The system verifies `6` is ≤ the plan limit (e.g., 15 for Free).
- **And** The system allows the request to proceed.

#### Scenario 3: Request blocked when limit is exceeded → HTTP 429 (Edge Case)
- **Given** The user's Redis counter has reached the plan limit (e.g., `15` for Free).
- **When** The user submits another AI request.
- **Then** The system reads the counter and blocks the request.
- **And** The system returns **HTTP `429 Too Many Requests`** without calling the LLM/RAG API.
- **And** The counter is **not** incremented.

#### Scenario 4: New calendar day starts a fresh quota (Behavior)
- **Given** The user exhausted their quota yesterday (key `user:ai_limit:{userId}:{yesterday}` = 15).
- **When** The user makes their first AI request today.
- **Then** The system looks up `user:ai_limit:{userId}:{today}`, which does not exist.
- **And** The system initializes the new key to `1` with a 24 h TTL.
- **And** The request proceeds (no cron-based reset required).

---

### 34. Subscription Expiration - Scheduled & Lazy Downgrade (F-MON-04)

> [!NOTE]
> Expiration uses a **scheduled + lazy** combination. `PlanExpirationScheduler` (08:00 daily) notifies users whose plan expires within 3 days (`PLAN_EXPIRING`); `PlanDowngradeScheduler` (08:00 daily) proactively downgrades expired premium users to Free. A lazy check on every request also catches expirations missed by the schedulers.

#### Scenario 1: Request from active Premium user (Happy Path)
- **Given** The user is Premium and `plan_expires_at` is in the future.
- **When** The user makes an API request.
- **Then** The system verifies the current time is less than `plan_expires_at`.
- **And** The system allows the request to proceed without changing plan details.

#### Scenario 2: Lazy downgrade - current storage under Free limit (Happy Path / Downgrade)
- **Given** The user is Premium, `plan_expires_at` has passed, and their current storage usage is 1.2 GB.
- **When** The user makes an API request.
- **Then** The system detects that the current time exceeds `plan_expires_at`.
- **And** The system downgrades the user's plan to `'Free'` in the database.
- **And** The system sets `plan_expires_at` to NULL.
- **And** The system compares the storage usage (1.2 GB) against the Free plan limit (**2 GB**).
- **And** The system confirms it is within limits and keeps the user status as `'active'`.
- **And** The system executes the original API request.

#### Scenario 3: Lazy downgrade - current storage over Free limit triggers storage lock (Edge Case)
- **Given** The user is Premium, `plan_expires_at` has passed, and their current storage usage is 3.0 GB.
- **When** The user makes an API request.
- **Then** The system detects expiration.
- **And** The system downgrades the user's plan to `'Free'` and sets `plan_expires_at` to NULL.
- **And** The system compares the storage usage (3.0 GB) against the Free plan limit (**2 GB**).
- **And** The system detects that storage exceeds the limit.
- **And** The system updates the user's status to `'overlimitstorage'` in the database.
- **And** The user can still list/read their own documents but is blocked from uploading.

#### Scenario 4: Scheduled expiry notification 3 days before expiration (Happy Path)
- **Given** A Premium user's `plan_expires_at` is within 3 days from now.
- **When** `PlanExpirationScheduler` runs at 08:00.
- **Then** The system sends a `PLAN_EXPIRING` notification to the user.
- **And** The notification prompts the user to renew before downgrade.

---

### 35. Transaction History Retrieval (F-MON-05)

#### Scenario 1: Successfully retrieving transaction history (Happy Path)
- **Given** The user is authenticated and has a history of transaction records (invoices) in the system.
- **When** The user requests their transaction history (`GET /api/v1/payments/history`).
- **Then** The system retrieves all invoice records belonging to the authenticated user from the database.
- **And** The system retrieves all storage plans to map the plan names for the transaction descriptions.
- **And** The system returns the list of transactions ordered by creation date descending.
- **And** Each transaction record contains the unique invoice ID (`id`), payment gateway reference (`transactionId`), amount, status, payment provider (VNPay), description (`content`, e.g., "Account upgrade payment - Premium Plan"), creation date (`createdAt`), and completion date (`updatedAt`).

#### Scenario 2: Retrieving empty transaction history (Alternative Path)
- **Given** The user is authenticated and has no transaction records in the system.
- **When** The user requests their transaction history.
- **Then** The system queries the database and finds zero invoices for this user.
- **And** The system returns an empty list with a success message.

#### Scenario 3: Request blocked for unauthenticated visitor (Edge Case)
- **Given** An unauthenticated guest visitor attempts to access the transaction history endpoint.
- **When** The visitor sends a GET request to `/api/v1/payments/history`.
- **Then** The security filter chain intercepts the request.
- **And** The system blocks the request and returns HTTP `401 Unauthorized` with an access-denied message.

---

## 🔔 PART 8: IN-APP NOTIFICATIONS (F-NOT)

### 36. In-App Notifications (F-NOT-01)

> [!NOTE]
> Notifications (`GET /api/v1/notifications`, `PUT /api/v1/notifications/{id}/read`) cover lifecycle and account events such as `DOCUMENT_PENDING`, `DOCUMENT_APPROVED`, `DOCUMENT_REJECTED`, `DOCUMENT_RESTORED`, `NEW_REVIEW`, `REPORT_SUBMITTED`, `DOCUMENT_VIOLATION_DELETED`, `PLAN_UPGRADED`, `PLAN_EXPIRING`, `ACCOUNT_BANNED`, `ACCOUNT_WARNING`, and `ACCOUNT_ACTIVATED`. A user sees **only their own** notifications.

#### Scenario 1: User retrieves only their own notifications (Happy Path)
- **Given** The authenticated user has several notifications, and other users have their own.
- **When** The user opens the notifications view (`GET /api/v1/notifications`).
- **Then** The system queries the `notifications` table filtered by the authenticated user's ID.
- **And** The system returns **only** the notifications belonging to this user.
- **And** The response returns `200 OK` with the paginated list (newest first).

#### Scenario 2: User marks a notification as read (Happy Path)
- **Given** The user owns an unread notification.
- **When** The user marks it as read (`PUT /api/v1/notifications/{id}/read`).
- **Then** The system verifies the notification belongs to the requesting user.
- **And** The system sets the notification's read state.
- **And** The system returns `200 OK` with the updated notification.

#### Scenario 3: Mark-as-read forbidden when the notification belongs to another user (Edge Case / Security)
- **Given** The notification `{id}` belongs to a different user.
- **When** The authenticated user attempts `PUT /api/v1/notifications/{id}/read`.
- **Then** The system detects the ownership mismatch.
- **And** The system returns **`403 Forbidden`** (or `404 Not Found`) and does not modify the notification.

#### Scenario 4: Notifications blocked for guests (Edge Case / Security)
- **Given** An unauthenticated guest visitor.
- **When** The visitor attempts `GET /api/v1/notifications`.
- **Then** The security filter chain blocks the request.
- **And** The system returns `401 Unauthorized`.

---

## 🏁 PART 9: DEFINITION OF DONE (DoD)

A feature is considered "Done" and ready for production only when it meets the following criteria:

1. **Unit & Integration Tests:**
   - Minimum code coverage of 80% for core modules (Authentication, RAG Pipeline, VNPay Payment Webhook, Moderation).
   - 100% of automated Happy Path test cases pass successfully before merging code.
2. **Performance Constraints:**
   - Multi-document chat/summary API under 100 pages must respond in ≤ 5.0 seconds.
   - Public keyword search query execution must return results in ≤ 1.5 seconds.
   - Document preview in-browser rendering must complete in ≤ 3.0 seconds.
3. **Security & Authorization:**
   - Strict authorization check: users must not be able to view, edit, or delete other users' documents unless public or accessed via a valid share link.
   - Passwords hashed with bcrypt; OTP TTL 300 s; reset-token TTL 900 s.
   - JWT access token 1 hour + rotating refresh token 7 days; logged-out or banned access tokens added to the Redis Blacklist in real time.
   - VNPay IPN verified via HMAC-SHA512; internal callbacks guarded by `X-Internal-Secret`.
4. **User Experience & Responsiveness:**
   - Interface is fully responsive on both desktop and mobile viewports.
   - The `'overlimitstorage'` status blocks **upload only** and clearly prompts the user on how to resolve the storage lock (e.g., delete files or upgrade).
