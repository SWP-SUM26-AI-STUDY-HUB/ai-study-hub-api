# TEST CASE — AI Study Hub API

> Workbook follows `docs/test_case_excel_format_for_llm.md` exactly.
> Project: **AI Study Hub API** (Spring Boot 4.0.6 / Java 21 backend).
> All `Result` = `Untested`; all `Test date` blank — to be filled by the tester.

---

## SHEET: Cover

### Project Information

| Field | Value |
|---|---|
| Project Name | AI Study Hub API |
| Project Code | ASH-API |
| Document Code | ASH-API_TC_v1.0 |
| Creator | `<TBD>` |
| Reviewer/Approver | `<TBD>` |
| Issue Date | 2026-07-19 |
| Version | 1.0 |

### Record of Change

| Effective Date | Version | Change Item | *A,D,M | Change description | Reference |
|---|---|---|---|---|---|
| 2026-07-19 | 1.0 | Initial workbook | A | Created test cases for 14 modules (Auth, User, Document, Chat, StudyMaterial, Review, Report, Payment, Notification, Tag, AdminDocument, AdminUser, AdminReport, AdminDashboard). | AGENTS.md; source code `src/main/java/vn/ai_study_hub_api/controller` |

---

## SHEET: Test case List

### Project and Environment Information

| Field | Value |
|---|---|
| Project Name | AI Study Hub API |
| Project Code | ASH-API |
| Test Environment Setup Description | 1. Backend server: Spring Boot 4.0.6 / Java 21 (port 8080), started with `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`<br>2. Database: PostgreSQL 16 + pgvector (`aistudyhub`)<br>3. Cache & queue: Redis 7<br>4. Object storage: AWS S3<br>5. External RAG service: FastAPI on port 8000 (sibling repo) for chat, document processing, quiz/flashcard, moderation chunks<br>6. API documentation / test client: Swagger UI at `http://localhost:8080/swagger-ui/index.html` and Postman<br>7. Operating system: Windows 11<br>8. Test accounts: ACTIVE USER on Free plan, ACTIVE USER on Premium plan, ADMIN role, BANNED user, a second ACTIVE USER (non-owner)<br>9. Authentication: JWT Bearer token in `Authorization` header (access token TTL 1h, refresh token TTL 7d)<br>10. Config: `OPENAI_API_KEY`, `LANGFUSE_*`, `GOOGLE_*`, `AWS_*`, `INTERNAL_API_SECRET` set in `.env` for full-feature tests |

### Function List

| No | Function Name | Sheet Name | Description | Pre-Condition |
|---:|---|---|---|---|
| 1 | Login | Auth | Authenticate a user with email + password and return access + refresh JWT tokens | An ACTIVE user account exists |
| 2 | Register & Verify | Auth | Register a pending account, then activate it with an OTP | Application running; OTP delivery (log/email) available |
| 3 | Token refresh | Auth | Rotate the access token using a valid refresh token | A user is logged in with a valid refresh token |
| 4 | Logout | Auth | Blacklist the current access token and delete the refresh token | A user is logged in |
| 5 | Forgot / Reset password | Auth | Request a password reset email and reset using the token | An ACTIVE user account exists |
| 6 | Google OAuth | Auth | Generate the Google OAuth URL and exchange an authorization code for tokens | Google OAuth client configured |
| 7 | Get profile & storage | UserProfile | Retrieve the authenticated user's profile and storage usage | User is logged in |
| 8 | Update profile | UserProfile | Update display name and/or bio | User is logged in |
| 9 | Avatar | UserProfile | Upload / replace the user avatar (JPEG/PNG, ≤ 2MB) | User is logged in |
| 10 | Change password | UserProfile | Change password by validating the current one | User is logged in and knows the current password |
| 11 | Preferred tags | UserProfile | Save 1–3 public tag ids from the onboarding survey | User is logged in; public tags exist |
| 12 | Upload document | Document | Upload a study document (PDF/DOCX/TXT/MD) PRIVATE or PUBLIC | User is logged in, ACTIVE, not over storage limit |
| 13 | Get & search documents | Document | Get document details and search public documents by keyword | Documents exist in various states |
| 14 | Preview & download | Document | Generate a presigned S3 URL for preview / download | A completed document exists |
| 15 | Share link | Document | Generate a read-only share token and preview via token | Owner has a PUBLIC+COMPLETED document |
| 16 | Personal & trash | Document | List the user's personal (non-deleted) and trash documents | User has documents |
| 17 | Update document | Document | Update title/description/tags/visibility (PRIVATE→PUBLIC triggers moderation) | Owner has a COMPLETED document |
| 18 | Delete & restore | Document | Soft-delete a document and restore it from trash | Owner has a COMPLETED document |
| 19 | Save / unsave | Document | Bookmark / unbookmark a public completed document | A PUBLIC+COMPLETED document exists |
| 20 | Recommendations & author | Document | Get recommended docs by preferred tags and list an author's public docs | PUBLIC+COMPLETED documents and preferred tags exist |
| 21 | Chat | Chat | Ask an AI question scoped to a document or all of the user's documents | User is logged in; a COMPLETED document the user can access exists; RAG service running |
| 22 | Sessions | Chat | List / read / rename / soft-delete chat sessions | User has at least one chat session |
| 23 | Quota | Chat | Read the current daily AI quota usage | User is logged in |
| 24 | Quiz | StudyMaterial | Generate a multiple-choice quiz from a document | User is logged in; an accessible COMPLETED document exists; RAG service running |
| 25 | Flashcard | StudyMaterial | Generate flashcards from a document | User is logged in; an accessible COMPLETED document exists; RAG service running |
| 26 | Submit review | Review | Rate and comment a public completed document | A PUBLIC+COMPLETED document exists |
| 27 | Get reviews | Review | List reviews of a public completed document (guest-accessible) | A PUBLIC+COMPLETED document with reviews exists |
| 28 | Submit report | Report | Report an abusive public document | A PUBLIC+COMPLETED document exists |
| 29 | Create payment | Payment | Create a VNPay payment URL for a storage plan | A valid storage plan exists |
| 30 | VNPay callback / IPN | Payment | Handle the VNPay return URL (callback) and server-to-server IPN | A payment was initiated |
| 31 | History | Payment | List the authenticated user's invoices | User has at least one invoice |
| 32 | Notifications | Notification | List the user's notifications and mark one as read | User has notifications |
| 33 | Tags | Tag | Search tags, list public tags, create private tags | User is logged in |
| 34 | Moderation | AdminDocument | Admin lists pending documents and approves / rejects them; auto-moderation triage is observable | Admin logged in; PUBLIC documents in PENDING state |
| 35 | User management | AdminUser | Admin lists, bans, reactivates and warns users | Admin logged in; target users exist |
| 36 | Report handling | AdminReport | Admin resolves / rejects reports and views reported documents | Admin logged in; pending reports exist |
| 37 | Dashboard | AdminDashboard | Admin retrieves platform stats and AI/RAG observability metrics | Admin logged in |

---

## SHEET: Auth

### Module Information

| Field | Value |
|---|---|
| Module Code | AUTH |
| Test requirement | Verify authentication: login, register + OTP verification, JWT refresh rotation, logout blacklisting, forgot/reset password, and Google OAuth. |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 21 | 0 | 21 |

