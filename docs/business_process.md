PART 1: ACCOUNT & AUTHENTICATION SUBSYSTEM (AUTHENTICATION & PROFILE)

1. Register (Register an account)

Step 1: Guest enters information (Email, Password, Full Name) on the registration form.

Step 2: System checks whether the Email already exists in the Database (table `users`). If it already exists, return the error "Email already in use".

Step 3: If valid, System hashes the password with bcrypt and creates a new account with `status = 'inactive'`, `role = 'user'`, and assigns the default `plan_id` of the FREE plan (limit **2 GB**).

Step 4: System automatically generates an OTP code (6 digits), saves it to Redis with key `otp:<email>`, **TTL 300 seconds (5 minutes)**, and at the same time sends the code via Email to the Guest.

2. Verify by Email (Verify account)

Step 1: Guest enters the OTP code from Email into the interface or clicks the verification link.

Step 2: System checks the correctness and lifetime (TTL **300 seconds / 5 minutes**) of the OTP code stored at key `otp:<email>`.

Step 3: If the code is valid, System updates `status = 'active'` for the User in table `users`. The Guest officially becomes a User and can log in.

3. Login (Log in)

Step 1: User enters Email and Password, or chooses "Log in with Google" (Google OAuth2).

Step 2:

- If normal login: System checks the Email and compares the Hash Password (bcrypt) in the DB.
- If Google login: System verifies the ID Token from Google and checks `google_id`. If the User has never had an account, System automatically creates a new account with `status = 'active'` and the FREE plan.

Step 3: System checks the `status` of the account. If the status is `banned`, block the login and show an error.

Step 4: If valid, System issues a **JWT pair**:

- **Access Token** — TTL **1 hour**, carries User ID + Role, used for every API request.
- **Refresh Token** — TTL **7 days**, used to rotate (renew) the Access Token via `POST /auth/refresh`.

Client stores the Access Token to establish the session.

4. Logout (Log out)

Step 1: User clicks the Logout button on the interface.

Step 2: Client sends `POST /auth/logout` to the Server. Backend adds the Access Token (and Refresh Token) to the Blacklist (Redis) to invalidate it immediately. Client deletes the stored token.

Step 3: Redirect the user back to the Landing Page.

5. Forgot Password (Forgot password)

Step 1: User enters the Email that needs password recovery on the "Forgot password" screen.

Step 2: System checks whether the Email exists. If it does, generate a reset token (UUID) stored at Redis key `otp:reset:<uuid>`, **TTL 900 seconds (15 minutes)** (same `otp:` prefix as the registration OTP), and send a reset link with the token via Email.

Step 3: User clicks the Link in the Email and enters a new password on the interface.

Step 4: System validates the reset token and the new password, hashes it with bcrypt, and updates the User's `password_hash`. Show a success message and navigate back to the Login page.

6. Edit Profile (Edit profile)

Step 1: User opens the Profile page and changes `full_name` or uploads a new avatar.

Step 2: System validates the data (the avatar must be a real PNG/JPEG — checks magic bytes, size, name length). Uploads the image to S3 to get the URL if the User changes the avatar.

Step 3: Update the corresponding fields in table `users` (`PUT /users/edit-profile`, `POST /users/edit-profile/avatar`) and return the new information to the Client.

PART 2: DOCUMENT MANAGEMENT SUBSYSTEM (DOCUMENT MANAGEMENT)

7. Upload Document (Upload a document)

Step 1: User selects the file to upload. Accepted formats **are only `.pdf`, `.docx`, `.txt`, `.md`** (presentation, audio, video, and zip formats are not accepted). Maximum size **50 MB** per file. At the same time, User selects `visibility` (`private` or `public`), enters `title`, `description`, and assigns `tags[]`.

Step 2: System checks the User's `status`. If `status == 'overlimitstorage'`, block immediately and show a popup asking to delete some documents or renew the plan.

Step 3: System checks the storage quota: gets the `storage_limit` of the current plan (FREE = **2 GB**, Premium = **10 GB**) from table `storage_plans`. If `storage_used + file_size_bytes > storage_limit` → block, return error "Storage full".