### Function: Login

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [AUTH-01] | Verify login with valid credentials returns both JWT tokens | 1. Send `POST /api/v1/auth/login`.<br>2. Use body `{ "email": "<active user email>", "password": "<correct password>" }`. | - HTTP 200.<br>- `success` is `true`.<br>- `data.accessToken` and `data.refreshToken` are non-empty JWT strings.<br>- `data.user.email` equals the submitted email. |  | Untested |  |  |
| [AUTH-02] | Reject login with a wrong password | 1. Send `POST /api/v1/auth/login`.<br>2. Use a valid email with an incorrect password. | - HTTP 401 (or the AppException status mapped by `GlobalExceptionHandler`).<br>- `success` is `false`.<br>- No token is returned. |  | Untested |  |  |
| [AUTH-03] | Reject login with a non-existent email | 1. Send `POST /api/v1/auth/login`.<br>2. Use `email = "nope@nowhere.invalid"`. | - HTTP 401.<br>- `success` is `false`.<br>- No token is returned. |  | Untested |  |  |
| [AUTH-04] | Reject login with a blank email | 1. Send `POST /api/v1/auth/login`.<br>2. Use body `{ "email": "", "password": "any" }`. | - HTTP 400.<br>- `errors.email` contains the "Email is required" message.<br>- No token is returned. |  | Untested |  | Validation via `@NotBlank`. |
| [AUTH-05] | Reject login with an invalid email format | 1. Send `POST /api/v1/auth/login`.<br>2. Use body `{ "email": "not-an-email", "password": "any" }`. | - HTTP 400.<br>- `errors.email` contains "Invalid email format".<br>- No token is returned. |  | Untested |  | Validation via `@Email`. |
| [AUTH-06] | Reject login for a BANNED account | 1. Ensure a user has status `BANNED`.<br>2. Send `POST /api/v1/auth/login` with correct credentials. | - Authentication is rejected (disabled account).<br>- HTTP 401 / appropriate error.<br>- No token is returned. |  | Untested |  | `CustomUserDetails` treats BANNED as disabled. |

### Function: Register & Verify

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [AUTH-07] | Register a new pending account | 1. Send `POST /api/v1/auth/register`.<br>2. Use a unique email, password and fullName. | - HTTP 200.<br>- `success` is `true`.<br>- `data` is null.<br>- A new user row exists with status `INACTIVE` / pending and an OTP is generated (visible in log/email). |  | Untested |  |  |
| [AUTH-08] | Reject registration with a duplicate email | 1. Send `POST /api/v1/auth/register`.<br>2. Re-use an email that already exists. | - HTTP 409 Conflict (or the AppException status).<br>- `success` is `false`.<br>- No new account is created. | [AUTH-07] | Untested |  |  |
| [AUTH-09] | Activate the account with a valid OTP | 1. Register an account (from AUTH-07).<br>2. Send `POST /api/v1/auth/verify?email=<email>&otp=<otp>`. | - HTTP 200.<br>- The user's status changes to `ACTIVE`.<br>- The OTP is invalidated after use. | [AUTH-07] | Untested |  |  |
| [AUTH-10] | Reject verification with a wrong OTP | 1. Send `POST /api/v1/auth/verify?email=<email>&otp=000000`. | - HTTP 400 / appropriate error.<br>- The account stays pending / INACTIVE. | [AUTH-07] | Untested |  |  |
| [AUTH-11] | Resend a new OTP | 1. Send `POST /api/v1/auth/resend-otp?email=<email>`. | - HTTP 200.<br>- A new OTP is generated (old one replaced). | [AUTH-07] | Untested |  | Subject to rate limit. |

### Function: Token refresh

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [AUTH-12] | Refresh rotates the access token | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/auth/refresh` with the current `refreshToken`. | - HTTP 200.<br>- `data.accessToken` is a new non-empty token.<br>- `data.refreshToken` is rotated (differs from the submitted one).<br>- The old refresh token is no longer valid. | [AUTH-01] | Untested |  | Refresh-token rotation is enforced. |
| [AUTH-13] | Reject refresh with an invalid / blacklisted token | 1. Send `POST /api/v1/auth/refresh` with `refreshToken = "invalid"`. | - HTTP 401.<br>- No access token is returned. |  | Untested |  |  |

### Function: Logout

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [AUTH-14] | Logout blacklists the access token | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/auth/logout` with header `Authorization: Bearer <accessToken>`. | - HTTP 200.<br>- `success` is `true`.<br>- The access token is added to the Redis blacklist and the refresh token is deleted. | [AUTH-01] | Untested |  |  |
| [AUTH-15] | Access a protected endpoint after logout fails | 1. Perform AUTH-14.<br>2. Send `GET /api/v1/users/profile` with the now-blacklisted access token. | - HTTP 401.<br>- The request is rejected by `JwtAuthenticationFilter` (blacklist check). | [AUTH-14] | Untested |  |  |

### Function: Forgot / Reset password

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [AUTH-16] | Request a password reset for a known email | 1. Send `POST /api/v1/auth/forgot-password` with a valid `{ "email": "<existing email>" }`. | - HTTP 200.<br>- `success` is `true`.<br>- A reset token is generated and delivered (email/log). |  | Untested |  |  |
| [AUTH-17] | Forgot-password for an unknown email does not leak existence | 1. Send `POST /api/v1/auth/forgot-password` with `email = "ghost@nowhere.invalid"`. | - HTTP 200 with a generic processed message, or a controlled error.<br>- No reset token / email is produced for a non-existent user. |  | Untested |  | Confirm actual behavior in service. |
| [AUTH-18] | Reset password with a valid token | 1. Perform AUTH-16 to obtain a reset token.<br>2. Send `POST /api/v1/auth/reset-password` with the token and a new password. | - HTTP 200.<br>- The user can then log in with the new password (re-verify via AUTH-01). | [AUTH-16] | Untested |  |  |
| [AUTH-19] | Reject reset with an invalid / expired token | 1. Send `POST /api/v1/auth/reset-password` with token `invalid` and any password. | - HTTP 400 / 401.<br>- The password is not changed. |  | Untested |  |  |

### Function: Google OAuth

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [AUTH-20] | Generate the Google OAuth authorization URL | 1. Send `GET /api/v1/auth/social-login?login_type=google`. | - HTTP 200.<br>- `data` is a non-empty URL starting with `https://accounts.google.com/o/oauth2/...`. |  | Untested |  |  |
| [AUTH-21] | Exchange a Google authorization code for tokens | 1. Complete the Google consent flow to obtain a `code`.<br>2. Send `GET /api/v1/auth/google/callback?code=<code>`. | - HTTP 200.<br>- `data.accessToken` and `data.refreshToken` are non-empty.<br>- A user record is created/updated from the Google profile. | [AUTH-20] | Untested |  | Requires valid Google `code`; cannot be reused. |

---

## SHEET: UserProfile

### Module Information

| Field | Value |
|---|---|
| Module Code | USER |
| Test requirement | Verify the user profile endpoints: profile read, storage usage, profile/bio update, avatar upload with size & format validation, change password, and onboarding preferred-tags. |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 16 | 0 | 16 |

### Function: Get profile & storage

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [USER-01] | Get the authenticated user's profile | 1. Login (AUTH-01).<br>2. Send `GET /api/v1/users/profile` with the access token. | - HTTP 200.<br>- `data.id`, `data.email`, `data.role` match the logged-in user. | [AUTH-01] | Untested |  |  |
| [USER-02] | Reject profile access without authentication | 1. Send `GET /api/v1/users/profile` with no `Authorization` header. | - HTTP 401.<br>- No profile data is returned. |  | Untested |  |  |
| [USER-03] | Get current storage usage | 1. Login (AUTH-01).<br>2. Send `GET /api/v1/users/storage`. | - HTTP 200.<br>- `data.storageUsed`, `data.storageLimit`, and plan info are present and numeric. | [AUTH-01] | Untested |  |  |

### Function: Update profile

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [USER-04] | Update display name | 1. Login (AUTH-01).<br>2. Send `PUT /api/v1/users/edit-profile` with `{ "fullName": "New Name" }`. | - HTTP 200.<br>- `data.fullName` equals "New Name".<br>- The DB `users.full_name` is updated and `updated_at` refreshed. | [AUTH-01] | Untested |  |  |
| [USER-05] | Update / clear bio | 1. Login (AUTH-01).<br>2. Send `PUT /api/v1/users/edit-profile` with `{ "bio": "My bio text" }` then once more with `{ "bio": "" }`. | - HTTP 200 on both.<br>- First call stores the bio; second call clears it (`bio` becomes null). | [AUTH-01] | Untested |  | Blank bio is trimmed to null. |
| [USER-06] | Reject profile update without authentication | 1. Send `PUT /api/v1/users/edit-profile` with no token. | - HTTP 401.<br>- Profile is unchanged. |  | Untested |  |  |