Step 4 (Dedup content-hash): System computes the **content-hash** of the file. If a document with the same hash already exists for the same User → return **HTTP 409 Conflict**, do not upload again.

Step 5: Create a `documents` record with `status = 'UPLOADING'` and `visibility` according to the User's choice, and push the file to AWS S3. **Note: distinguish between two separate status fields:**

- `status` (`document_status`, enum of 7 values: `UPLOADING, PROCESSING, PENDING, REJECTED, DELETED, FAILED, COMPLETED`) — the document processing lifecycle.
- `visibility` (`document_visibility`: `private | public`) — the display mode decided by the User, independent of `status`.

Step 6: System routes based on `visibility`:

- **Private**: `status = UPLOADING → PROCESSING`, call RAG service `/process` to extract + index content immediately → callback `SUCCESS` → `COMPLETED`. No moderation.
- **Public**: `status = UPLOADING → PENDING`, call RAG service `/extract` (split into chunks, embedding = NULL) → callback `EXTRACTED` → push into the moderation queue (process 25).

Step 7: Update storage cumulatively: `storage_used = storage_used + file_size_bytes`.

8. Document Tagging (Tag a document)

Step 1: When uploading or editing a document, User selects existing tags (public tags or their own private tags) or types a new tag (`label` ≤ 100 characters).

Step 2: System checks whether the new tag already exists as a public tag. If so, use that public tag. If not, continue checking whether the User already has a private tag with the same name; if not, create a new private tag record (`visibility = 'private'`, `created_by = user_id`) in table `tags`.

Step 3: System records the document–tag relationship in table `document_tags` (`document_id`, `tag_id`).

9. Manage Personal Documents (Manage personal documents)

Step 1: User opens the "My Documents" section via `GET /documents/personal`.

Step 2: System queries table `documents` by `uploader_id = current_user_id`, excluding files where `deleted_at IS NOT NULL` and `status = 'DELETED'`.

Step 3: Return the list of documents with the detailed status of each file (`status` ∈ `UPLOADING/PROCESSING/PENDING/REJECTED/COMPLETED/FAILED` + `visibility`) to render on the interface. **Note:** Even when `status == 'overlimitstorage'`, the User can still list and read personal documents — this status only locks the upload permission.

10. View & Download Document (View and Download a document)

Step 1: User or Guest clicks to select a document.

Step 2: System checks access permission based on `status` + `visibility`:

- A `COMPLETED` + `public` document: **anyone can view** (including Guests) via `GET /documents/{id}/preview` or `GET /documents/shared/{token}` (permitAll).
- A `private` / `PENDING` / `REJECTED` / `PROCESSING` / `UPLOADING` document: **only the owner or Admin** can view the preview.

Step 3 (Download requires login): When clicking "Download", System checks:

- If a **Guest** → show a popup asking to log in.
- If a **User** with permission → System calls `GET /documents/{id}/download`, generates a **presigned S3 URL** (TTL 10 minutes) and **increments the document's `download_count` by 1**.
- Preview/Download of a `private` / `PENDING` / `REJECTED` file is only allowed for the owner or Admin.

Step 4: The system renders and displays the content (PDF, Word, Text, Markdown) in the browser (Web Preview).

11. Search Document (Public search)

Step 1: A person (including Guests) enters a Keyword into the general search bar, calling `GET /documents/search?keyword=`.

Step 2: System runs a SQL `ILIKE` query (case-insensitive) **matching only the two fields `title` and `description`**. This endpoint **does not** scan the full content of the file — there is no FTS index on the document body in this API.

Step 3: The system only returns documents with `status = 'COMPLETED'` + `visibility = 'public'` + `deleted_at IS NULL`.

Step 4: **Real content search** (semantic / full-content search over the document body, BM25 + pgvector + Jina rerank) is in **RAG chat** (see process 20, 21), not in this public search endpoint.

12. Share Document (Share a document link)

Step 1: User clicks "Share" on a document they own (`POST /documents/{id}/share`).

Step 2: System automatically generates a unique Hash/UUID linked to the document and updates it into the `link_share` field in table `documents`.

Step 3: Return a complete URL (`GET /documents/shared/{token}`, permitAll). The link recipient only has view (Read-only) permission. When viewing a shared document, the system hides the owner's private tags and only shows public tags.

13. Edit Document (Edit document information)

Step 1: User clicks "Edit" on a document they own (`PUT /documents/{id}`).

Step 2: User changes the `title`, updates `tags`, `description`, or changes `visibility` (Private → Public).

Step 3: System updates the new data into the DB.

Step 4 (Special — Private → Public): When the User switches `visibility` from `private` to `public`, the document **is already indexed** so no `/extract` round-trip is needed; System sets `status = 'PENDING'` and pushes it directly into the moderation queue (`stream:moderation`) to re-moderate (process 25).

14. Delete Document (Soft delete — Soft Delete)

Step 1: User selects the "Delete" command on a document on the personal management page (`DELETE /documents/{id}`).

Step 2: The system shows a confirmation popup to make sure the User did not click by mistake.

Step 3: When the User confirms, System performs a **soft-delete** in one DB transaction:

- Set `status = 'DELETED'`.
- Save `deleted_at = NOW()`.
- Save `status_before_deletion` = the status right before deletion (serves the Restore in process 15).
- Set `link_share = NULL` (cancel the share link).
- **Do NOT purge S3 / RAG immediately** — the file and vectors are kept for **30 days** (`app.document.retention-days`) for Restore.

Step 4: System recalculates storage: `storage_used = storage_used − file_size_bytes`, immediately freeing up the quota for the User.

Step 5: The document disappears from all public interfaces and the normal "My Documents" section; it only remains in Trash (`GET /documents/trash`).

Step 6: After **30 days**, `DocumentPurgeScheduler` (runs at 03:00 daily) hard-deletes — purges S3 + DB + RAG vectors.

15. Restore Document from Trash (Restore a document from Trash)

Step 1: User goes to the "Trash" section (`GET /documents/trash`), selects a document with `status = 'DELETED'` and clicks "Restore" (`POST /documents/{id}/restore`).

Step 2: System checks the `deleted_by_admin` flag:

- If `deleted_by_admin = true` (deleted by Admin) → **refuse Restore**, return an error. Only Admin can handle these documents.
- If `deleted_by_admin = false` (User deleted it themselves) → allow Restore.

Step 3: System restores the document in one DB transaction:

- Roll `status` back to `status_before_deletion`.
- Clear `deleted_at` (= NULL).
- Restore storage: `storage_used = storage_used + file_size_bytes`. If, after restoring, it exceeds the quota → set the User's `status` to `overlimitstorage` (only locks upload).
- If `visibility = 'public'` and `status_before_deletion = 'COMPLETED'` → generate a new `link_share`.

Step 4: **Do NOT call RAG** in Restore — the file and vectors have not been purged (still within the 30-day window) so they can be reused immediately. The document reappears in "My Documents" and (if public + COMPLETED) in the public library.

16. Bookmark Document (Save a document)

Step 1: User is viewing a `COMPLETED` + `public` document and clicks "Save" (`POST /documents/{id}/save`).

Step 2: System creates a record in table `saved_documents` (`user_id`, `document_id`). If already saved → idempotent (no duplicate).

Step 3: User goes to the "Saved documents" section (`GET /documents/saved`) to view their bookmark list.

Step 4: User can unsave (`DELETE /documents/{id}/unsave`) — delete the record from `saved_documents`.

17. Recommendations (Recommend documents)

Step 1: During onboarding (or on the profile page), User selects favorite tags → saves them into `UserEntity.preferred_tag_ids` (an integer array) via `POST /users/preferred-tags`.

Step 2: User opens the "Recommendations for you" section via `GET /documents/recommendations`. The endpoint requires `preferred_tag_ids` to be non-empty.

Step 3: System queries documents that are `COMPLETED` + `public` and have tags matching `preferred_tag_ids`, **limited to a maximum of 100 candidate ids**, then paginates the results returned to the Client.

Step 4: In addition there is `GET /documents/trending` (Redis cache 10 minutes) — recommendations based on views / downloads / rating score.

PART 3: EXTENSION SUBSYSTEM - SOCIAL LEARNING (COMMUNITY INTERACTION)

18. Review & Rate Document (Rate a document)

Step 1: User (logged in) opens a public `COMPLETED` document (`POST /documents/{id}/reviews`).