### Function: Avatar

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [USER-07] | Upload a valid PNG avatar | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/users/edit-profile/avatar` (multipart, part `avatar`) with a PNG ≤ 2MB. | - HTTP 200.<br>- `data.avatarUrl` is set to a new URL.<br>- The old avatar (if any) is replaced. | [AUTH-01] | Untested |  |  |
| [USER-08] | Reject avatar larger than 2MB | 1. Login (AUTH-01).<br>2. Send the avatar request with a file of 2.5MB. | - HTTP 400.<br>- Error message mentions the 2MB limit.<br>- Avatar is not changed. | [AUTH-01] | Untested |  |  |
| [USER-09] | Reject a non-image avatar | 1. Login (AUTH-01).<br>2. Send the avatar request with a `.txt` file ≤ 2MB. | - HTTP 400.<br>- Error message states only JPEG/PNG are allowed.<br>- Avatar is not changed. | [AUTH-01] | Untested |  | Image content is verified via `ImageIO.read`. |
| [USER-10] | Reject an empty avatar part | 1. Login (AUTH-01).<br>2. Send the avatar request with an empty file part. | - HTTP 400.<br>- "Avatar file is required." | [AUTH-01] | Untested |  |  |

### Function: Change password

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [USER-11] | Change password with a correct current password | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/users/change-password` with the correct current password and a new password. | - HTTP 200.<br>- The user can log in with the new password and NOT with the old one. | [AUTH-01] | Untested |  |  |
| [USER-12] | Reject change password with a wrong current password | 1. Login (AUTH-01).<br>2. Send the request with an incorrect current password. | - HTTP 400 (or AppException status).<br>- The password is not changed. | [AUTH-01] | Untested |  |  |
| [USER-13] | Reject change password without authentication | 1. Send `POST /api/v1/users/change-password` with no token. | - HTTP 401.<br>- Password unchanged. |  | Untested |  |  |