Step 2: User selects a star rating (1–5 stars) and enters a comment, then clicks Send.

Step 3: System validates the information and creates a new record in table `reviews` (`user_id`, `document_id`, `rating`, `comment`). Recalculates the document's average rating to feed into the Trending algorithm.

Step 4: System generates a `NEW_REVIEW` notification to the document owner (process 33).

19. Report Document (Report a violation)

Step 1: User finds a public document containing prohibited content, a confidential exam, or inappropriate language, and clicks "Report violation" (`POST /documents/{id}/reports`).

Step 2: User enters the report reason (`reason`) and sends it to the system.

Step 3: System creates a record in table `reports` with the default status `status = 'pending'`, and at the same time generates a `REPORT_SUBMITTED` notification to the Admin. The document keeps its status, waiting for the Admin to resolve (process 26).

PART 4: AI CHATBOT SUBSYSTEM (RAG ARCHITECTURE)

20. AI Chatbot in My Documents (Query by document group)

Step 1: User opens the My Documents page, selects one or more specific documents as the background dataset (Context Window), then enters a question in the chat box (`POST /chat`).

Step 2: [AI Guard Middleware] Backend checks the daily AI quota via Redis key `user:ai_limit:{userId}:{yyyy-MM-dd}`. If it has exceeded the `max_ai_requests_per_day` of the plan (FREE **15/day**, Premium **500/day**) → **HTTP 429**, the counter **does not increase**.

Step 3: If quota remains, System `INCR`s the Redis counter (if the key is newly created → set TTL **24h = 86400 seconds**), checks / creates a ChatSession in `chat_sessions`, and saves the list of selected documents into `session_documents`.

Step 4: System sends the question to ChatbotService (RAG service). RAG vectorizes the question, performs hybrid retrieval (BM25 + pgvector), Jina rerank top-5, **only within the scope of the documents selected in Step 1**.

Step 5: LLM (Gemini 2.5 Flash-Lite) synthesizes the answer with citation information (file name, page number, original text snippet).

Step 6: System saves the User's question (`sender = user`) and the Bot's answer (`sender = bot`) into table `chat_messages`; citations are stored in the `citations` field as jsonb.

Step 7: Return the response to the Client. The quota was already incremented in Step 3.

21. AI Chatbot in View Document (Q&A & Summary of a single document)

Step 1: User has a specific document open and enters a question (for example: "Summarize this document for me").

Step 2: [AI Guard Middleware] Check the daily quota the same way as process 20; if over the limit → 429.

Step 3: The system sends the request through ChatbotService to handle RAG, but limits the retrieval scope **to exactly the ID of the document being viewed**.

Step 4: LLM generates the summary/answer with specific cited sources.

Step 5: Save the message to the DB; the quota was already incremented in Step 2. Return the result to the chat interface.

22. Manage Chat History (Manage Chat history)

Step 1: User clicks the "Chat history" section (`GET /chat/sessions`).

Step 2: System queries table `chat_sessions` filtered by `user_id = current_user_id` and `deleted_at IS NULL`, displaying the list of old conversations.

Step 3: When User clicks a specific session (`GET /chat/sessions/{id}/messages`), System gets all messages from `chat_messages` sorted by `created_at` ascending to display the conversation content again.

Step 4: User can rename the conversation title (`PATCH /chat/sessions/{id}`) or choose Delete conversation (`DELETE /chat/sessions/{id}` — soft-delete, updates `deleted_at`).

23. Quiz / Flashcard Generation (Generate study materials — FEAT-AI-MAT)

Step 1: User selects a specific document (`COMPLETED`, with access permission) and clicks "Generate Quiz" or "Generate Flashcard" (`POST /study-materials/quiz`, `POST /study-materials/flashcard`).

Step 2: [AI Guard Middleware] Check the daily AI quota — **shared with chat** (same Redis counter `user:ai_limit:{userId}:{date}`, FREE 15/day, Premium 500/day). System **INCRs before calling RAG**; if over the limit → **HTTP 429**, do not generate.

Step 3: System calls RAG service `/quiz/generate` or `/flashcard/generate` with `document_id`. RAG reads the document's chunks and uses the LLM to generate a set of questions/cards.

Step 4 (Refusal): If the document is too short or lacks context → RAG returns a refusal: API returns **HTTP 200**, **empty list**, with a `reason` in the message (for example "The document is too short to generate questions").

Step 5: If successful → return JSON containing the quiz (questions + answers + explanations) or flashcard (front / back) to the Client. The quota was already incremented in Step 2.

PART 5: ADMIN SUBSYSTEM (ADMIN DASHBOARD)

24. Approve/Reject Public Document (Approve document — manual fallback)

Step 1: Admin opens the admin page and views `GET /admin/documents/pending` (list of documents with `status = 'PENDING'`).

Step 2: Admin previews the document content and checks validity.

Step 3 — **Approve** (`POST /admin/documents/{id}/approve`):

- `status = PENDING → PROCESSING`.
- `PATCH /visibility = public` (formalize the visibility).
- `POST /index` (RAG embeds the pending chunks).
- RAG callback `SUCCESS` → `status = COMPLETED`. The document goes to the public library.
- Generate a `DOCUMENT_APPROVED` notification to the owner.

Step 4 — **Reject** (`POST /admin/documents/{id}/reject` with body `rejectionReason`):

- `status = REJECTED`.
- Call `DELETE /documents/{id}` on the RAG service (purge chunk vectors, `@Async deleteVectors`).
- The document is hidden from the public library but still shown in the User's personal section with the reason.
- Generate a `DOCUMENT_REJECTED` notification to the owner.

Step 5: This is a **manual fallback**. Most PENDING documents are already handled automatically by Auto-Moderation (process 25) — Admin only reviews the "gray" zone 0.40–0.80 and cases where AI is not working.

25. Auto-Moderation (Auto content moderation — OpenAI + Redis Streams)

Step 1 (Two trigger points): The moderation worker receives messages from Redis Stream `stream:moderation` (consumer group `moderation-cg`, manual ACK). There are **2 points** that push documents into the stream:

- (a) RAG callback `EXTRACTED` after a new public upload.
- (b) `updateDocument` switching `visibility` from `PRIVATE → PUBLIC`.

Step 2 (Consumer): A worker from consumer-group `moderation-cg` takes a message and calls `AutoModerationService.process(documentId)`. Idempotent — no-op when `status != PENDING`.

Step 3 (Collect moderation input):

- Read text chunks from `document_chunks` (read-only, owned and written by the RAG service).
- Extract embedded images from the original S3 file via `DocumentImageExtractor` (PDFBox for PDF, POI for DOCX) → base64 → `image_url` input.

Step 4 (Call OpenAI Moderation API `omni-moderation-latest`): text batch ≤ 30, images in smaller batches. Take the **max category score** across all chunks + images.

Step 5 (Triage 3 zones):

- **Score ≥ 0.80** → auto-reject (`rejectDocument(id, generatedReason)`): `status = REJECTED` + purge RAG vectors. Generate `DOCUMENT_REJECTED`.
- **Score < 0.40** (and `imagesChecked = true`) → auto-approve (`approveDocument(id)`): `status = COMPLETED`, goes to the public library. Generate `DOCUMENT_APPROVED`.
- **0.40 ≤ Score < 0.80** → keep `PENDING`, wait for manual Admin review in process 24.

Step 6 (Error handling / DLQ): Text-moderation fail → propagate (message unacked → retry). After **5** failures → push to **DLQ**. Image-flow fail → defer to PENDING. Skip (keep PENDING) when `openai.api-key` is empty / `mock_key` or chunks are empty.

Step 7 (PEL reclaim): A scheduled job (60-second cycle) reclaims idle messages in the stream (Pending Entry List) for another consumer to process again.

26. Handle Document Reports (Handle violation reports)

Step 1: Admin opens the "Report Management" section and views the list of reports with `status = 'pending'` (`GET /admin/reports/documents`).

Step 2: Admin checks the details of the reason and the reported document (`GET /admin/reports/documents/{id}`) to resolve:

- If the report is correct (`POST /admin/reports/{id}/resolve`): change `reports.status = 'resolved'`, downgrade the document to `REJECTED` or `DELETED`. Record 1 violation history of the User who uploaded the file into `violation_histories`. Generate a `DOCUMENT_VIOLATION_DELETED` notification to the owner.
- If the report is wrong (`POST /admin/reports/{id}/reject`): change `reports.status = 'rejected'` (dismiss the report), the document keeps its public status.

27. Warn / Ban User (Warn or Ban an account)

Step 1: Admin opens the User management page (`GET /admin/users`) and checks the violation history in table `violation_histories`.

Step 2: Admin performs an action:

- **Warn** — `POST /admin/users/{id}/warn` (body `reason` required): send a system notification and a reminder email to the User. Generate `ACCOUNT_WARNING`.
- **Ban (Lock account)** — `POST /admin/users/{id}/ban`: update `status = 'banned'` in table `users`.
- **Reactivate (Unlock)** — `POST /admin/users/{id}/reactivate`: reopen the account. Generate `ACCOUNT_ACTIVATED`.

Step 3 (Mass-blacklist on Ban): When a User is Banned, System performs a **mass-blacklist of all active access tokens** of the User (via Redis key `active_tokens:{userId}`). A User who is interacting on the Web will **be kicked out to the login screen immediately**; every subsequent request using an old token is rejected by the Backend (401). Generate an `ACCOUNT_BANNED` notification.

28. View System Statistics (View system statistics)

Step 1: Admin clicks the Overview / Statistics screen (`GET /admin/dashboard/stats`).

Step 2: System runs Count/Sum/Group By queries to calculate: total new users, total successfully uploaded documents, cloud storage consumed, and revenue of plans by month. Render the data as charts.

PART 6: MONETIZATION & PAYMENT SUBSYSTEM (SUBSCRIPTION & PAYMENT)

29. Buy Premium Subscription (Register / Renew the Premium plan — VNPay)

Step 1: User opens the plan Dashboard and clicks to register for the "PREMIUM" plan (30-day cycle).

Step 2: System creates a new invoice record in table `invoices` with `status = 'pending'` and saves the Premium `plan_id`.

Step 3: System calls `POST /payments/create-payment` → the **VNPay** payment gateway (the only payment gateway, signs HMAC-SHA512 via `VNPayUtil`). The endpoint returns a **VNPay payment URL** containing the exact amount and transaction code.

Step 4: User is redirected to the VNPay payment page to complete the transaction.

30. Payment Automation Processing (Handle VNPay IPN + Callback)

Step 1: User completes the payment on the VNPay gateway.

Step 2: VNPay fires **two streams** back to the Backend:

- **`GET /payments/vnpay-ipn`** (server-to-server IPN): VNPay calls the server directly. Backend **verifies the HMAC-SHA512 signature**; if wrong → reject.
- **`GET /payments/vnpay-callback`**: **302 redirect** the browser back to the frontend with the result (success/failure) so the UI can update.

Step 3: Backend matches `vnp_TxnRef` with the invoice code in the DB and checks the amount `vnp_Amount`.

Step 4: If the transaction is valid and successful, System performs an **atomic** sequence of actions (one DB transaction):

- Update invoice status: `invoices.status = 'success'`.
- Update the User in table `users`: change `plan_id` to the Premium plan code, set `plan_expires_at = NOW() + INTERVAL '30 days'`. If the User is currently at `status = 'overlimitstorage'` → automatically revert to `'active'`.
- Create a record in `notifications` of type `PLAN_UPGRADED` to notify the User of the successful upgrade.

Step 5: If the transaction fails → `invoices.status = 'failed'`, do not change the plan.

31. Reset daily AI quota (Lazy Update mechanism + Redis Counter)

Description: Instead of running a Cron Job that scans the entire Database at midnight and causes congestion, the AI quota (chat + quiz + flashcard, **shared in one counter**) is managed entirely through Redis and **reset lazily** based on the key TTL. There is no midnight cron scanning the DB.

Step 1: User sends an AI request (chat on the My Documents page / View Document page, or generate quiz/flashcard).

Step 2: [AI Guard Middleware] Backend checks the quota via Redis key `user:ai_limit:{userId}:{yyyy-MM-dd}`.

Step 3: Backend runs `INCR` on that key in Redis:

- Case 1 (First request on a new day): The key does not exist → Redis automatically creates the key with value 1. Backend immediately sets TTL **24h (= 86400 seconds)** for this key.
- Case 2 (Subsequent requests): The key exists → Redis adds up to 2, 3, 4... and returns the current count.

Step 4: Backend compares the returned count with the `max_ai_requests_per_day` of the plan (FREE **15/day**, Premium **500/day**):

- If **over the limit** → block the request, **HTTP 429**, do not send to the LLM, **counter rollback (does not increase)**, return the message "You have run out of AI turns for today, please upgrade your plan".
- If within the limit → allow it to continue, forwarding the question to ChatbotService to handle RAG.

Step 5: Client views the remaining quota via `GET /chat/quota`. When the day ends, the key auto-expires by TTL → "reset" lazily; no midnight cron is needed.

Step 6 (Background sync — optional): The system may run a periodic Background Worker to sync the count from Redis to `ai_requests_today` in the DB to keep logs/statistics for the Admin, without ever blocking the User's main flow.

32. Check Subscription plan expiration (Scheduler + Lazy Downgrade)

Description: The system uses **both mechanisms** to handle an expired Premium plan — combining a proactive scheduler and lazy downgrade when the User interacts.

Step 1 — **(a) Proactive scheduler**:

- `PlanExpirationScheduler` (runs at 08:00 daily): scans Users whose Premium plan expires within 3 days → generates a `PLAN_EXPIRING` notification.
- `PlanDowngradeScheduler` (runs at 08:00 daily): proactively downgrades Users with `plan_id = Premium` and `plan_expires_at < NOW()` to the FREE plan.

Step 2 — **(b) Lazy downgrade**: When the User performs any action that sends a Request to the system (re-login, refresh the Dashboard, or click Upload), the Middleware quickly checks the condition: `if (user.plan_id != FREE AND user.plan_expires_at < NOW())`. If true:

- Change `plan_id` back to the FREE plan.
- Reset `plan_expires_at = NULL`.

Step 3 (Check and apply overlimit): The system immediately compares `storage_used` with the `storage_limit` of the FREE plan (2 GB):

- If `storage_used > storage_limit_free` → update `users.status = 'overlimitstorage'`.
- If `storage_used ≤ storage_limit_free` → keep `status = 'active'`.

Step 4 (Lock scope): The `overlimitstorage` status **only locks upload** — the User can still list/read their own documents, still use chat/AI (if quota remains), and still use other features. It does not lock access to "My Documents" (unlike an old note).

Step 5: The system creates a new notification record in `notifications` (type `PLAN_EXPIRING` or a downgrade notification) so the User knows the new account status.

Step 6: UI/UX updates according to the new account status (lock the Upload button if `overlimitstorage`).

PART 7: NOTIFICATIONS SUBSYSTEM (NOTIFICATIONS)

33. Notifications (In-app notifications)

Step 1: System **synchronously generates** records in table `notifications` at many business points, each type corresponding to a `type`:

- `DOCUMENT_PENDING`: a document enters the moderation queue → notify the Admin.
- `DOCUMENT_APPROVED` / `DOCUMENT_REJECTED`: moderation result (auto in process 25 or manual in process 24) → notify the owner.
- `DOCUMENT_RESTORED`: owner successfully restores from Trash (process 15) → notify the owner.
- `NEW_REVIEW`: another User rates a public document (process 18) → notify the owner.
- `REPORT_SUBMITTED`: User reports a document (process 19) → notify the Admin.
- `DOCUMENT_VIOLATION_DELETED`: Admin handles a report and deletes the document (process 26) → notify the owner.
- `PLAN_UPGRADED`: VNPay payment successful (process 30) → notify the User.
- `PLAN_EXPIRING`: Premium plan expires within 3 days (process 32) → notify the User.
- `ACCOUNT_BANNED` / `ACCOUNT_WARNING` / `ACCOUNT_ACTIVATED`: sanction or reopen an account (process 27) → notify the User.

Step 2: User opens the Notifications section (`GET /notifications`) to view their notification list.

Step 3: User clicks to view/read a notification → System marks it as read via `PUT /notifications/{id}/read`.

Step 4: Unread notifications show a number badge on the UI; read notifications are visually distinguished.