### Function: Preferred tags

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [USER-14] | Save 1–3 public tag ids | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/users/preferred-tags` with `{ "tagIds": [1, 2] }` (valid public tag ids). | - HTTP 200.<br>- `users.preferred_tag_ids` equals `[1,2]`. | [AUTH-01] | Untested |  |  |
| [USER-15] | Reject saving more than 3 tags | 1. Login (AUTH-01).<br>2. Send the request with `{ "tagIds": [1,2,3,4] }`. | - HTTP 400.<br>- `preferred_tag_ids` is unchanged. | [AUTH-01] | Untested |  | Constraint: 1–3 public tags. |
| [USER-16] | Reject saving preferred tags without authentication | 1. Send `POST /api/v1/users/preferred-tags` with no token. | - HTTP 401.<br>- Tags are not saved. |  | Untested |  |  |

---

## SHEET: Document

### Module Information

| Field | Value |
|---|---|
| Module Code | DOC |
| Test requirement | Verify the full document lifecycle: upload (validation + size/type limits + state machine), get/search, preview/download (guest vs auth), share link, personal/trash lists, update (PRIVATE→PUBLIC moderation trigger), soft-delete/restore, save/unsave, recommendations, and author lookup. |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 40 | 0 | 40 |

### Function: Upload document

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [DOC-01] | Upload a valid PRIVATE document | 1. Login as an ACTIVE user (AUTH-01).<br>2. Send `POST /api/v1/documents/upload` (multipart) with a PDF, `title`, and `visibility=PRIVATE`. | - HTTP 200.<br>- `data.documentId` is a UUID.<br>- `data.status` is "uploading".<br>- A `documents` row exists with status `PROCESSING` and visibility `PRIVATE`; storage used increases by file size.<br>- Background `/process` is dispatched to RAG. | [AUTH-01] | Untested |  |  |
| [DOC-02] | Upload a valid PUBLIC document | 1. Login as an ACTIVE user (AUTH-01).<br>2. Send the upload request with `visibility=PUBLIC`. | - HTTP 200.<br>- Document row has status `PENDING` and visibility `PUBLIC`.<br>- Admins receive a `DOCUMENT_PENDING` notification.<br>- `/extract` is dispatched to RAG (chunks created, embeddings deferred). | [AUTH-01] | Untested |  | Auto-moderation triage then runs asynchronously. |
| [DOC-03] | Reject an unsupported file extension | 1. Login (AUTH-01).<br>2. Upload a `.exe` file. | - HTTP 400.<br>- Error message is "Unsupported file format".<br>- No document row is created. | [AUTH-01] | Untested |  | Allowed: pdf, docx, txt, md. |
| [DOC-04] | Reject a file over the 50MB business cap | 1. Login (AUTH-01).<br>2. Upload a valid-type file larger than 50MB. | - HTTP 400 (clean service-layer error) for files ≤ 60MB.<br>- Files > 60MB are rejected by the multipart ceiling (HTTP 500 / size-exceeded) — see Note. | [AUTH-01] | Untested |  | Multipart ceiling is 60MB; business cap is 50MB. |
| [DOC-05] | Reject upload with a missing title | 1. Login (AUTH-01).<br>2. Upload a valid file with an empty/blank title. | - HTTP 400.<br>- No document row is created. | [AUTH-01] | Untested |  | Title is required by the service. |
| [DOC-06] | Reject upload without authentication | 1. Send `POST /api/v1/documents/upload` with no token. | - HTTP 401.<br>- No document is created. |  | Untested |  |  |
| [DOC-07] | Reject upload when the user is over storage limit | 1. Use an account with status `OVERLIMITSTORAGE`.<br>2. Attempt to upload. | - HTTP 400.<br>- Error indicates storage limit exceeded.<br>- No document is created; the user can still list/read own docs. |  | Untested |  | `OVERLIMITSTORAGE` blocks only upload. |

### Function: Get & search documents

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [DOC-08] | Owner sees full details of own document | 1. Login as the owner of a COMPLETED doc (DOC-01 after processing).<br>2. Send `GET /api/v1/documents/{documentId}`. | - HTTP 200.<br>- `data` includes the full document; PRIVATE tags are visible to the owner. | [DOC-01] | Untested |  |  |
| [DOC-09] | Guest can view a public COMPLETED document | 1. Send `GET /api/v1/documents/{publicCompletedId}` with no token. | - HTTP 200.<br>- Only PUBLIC tags are returned; PRIVATE tags are filtered out. | [DOC-02] | Untested |  |  |
| [DOC-10] | Another user cannot view a private document | 1. Login as a non-owner user.<br>2. Send `GET /api/v1/documents/{privateDocId}`. | - HTTP 403 or 404 (document not visible to this user).<br>- No document payload is returned. | [DOC-01] | Untested |  | Confirm exact status in service. |
| [DOC-11] | Search public documents by keyword | 1. Send `GET /api/v1/documents/search?keyword=<knownWord>`. | - HTTP 200.<br>- `data` contains only documents whose title/tags/description/summary match and that are PUBLIC+COMPLETED+not-deleted. | [DOC-02] | Untested |  |  |
| [DOC-12] | Search never returns private/pending/rejected/deleted docs | 1. Ensure docs in PRIVATE, PENDING, REJECTED, DELETED states contain the keyword.<br>2. Run the search. | - HTTP 200.<br>- None of the PRIVATE/PENDING/REJECTED/DELETED documents appear in results. | [DOC-01] | Untested |  |  |
| [DOC-13] | Get a non-existent document returns 404 | 1. Send `GET /api/v1/documents/00000000-0000-0000-0000-000000000000`. | - HTTP 404.<br>- `success` is `false`. |  | Untested |  |  |

### Function: Preview & download

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [DOC-14] | Guest preview of a public doc is the 30% preview file | 1. Send `GET /api/v1/documents/{publicCompletedId}/preview` with no token. | - HTTP 200.<br>- `data.url` points to the `_preview` S3 path (truncated ~30%). | [DOC-02] | Untested |  | Guests are restricted to the preview file. |
| [DOC-15] | Owner preview returns the full document path | 1. Login as the owner.<br>2. Send `GET /api/v1/documents/{ownDocId}/preview`. | - HTTP 200.<br>- `data.url` points to the full document path (not `_preview`). | [DOC-01] | Untested |  |  |
| [DOC-16] | Authenticated user can download a public document | 1. Login (AUTH-01).<br>2. Send `GET /api/v1/documents/{publicCompletedId}/download`. | - HTTP 200.<br>- `data.url` is a presigned S3 URL to the full file.<br>- `documents.download_count` is incremented by 1. | [DOC-02] | Untested |  |  |
| [DOC-17] | Download requires authentication | 1. Send `GET /api/v1/documents/{id}/download` with no token. | - HTTP 401.<br>- No URL is returned; download_count unchanged. |  | Untested |  |  |
| [DOC-18] | Download count increments only on a successful download request | 1. Login (AUTH-01).<br>2. Record current `download_count`.<br>3. Call `/download` twice on an accessible doc.<br>4. Check the count. | - `download_count` increases by exactly 2 (one per successful request). | [DOC-16] | Untested |  |  |

### Function: Share link

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [DOC-19] | Owner generates a share link | 1. Login as owner of a PUBLIC+COMPLETED doc.<br>2. Send `POST /api/v1/documents/{documentId}/share`. | - HTTP 200.<br>- `data.token` is a non-empty UUID/hash.<br>- `data.shareUrl` starts with the configured `app.share-url-prefix`.<br>- The doc row's `link_share` is set to the token. | [DOC-02] | Untested |  |  |
| [DOC-20] | Non-owner cannot generate a share link | 1. Login as a non-owner user.<br>2. Send `POST /api/v1/documents/{documentId}/share`. | - HTTP 403.<br>- `link_share` is unchanged. | [DOC-02] | Untested |  |  |
| [DOC-21] | Preview a shared document by token | 1. Generate a share link (DOC-19).<br>2. Send `GET /api/v1/documents/shared/{token}` (no token required). | - HTTP 200.<br>- `data.title`, `data.uploaderName`, and `data.previewUrl` are returned.<br>- Only PUBLIC tags are listed. | [DOC-19] | Untested |  |  |
| [DOC-22] | Preview with an invalid token returns 404 | 1. Send `GET /api/v1/documents/shared/not-a-real-token`. | - HTTP 404.<br>- No document payload. |  | Untested |  |  |
| [DOC-23] | Shared link is invalidated after the document is deleted | 1. Generate a share link (DOC-19).<br>2. Soft-delete the document (owner).<br>3. Send `GET /api/v1/documents/shared/{token}`. | - HTTP 404.<br>- `link_share` is set to null on delete. | [DOC-19] | Untested |  | Share token is nulled on soft-delete. |

### Function: Personal & trash

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [DOC-24] | List personal documents | 1. Login (AUTH-01).<br>2. Send `GET /api/v1/documents/personal`. | - HTTP 200.<br>- `data` lists only the caller's non-deleted documents. | [DOC-01] | Untested |  | Works even when OVERLIMITSTORAGE. |
| [DOC-25] | List trash documents | 1. Login as a user who has soft-deleted a doc.<br>2. Send `GET /api/v1/documents/trash`. | - HTTP 200.<br>- `data` lists only the caller's soft-deleted (DELETED) documents. | [DOC-29] | Untested |  |  |

### Function: Update document

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [DOC-26] | Updating PRIVATE→PUBLIC triggers moderation and stays PENDING | 1. Login as owner of a COMPLETED PRIVATE doc.<br>2. Send `PUT /api/v1/documents/{id}` with `visibility=PUBLIC`. | - HTTP 200.<br>- Document status becomes `PENDING`; visibility stays PRIVATE in RAG until approve.<br>- A moderation job is enqueued on `stream:moderation`; admins are notified.<br>- No `/extract` call is made (chunks already exist). | [DOC-01] | Untested |  | RAG visibility flips to public only at admin/m auto-approve. |
| [DOC-27] | Non-owner cannot update another user's document | 1. Login as a non-owner user.<br>2. Send `PUT /api/v1/documents/{id}`. | - HTTP 403.<br>- Document is unchanged. | [DOC-01] | Untested |  |  |
| [DOC-28] | Update a non-existent document returns 404 | 1. Login (AUTH-01).<br>2. Send `PUT /api/v1/documents/00000000-0000-0000-0000-000000000000`. | - HTTP 404.<br>- `success` is `false`. |  | Untested |  |  |

### Function: Delete & restore

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [DOC-29] | Owner soft-deletes a document | 1. Login as owner of a COMPLETED doc.<br>2. Send `DELETE /api/v1/documents/{documentId}`. | - HTTP 200.<br>- Status becomes `DELETED`; `deletedAt` is set; `link_share` set to null; `status_before_deletion` saved; user storage decreases by file size.<br>- If storage drops under the limit, status restores `OVERLIMITSTORAGE → ACTIVE`.<br>- Trending cache is evicted; RAG is NOT purged (restore-friendly). | [DOC-01] | Untested |  |  |
| [DOC-30] | Owner restores their own soft-deleted document | 1. After DOC-29, send `POST /api/v1/documents/{documentId}/restore`. | - HTTP 200.<br>- Status returns to `status_before_deletion`; storage is restored (may set OVERLIMITSTORAGE).<br>- A fresh `link_share` is minted if PUBLIC+COMPLETED; a `DOCUMENT_RESTORED` notification is written.<br>- RAG is not called. | [DOC-29] | Untested |  |  |
| [DOC-31] | Cannot restore a document deleted by an admin | 1. Use a document where `deleted_by_admin=true`.<br>2. As the owner, send `POST /api/v1/documents/{id}/restore`. | - HTTP 403 (or appropriate error).<br>- Document stays `DELETED`. |  | Untested |  | Admin-deleted docs are not restorable by the owner. |
| [DOC-32] | Cannot restore a document that is not deleted | 1. Login as owner of a COMPLETED (non-deleted) doc.<br>2. Send `POST /api/v1/documents/{id}/restore`. | - HTTP 400 / appropriate error.<br>- Document status is unchanged. | [DOC-01] | Untested |  |  |

### Function: Save / unsave

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [DOC-33] | Save a public completed document | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/documents/{publicCompletedId}/save`. | - HTTP 200.<br>- A `saved_documents` row (user_id + document_id) is created. | [DOC-02] | Untested |  |  |
| [DOC-34] | Reject saving a private or non-completed document | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/documents/{privateOrPendingId}/save`. | - HTTP 400 (or AppException status).<br>- No saved_documents row is created. | [DOC-01] | Untested |  | Only PUBLIC+COMPLETED docs are savable. |
| [DOC-35] | Unsave a previously saved document | 1. Login (AUTH-01).<br>2. Save a doc (DOC-33).<br>3. Send `DELETE /api/v1/documents/{documentId}/unsave`. | - HTTP 200.<br>- The `saved_documents` row is removed. | [DOC-33] | Untested |  |  |
| [DOC-36] | List saved documents with pagination | 1. Login as a user with ≥ 1 saved doc.<br>2. Send `GET /api/v1/documents/saved?page=0&size=10`. | - HTTP 200.<br>- `data.content` lists saved documents; pagination metadata is present. | [DOC-33] | Untested |  |  |
| [DOC-37] | Re-saving an already-saved document is idempotent or returns a controlled error | 1. Save a doc (DOC-33).<br>2. Save it again. | - Either HTTP 200 with no duplicate row, or HTTP 400/409.<br>- The unique constraint (user_id+document_id) prevents duplicates. | [DOC-33] | Untested |  | Confirm actual handling in service. |

### Function: Recommendations & author

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [DOC-38] | Get recommended documents by preferred tags | 1. Login as a user with `preferred_tag_ids` set.<br>2. Send `GET /api/v1/documents/recommendations?page=0&size=8`. | - HTTP 200.<br>- Results are PUBLIC+COMPLETED docs ranked by tag-match count, avg rating, recency; paginated. | [USER-14] | Untested |  |  |
| [DOC-39] | Recommendations are empty when no preferred tags are set | 1. Login as a user with no `preferred_tag_ids`.<br>2. Send `GET /api/v1/documents/recommendations`. | - HTTP 200.<br>- `data.content` is empty. |  | Untested |  |  |
| [DOC-40] | Get an author's public documents (public endpoint) | 1. Send `GET /api/v1/documents/user/{authorUserId}?page=0&size=10` (no token needed). | - HTTP 200.<br>- Results are only PUBLIC+COMPLETED docs by that author; paginated. | [DOC-02] | Untested |  |  |

---

## SHEET: Chat

### Module Information

| Field | Value |
|---|---|
| Module Code | CHAT |
| Test requirement | Verify the RAG chatbot: question asking (document-scoped or all-user), AI quota enforcement, message persistence, session listing/reading/renaming/soft-delete, and the quota read endpoint. |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 12 | 0 | 12 |

### Function: Chat

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [CHAT-01] | Ask a question scoped to an accessible document | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/chat` with `{ "documentId": "<accessibleCompletedId>", "query": "Summarize the main points", "sessionId": null }`. | - HTTP 200.<br>- `data.answer` is a non-empty string.<br>- `data.citations` lists sources retrieved from the document.<br>- `data.remainingRequests` decreases by 1 vs. quota.<br>- A USER and a BOT `chat_messages` row are persisted. | [DOC-01] | Untested |  | RAG service must be running. |
| [CHAT-02] | Reject an empty query | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/chat` with `{ "query": "" }`. | - HTTP 400 / appropriate error.<br>- No RAG call is made; quota is not consumed. | [AUTH-01] | Untested |  | Confirm exact handling. |
| [CHAT-03] | Block chat when the daily AI quota is exceeded | 1. Exhaust the user's daily AI quota (chat or study-material calls).<br>2. Send `POST /api/v1/chat`. | - HTTP 429.<br>- Error message mentions the daily limit / upgrade to Premium.<br>- The counter is NOT incremented; no RAG call. | [CHAT-01] | Untested |  |  |
| [CHAT-04] | Reject chat without authentication | 1. Send `POST /api/v1/chat` with no token. | - HTTP 401.<br>- No answer is returned. |  | Untested |  |  |
| [CHAT-05] | Reject chat on a document the user cannot access | 1. Login as a non-owner of a PRIVATE doc.<br>2. Send `POST /api/v1/chat` with that documentId. | - HTTP 403.<br>- No RAG call; quota not consumed. | [DOC-01] | Untested |  |  |
| [CHAT-06] | Chat persists both user and bot messages and uses history | 1. Send a first chat message (CHAT-01).<br>2. Send a follow-up in the returned `sessionId` referencing the prior turn. | - HTTP 200 on both.<br>- `GET /sessions/{id}/messages` returns the prior USER+BOT turns in order (≤10 used as history).<br>- The follow-up answer resolves references using the history. | [CHAT-01] | Untested |  | History = last 10 messages, oldest first. |

### Function: Sessions

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [CHAT-07] | List the user's chat sessions | 1. Login (AUTH-01) with ≥ 1 session.<br>2. Send `GET /api/v1/chat/sessions`. | - HTTP 200.<br>- `data` lists the caller's sessions. | [CHAT-01] | Untested |  |  |
| [CHAT-08] | Read a session's message history | 1. Send `GET /api/v1/chat/sessions/{sessionId}/messages`. | - HTTP 200.<br>- `data` lists messages in chronological order; study-material bot messages carry parsed `materialType`/`quiz`/`flashcards`. | [CHAT-01] | Untested |  |  |
| [CHAT-09] | Reject reading another user's session | 1. Login as user B.<br>2. Send `GET /api/v1/chat/sessions/{sessionOfUserA}/messages`. | - HTTP 403 / 404.<br>- No messages are returned. | [CHAT-01] | Untested |  |  |
| [CHAT-10] | Rename a chat session | 1. Send `PATCH /api/v1/chat/sessions/{sessionId}` with `{ "title": "New title" }`. | - HTTP 200.<br>- The session title is updated. | [CHAT-01] | Untested |  |  |
| [CHAT-11] | Soft-delete a chat session | 1. Send `DELETE /api/v1/chat/sessions/{sessionId}`. | - HTTP 200.<br>- The session no longer appears in `GET /chat/sessions`. | [CHAT-01] | Untested |  |  |

### Function: Quota

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [CHAT-12] | Read the current daily AI quota usage | 1. Login (AUTH-01).<br>2. Send `GET /api/v1/chat/quota`. | - HTTP 200.<br>- `data.currentCount`, `data.dailyLimit`, `data.remaining` are present and consistent (current + remaining = limit). | [AUTH-01] | Untested |  | Limit is plan-dependent (`max_ai_requests_per_day`). |

---

## SHEET: StudyMaterial

### Module Information

| Field | Value |
|---|---|
| Module Code | STUDY |
| Test requirement | Verify quiz and flashcard generation from a document: happy path, refusal (document too short/fragmented), quota enforcement (shared with chat), document access validation, and persistence into a chat session. |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 10 | 0 | 10 |

### Function: Quiz

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [STUDY-01] | Generate a quiz from an accessible document | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/study-materials/quiz` with `{ "documentId": "<accessibleCompletedId>", "count": 10 }`. | - HTTP 200.<br>- `data.quiz` has 5–20 items; each item has `question`, `options[4]`, `correctIndex` (0–3), `explanation`.<br>- `data.remainingRequests`/`dailyLimit` reflect quota after the call.<br>- `data.sessionId` is set (new or reused). | [DOC-01] | Untested |  | RAG clamps count to 5–20. |
| [STUDY-02] | A refusal returns HTTP 200 with an empty quiz list | 1. Use a very short/fragmented document.<br>2. Send the quiz request. | - HTTP 200.<br>- `data.quiz` is empty.<br>- `message` carries the RAG refusal reason.<br>- Quota is still consumed (counter incremented before the RAG call). | [DOC-01] | Untested |  | Refusal still consumes quota. |
| [STUDY-03] | Reject quiz generation when quota is exceeded | 1. Exhaust the daily AI quota.<br>2. Send the quiz request. | - HTTP 429.<br>- No RAG call is made. | [STUDY-01] | Untested |  | Shared counter with chat. |
| [STUDY-04] | Reject quiz generation without authentication | 1. Send `POST /api/v1/study-materials/quiz` with no token. | - HTTP 401.<br>- No generation. |  | Untested |  |  |
| [STUDY-05] | Reject quiz for a document the user cannot access | 1. Login as a non-owner of a PRIVATE doc.<br>2. Send the quiz request with that documentId. | - HTTP 403.<br>- Quota is not consumed. | [DOC-01] | Untested |  |  |
| [STUDY-06] | Reject quiz when documentId is missing | 1. Login (AUTH-01).<br>2. Send `{ "count": 10 }` (no documentId). | - HTTP 400 / appropriate error.<br>- No generation; quota not consumed. | [AUTH-01] | Untested |  | documentId is required. |

### Function: Flashcard

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [STUDY-07] | Generate flashcards from an accessible document | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/study-materials/flashcard` with `{ "documentId": "<id>", "count": 15 }`. | - HTTP 200.<br>- `data.flashcards` has 5–30 items; each item has `term` and `definition`.<br>- `data.sessionId` is set. | [DOC-01] | Untested |  | RAG clamps count to 5–30. |
| [STUDY-08] | A flashcard refusal returns HTTP 200 with an empty list | 1. Use a short/fragmented document.<br>2. Send the flashcard request. | - HTTP 200.<br>- `data.flashcards` is empty.<br>- `message` carries the refusal reason; quota is consumed. | [DOC-01] | Untested |  |  |
| [STUDY-09] | Quiz and flashcard share the daily quota with chat | 1. Record `GET /chat/quota`.remaining = R.<br>2. Send a quiz call (STUDY-01).<br>3. Re-read quota. | - remaining decreases by 1 after the quiz call (same counter as chat). | [STUDY-01] | Untested |  |  |
| [STUDY-10] | Generation is persisted as a USER+BOT message pair in a chat session | 1. Send a quiz generation (STUDY-01).<br>2. Read the returned `sessionId` messages. | - The session contains a USER message (the request) and a BOT message whose `material_payload` JSONB holds the items; the BOT message re-parses to `quiz`/`flashcards` in the response. | [STUDY-01] | Untested |  | Bot row carries `material_payload` JSONB. |

---

## SHEET: Review

### Module Information

| Field | Value |
|---|---|
| Module Code | REVIEW |
| Test requirement | Verify document reviews: submit (rating validation 1–5, public-completed target only), duplicate handling, and the guest-accessible review listing. |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 10 | 0 | 10 |

### Function: Submit review

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [REVIEW-01] | Submit a valid review | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/documents/{publicCompletedId}/reviews` with `{ "rating": 4, "comment": "Helpful" }`. | - HTTP 200.<br>- `data.rating` is 4, `data.comment` is "Helpful".<br>- A `reviews` row is created and the uploader receives a `NEW_REVIEW` notification. | [DOC-02] | Untested |  |  |
| [REVIEW-02] | Reject rating below 1 | 1. Login (AUTH-01).<br>2. Submit `{ "rating": 0 }`. | - HTTP 400.<br>- `errors.rating` states the minimum is 1.<br>- No review is created. | [DOC-02] | Untested |  | `@Min(1)`. |
| [REVIEW-03] | Reject rating above 5 | 1. Login (AUTH-01).<br>2. Submit `{ "rating": 6 }`. | - HTTP 400.<br>- `errors.rating` states the maximum is 5.<br>- No review is created. | [DOC-02] | Untested |  | `@Max(5)`. |
| [REVIEW-04] | Reject a null rating | 1. Login (AUTH-01).<br>2. Submit `{ "comment": "x" }` (no rating). | - HTTP 400.<br>- `errors.rating` states it is required. | [DOC-02] | Untested |  | `@NotNull`. |
| [REVIEW-05] | Submit a review with a blank comment is allowed | 1. Login (AUTH-01).<br>2. Submit `{ "rating": 3 }` (comment omitted). | - HTTP 200.<br>- A review is created with a null/empty comment. | [DOC-02] | Untested |  | Comment is optional. |
| [REVIEW-06] | Reject reviewing a private or non-completed document | 1. Login (AUTH-01).<br>2. Submit a review for a PRIVATE/PENDING document. | - HTTP 403 / 404.<br>- No review is created. | [DOC-01] | Untested |  |  |
| [REVIEW-07] | Duplicate review by the same user | 1. Submit a review (REVIEW-01).<br>2. Submit a second review on the same document by the same user. | - Either the existing review is updated, or HTTP 409 is returned (no duplicate row). | [REVIEW-01] | Untested |  | Confirm actual policy in service. |
| [REVIEW-08] | Reject submitting a review without authentication | 1. Send `POST /api/v1/documents/{id}/reviews` with no token. | - HTTP 401.<br>- No review is created. |  | Untested |  |  |

### Function: Get reviews

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [REVIEW-09] | Guests can list reviews of a public document | 1. Send `GET /api/v1/documents/{publicCompletedId}/reviews` with no token. | - HTTP 200.<br>- `data` lists the document's reviews (no auth required). | [REVIEW-01] | Untested |  |  |
| [REVIEW-10] | Listing reviews for a non-public document fails | 1. Send `GET /api/v1/documents/{privateDocId}/reviews`. | - HTTP 404 / 403.<br>- No reviews are returned. | [DOC-01] | Untested |  |  |

---

## SHEET: Report

### Module Information

| Field | Value |
|---|---|
| Module Code | REPORT |
| Test requirement | Verify user-initiated abuse reports: reason validation, public-completed target only, and duplicate handling. |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 6 | 0 | 6 |

### Function: Submit report

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [REPORT-01] | Submit a valid abuse report | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/documents/{publicCompletedId}/reports` with `{ "reason": "Copyright infringement" }`. | - HTTP 200.<br>- A `reports` row is created with status `PENDING`.<br>- Admins receive a `REPORT_SUBMITTED` notification. | [DOC-02] | Untested |  |  |
| [REPORT-02] | Reject a blank report reason | 1. Login (AUTH-01).<br>2. Submit `{ "reason": "   " }`. | - HTTP 400.<br>- `errors.reason` states it is required. | [DOC-02] | Untested |  | `@NotBlank`. |
| [REPORT-03] | Reject a report reason over 1000 characters | 1. Login (AUTH-01).<br>2. Submit `{ "reason": "<1001 chars>" }`. | - HTTP 400.<br>- `errors.reason` states the 1000-character maximum. | [DOC-02] | Untested |  | `@Size(max=1000)`. |
| [REPORT-04] | Reject reporting a non-public document | 1. Login (AUTH-01).<br>2. Submit a report for a PRIVATE document. | - HTTP 403 / 404.<br>- No report is created. | [DOC-01] | Untested |  |  |
| [REPORT-05] | Reject reporting without authentication | 1. Send `POST /api/v1/documents/{id}/reports` with no token. | - HTTP 401.<br>- No report is created. |  | Untested |  |  |
| [REPORT-06] | Duplicate report by the same user on the same document | 1. Submit a report (REPORT-01).<br>2. Submit a second report on the same document by the same user. | - Either an HTTP 409 / "already reported" error, or the existing report is kept (no duplicate row). | [REPORT-01] | Untested |  | Confirm actual policy in service. |

---

## SHEET: Payment

### Module Information

| Field | Value |
|---|---|
| Module Code | PAY |
| Test requirement | Verify VNPay billing: payment URL creation (plan validation), the browser callback redirect, the server-to-server IPN, and the transaction history listing. |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 9 | 0 | 9 |

### Function: Create payment

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [PAY-01] | Create a payment URL for a valid plan | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/payments/create-payment` with `{ "planId": <validPlanId> }`. | - HTTP 200 (bare `PaymentResponse`).<br>- `paymentUrl` is a non-empty VNPay URL.<br>- A pending `invoices` row is created for the user. | [AUTH-01] | Untested |  |  |
| [PAY-02] | Reject create-payment with a null planId | 1. Login (AUTH-01).<br>2. Send `{ }` (no planId). | - HTTP 400.<br>- `errors.planId` states it is required. | [AUTH-01] | Untested |  | `@NotNull`. |
| [PAY-03] | Reject create-payment for an invalid plan | 1. Login (AUTH-01).<br>2. Send `{ "planId": 999999 }`. | - HTTP 400 / 404.<br>- No invoice is created. | [AUTH-01] | Untested |  |  |
| [PAY-04] | Reject create-payment without authentication | 1. Send `POST /api/v1/payments/create-payment` with no token. | - HTTP 401.<br>- No invoice is created. |  | Untested |  |  |

### Function: VNPay callback / IPN

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [PAY-05] | Successful callback redirects to the frontend with `paymentStatus=success` | 1. Create a payment (PAY-01).<br>2. Send `GET /api/v1/payments/vnpay-callback?vnp_ResponseCode=00&vnp_TxnRef=...&vnp_SecureHash=<valid>` (plus required VNPay params). | - HTTP 302 redirect.<br>- Target URL is `<frontend-url>?paymentStatus=success`.<br>- The invoice is marked paid; the user's plan is upgraded; a `PLAN_UPGRADED` notification is written. | [PAY-01] | Untested |  | Requires a valid VNPay signature. |
| [PAY-06] | Failed callback redirects with `paymentStatus=failed` | 1. Create a payment (PAY-01).<br>2. Send the callback with `vnp_ResponseCode=24` (or non-00). | - HTTP 302 redirect.<br>- Target URL is `<frontend-url>?paymentStatus=failed`.<br>- The plan is NOT upgraded. | [PAY-01] | Untested |  |  |
| [PAY-07] | IPN with a valid signature acknowledges with `RspCode=00` | 1. Create a payment (PAY-01).<br>2. Send `GET /api/v1/payments/vnpay-ipn?<all VNPay params with valid hash>`. | - HTTP 200.<br>- Body contains `RspCode=00` (success).<br>- The invoice/plan is updated server-side. | [PAY-01] | Untested |  |  |

### Function: History

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [PAY-08] | List the user's transaction history | 1. Login as a user with ≥ 1 invoice.<br>2. Send `GET /api/v1/payments/history`. | - HTTP 200.<br>- `data` lists invoices for the caller ordered by `createdAt` desc. | [PAY-01] | Untested |  |  |
| [PAY-09] | Reject history without authentication | 1. Send `GET /api/v1/payments/history` with no token. | - HTTP 401.<br>- No history is returned. |  | Untested |  |  |

---

## SHEET: Notification

### Module Information

| Field | Value |
|---|---|
| Module Code | NOTI |
| Test requirement | Verify in-app notifications: listing the user's notifications, marking one as read with ownership checks, and that lifecycle events actually create notifications. |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 6 | 0 | 6 |

### Function: Notifications

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [NOTI-01] | List the user's notifications | 1. Login (AUTH-01) as a user with notifications.<br>2. Send `GET /api/v1/notifications`. | - HTTP 200.<br>- `data` lists only the caller's notifications ordered by `createdAt` desc; each has `type` and `targetId`. | [DOC-02] | Untested |  |  |
| [NOTI-02] | Reject listing notifications without authentication | 1. Send `GET /api/v1/notifications` with no token. | - HTTP 401.<br>- No notifications returned. |  | Untested |  |  |
| [NOTI-03] | Mark own notification as read | 1. Login (AUTH-01).<br>2. Send `PUT /api/v1/notifications/{ownNotificationId}/read`. | - HTTP 200.<br>- The notification `is_read` becomes true. | [NOTI-01] | Untested |  |  |
| [NOTI-04] | Reject marking another user's notification as read | 1. Login as user B.<br>2. Send `PUT /api/v1/notifications/{notificationOfUserA}/read`. | - HTTP 403.<br>- The notification is unchanged. | [NOTI-01] | Untested |  | Ownership check enforced. |
| [NOTI-05] | Marking a non-existent notification returns 404 | 1. Login (AUTH-01).<br>2. Send `PUT /api/v1/notifications/00000000-0000-0000-0000-000000000000/read`. | - HTTP 404.<br>- Nothing changes. | [AUTH-01] | Untested |  |  |
| [NOTI-06] | A document lifecycle event creates a notification | 1. Upload a PUBLIC document (DOC-02).<br>2. As an admin, read `GET /api/v1/notifications`. | - The admin receives a `DOCUMENT_PENDING` notification referencing the new document. | [DOC-02] | Untested |  | Notifications are written synchronously. |

---

## SHEET: Tag

### Module Information

| Field | Value |
|---|---|
| Module Code | TAG |
| Test requirement | Verify tag endpoints: keyword search (public + caller's private), listing all public tags (onboarding), and creating private tags for the caller. |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 5 | 0 | 5 |

### Function: Tags

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [TAG-01] | Search accessible tags by keyword | 1. Login (AUTH-01).<br>2. Send `GET /api/v1/tags/search?keyword=math`. | - HTTP 200.<br>- `data` includes matching PUBLIC tags and the caller's own PRIVATE tags (for autocompletion). | [AUTH-01] | Untested |  |  |
| [TAG-02] | List all public tags | 1. Login (AUTH-01).<br>2. Send `GET /api/v1/tags/public`. | - HTTP 200.<br>- `data` lists only PUBLIC tags (for the onboarding survey). | [AUTH-01] | Untested |  |  |
| [TAG-03] | Create private tags for the caller | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/tags` with `["MyNotes", "Revision"]`. | - HTTP 200.<br>- New PRIVATE tag rows are created under the caller; existing matches (public or caller-private) are returned instead of duplicated. | [AUTH-01] | Untested |  |  |
| [TAG-04] | Reject creating tags without authentication | 1. Send `POST /api/v1/tags` with no token. | - HTTP 401.<br>- No tags are created. |  | Untested |  |  |
| [TAG-05] | Creating with an empty tag list | 1. Login (AUTH-01).<br>2. Send `POST /api/v1/tags` with `[]`. | - HTTP 200 with empty result, or HTTP 400.<br>- No new rows are created. | [AUTH-01] | Untested |  | Confirm actual handling. |

---

## SHEET: AdminDocument

### Module Information

| Field | Value |
|---|---|
| Module Code | ADMDOC |
| Test requirement | Verify admin document moderation: listing pending documents, manual approve/reject, and observable outcomes of the OpenAI auto-moderation triage (auto-approve / auto-reject / leave PENDING). |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 8 | 0 | 8 |

### Function: Moderation

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [ADMDOC-01] | Admin lists pending public documents | 1. Login as ADMIN.<br>2. Send `GET /api/v1/admin/documents/pending`. | - HTTP 200.<br>- `data` lists documents with status `PENDING` (visibility PUBLIC). | [DOC-02] | Untested |  |  |
| [ADMDOC-02] | Non-admin cannot access pending documents | 1. Login as a regular USER.<br>2. Send `GET /api/v1/admin/documents/pending`. | - HTTP 403.<br>- No data is returned. | [AUTH-01] | Untested |  | Path-based `hasRole(ADMIN)`. |
| [ADMDOC-03] | Admin approves a pending document | 1. Login as ADMIN.<br>2. Send `POST /api/v1/admin/documents/{pendingId}/approve`. | - HTTP 200.<br>- Status becomes `PROCESSING`, then `COMPLETED` after the `/index` callback; RAG visibility flips to public; the owner receives `DOCUMENT_APPROVED`; the trending cache is evicted. | [ADMDOC-01] | Untested |  |  |
| [ADMDOC-04] | Approving a non-pending document fails | 1. Login as ADMIN.<br>2. Send approve on a COMPLETED or REJECTED document. | - HTTP 400 / appropriate error.<br>- Document status is unchanged. | [ADMDOC-03] | Untested |  |  |
| [ADMDOC-05] | Admin rejects a pending document with a reason | 1. Login as ADMIN.<br>2. Send `POST /api/v1/admin/documents/{pendingId}/reject` with `{ "rejectionReason": "Violates policy" }`. | - HTTP 200.<br>- Status becomes `REJECTED`; RAG chunks are purged (`DELETE /documents/{id}`); the owner receives `DOCUMENT_REJECTED` with the reason; trending cache evicted. | [ADMDOC-01] | Untested |  |  |
| [ADMDOC-06] | Auto-moderation auto-approves a low-score public document | 1. Ensure `OPENAI_API_KEY` is set (not empty/mock).<br>2. Upload a clearly-safe PUBLIC document (DOC-02).<br>3. Wait for the moderation stream consumer. | - The document moves to `COMPLETED` automatically (max category score < 0.40) without admin action; RAG visibility becomes public. | [DOC-02] | Untested |  | Requires image-moderation flow to complete cleanly. |
| [ADMDOC-07] | Auto-moderation auto-rejects a high-score public document | 1. Ensure `OPENAI_API_KEY` is set.<br>2. Upload a PUBLIC document whose content scores >= 0.80.<br>3. Wait for the consumer. | - The document is auto-rejected (status `REJECTED`); a generated Vietnamese rejection reason is recorded; RAG chunks purged. | [DOC-02] | Untested |  |  |
| [ADMDOC-08] | Auto-moderation leaves a borderline document PENDING | 1. Ensure `OPENAI_API_KEY` is set.<br>2. Upload a PUBLIC document whose max score is between 0.40 and 0.80.<br>3. Wait for the consumer. | - The document stays `PENDING` for manual admin review (appears in `GET /admin/documents/pending`). | [DOC-02] | Untested |  | An image-flow failure also defers to PENDING. |

---

## SHEET: AdminUser

### Module Information

| Field | Value |
|---|---|
| Module Code | ADMUSR |
| Test requirement | Verify admin user management: paginated/filtered user listing, ban (with mass session revocation), reactivate, and warn. |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 7 | 0 | 7 |

### Function: User management

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [ADMUSR-01] | List users with filters and pagination | 1. Login as ADMIN.<br>2. Send `GET /api/v1/admin/users?search=alice&role=USER&status=ACTIVE&page=0&size=10`. | - HTTP 200.<br>- `data.content` lists matching users; pagination metadata is present. |  | Untested |  |  |
| [ADMUSR-02] | Non-admin cannot list users | 1. Login as a regular USER.<br>2. Send `GET /api/v1/admin/users`. | - HTTP 403.<br>- No user data is returned. | [AUTH-01] | Untested |  |  |
| [ADMUSR-03] | Ban a user with a reason | 1. Login as ADMIN.<br>2. Send `POST /api/v1/admin/users/{userId}/ban` with `{ "reason": "Spam" }`. | - HTTP 200.<br>- User status becomes `BANNED`; a violation history row is logged; the user receives `ACCOUNT_BANNED`. |  | Untested |  |  |
| [ADMUSR-04] | Banning revokes all active sessions | 1. Have the target user hold a valid access token.<br>2. Ban the user (ADMUSR-03).<br>3. Use the target's token on a protected endpoint. | - The target's token is rejected (HTTP 401) — every active access token is blacklisted and the refresh token deleted. | [ADMUSR-03] | Untested |  | Uses `active_tokens:{userId}` tracking. |
| [ADMUSR-05] | Reactivate a banned user | 1. Login as ADMIN.<br>2. Send `POST /api/v1/admin/users/{bannedUserId}/reactivate`. | - HTTP 200.<br>- User status returns to `ACTIVE`; an activation history row is logged; the user receives `ACCOUNT_ACTIVATED`. | [ADMUSR-03] | Untested |  |  |
| [ADMUSR-06] | Warn a user | 1. Login as ADMIN.<br>2. Send `POST /api/v1/admin/users/{userId}/warn` with `{ "reason": "Inappropriate content" }`. | - HTTP 200.<br>- A violation history row is logged; the user receives `ACCOUNT_WARNING`. |  | Untested |  |  |
| [ADMUSR-07] | Reject warn with a blank reason | 1. Login as ADMIN.<br>2. Send `POST /api/v1/admin/users/{userId}/warn` with `{ "reason": "   " }`. | - HTTP 400.<br>- `errors.reason` states it is required. |  | Untested |  | `@NotBlank`, max 1000 chars. |

---

## SHEET: AdminReport

### Module Information

| Field | Value |
|---|---|
| Module Code | ADMRPT |
| Test requirement | Verify admin handling of document reports: list reported documents, view report details, resolve (delete document + warn owner), and reject (keep document active). |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 5 | 0 | 5 |

### Function: Report handling

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [ADMRPT-01] | List reported documents | 1. Login as ADMIN.<br>2. Send `GET /api/v1/admin/reports/documents`. | - HTTP 200.<br>- `data` lists documents with pending reports, sorted by report count descending. | [REPORT-01] | Untested |  |  |
| [ADMRPT-02] | View report details for a document | 1. Login as ADMIN.<br>2. Send `GET /api/v1/admin/reports/documents/{documentId}`. | - HTTP 200.<br>- `data` lists the pending report details for that document. | [REPORT-01] | Untested |  |  |
| [ADMRPT-03] | Resolve a report deletes the document and warns the owner | 1. Login as ADMIN.<br>2. Send `POST /api/v1/admin/reports/{reportId}/resolve` with `{ "reason": "Policy violation" }`. | - HTTP 200.<br>- Report status becomes `RESOLVED`; the document is soft-deleted (`DELETED`, `deleted_by_admin=true`, `link_share=null`, storage subtracted); a violation + warning (`DOCUMENT_VIOLATION_DELETED`) is sent to the owner. | [REPORT-01] | Untested |  |  |
| [ADMRPT-04] | Reject a report keeps the document active | 1. Login as ADMIN.<br>2. Send `POST /api/v1/admin/reports/{reportId}/reject`. | - HTTP 200.<br>- Report status becomes `REJECTED`; the document remains active/COMPLETED. | [REPORT-01] | Untested |  |  |
| [ADMRPT-05] | Resolving a non-existent report fails | 1. Login as ADMIN.<br>2. Send `POST /api/v1/admin/reports/00000000-0000-0000-0000-000000000000/resolve`. | - HTTP 404.<br>- Nothing changes. |  | Untested |  |  |

---

## SHEET: AdminDashboard

### Module Information

| Field | Value |
|---|---|
| Module Code | ADMDASH |
| Test requirement | Verify the admin dashboard: platform aggregation stats (with optional date range) and the AI/RAG observability metrics from Langfuse (default 7-day window, fail-open when unconfigured, admin-only). |
| Tester | `<TBD>` |

### Summary

| Pass | Fail | Untested | N/A | Number of Test cases |
|---:|---:|---:|---:|---:|
| 0 | 0 | 6 | 0 | 6 |

### Function: Dashboard

| ID | Test Case Description | Test Case Procedure | Expected Output | Inter-test case Dependence | Result | Test date | Note |
|---|---|---|---|---|---|---|---|
| [ADMDASH-01] | Get dashboard stats with the default window | 1. Login as ADMIN.<br>2. Send `GET /api/v1/admin/dashboard/stats`. | - HTTP 200.<br>- `data` includes user/doc counts, total storage, monthly revenue, and signup stats for the last 30 days. |  | Untested |  |  |
| [ADMDASH-02] | Get dashboard stats with an explicit date range | 1. Login as ADMIN.<br>2. Send `GET /api/v1/admin/dashboard/stats?startDate=2026-06-01T00:00:00&endDate=2026-06-30T23:59:59`. | - HTTP 200.<br>- Signup stats are scoped to the supplied window. |  | Untested |  |  |
| [ADMDASH-03] | Non-admin cannot view dashboard stats | 1. Login as a regular USER.<br>2. Send `GET /api/v1/admin/dashboard/stats`. | - HTTP 403.<br>- No stats are returned. | [AUTH-01] | Untested |  |  |
| [ADMDASH-04] | Get AI metrics for the default 7-day window | 1. Login as ADMIN with Langfuse keys configured.<br>2. Send `GET /api/v1/admin/dashboard/ai-metrics`. | - HTTP 200.<br>- `data.configured` is true; latency/token/cost/citation/route widgets are populated; cached for ~5 min. |  | Untested |  |  |
| [ADMDASH-05] | AI metrics fail-open to an empty payload when Langfuse is unconfigured | 1. With blank Langfuse keys.<br>2. Login as ADMIN.<br>3. Send `GET /api/v1/admin/dashboard/ai-metrics`. | - HTTP 200 (never 5xx).<br>- `data.configured` is false; widgets are empty/zero; `dataAvailable` is false. |  | Untested |  | Fail-open per widget. |
| [ADMDASH-06] | Non-admin cannot view AI metrics | 1. Login as a regular USER.<br>2. Send `GET /api/v1/admin/dashboard/ai-metrics`. | - HTTP 403.<br>- No metrics are returned. | [AUTH-01] | Untested |  |  |

---

## SHEET: Test Report

### Report Information

| Field | Value |
|---|---|
| Project Name | AI Study Hub API |
| Project Code | ASH-API |
| Document Code | ASH-API_Test Report_v1.0 |
| Creator | `<TBD>` |
| Reviewer/Approver | `<TBD>` |
| Issue Date | 2026-07-19 |
| Notes | Release 1 includes 14 modules: Auth, UserProfile, Document, Chat, StudyMaterial, Review, Report, Payment, Notification, Tag, AdminDocument, AdminUser, AdminReport, AdminDashboard. |

### Module Result Summary

| No | Module code | Pass | Fail | Untested | N/A | Number of test cases |
|---:|---|---:|---:|---:|---:|---:|
| 1 | AUTH | 0 | 0 | 21 | 0 | 21 |
| 2 | USER | 0 | 0 | 16 | 0 | 16 |
| 3 | DOC | 0 | 0 | 40 | 0 | 40 |
| 4 | CHAT | 0 | 0 | 12 | 0 | 12 |
| 5 | STUDY | 0 | 0 | 10 | 0 | 10 |
| 6 | REVIEW | 0 | 0 | 10 | 0 | 10 |
| 7 | REPORT | 0 | 0 | 6 | 0 | 6 |
| 8 | PAY | 0 | 0 | 9 | 0 | 9 |
| 9 | NOTI | 0 | 0 | 6 | 0 | 6 |
| 10 | TAG | 0 | 0 | 5 | 0 | 5 |
| 11 | ADMDOC | 0 | 0 | 8 | 0 | 8 |
| 12 | ADMUSR | 0 | 0 | 7 | 0 | 7 |
| 13 | ADMRPT | 0 | 0 | 5 | 0 | 5 |
| 14 | ADMDASH | 0 | 0 | 6 | 0 | 6 |
|  | **Sub total** | **0** | **0** | **161** | **0** | **161** |

### Coverage

| Metric | Value |
|---|---:|
| Test coverage | 0.00% |
| Test successful coverage | 0.00% |

> Coverage is 0.00% because no test case has been executed yet (all `Result = Untested`). Update Pass/Fail counts and recompute after execution:
> Test coverage = (Pass + Fail) / 161 × 100%
> Test successful coverage = Pass / 161 × 100%
