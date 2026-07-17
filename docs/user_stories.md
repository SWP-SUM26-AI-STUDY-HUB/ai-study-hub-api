# User Stories: AI Study Hub

This document maps the refined functional requirements of the AI Study Hub project into agile, value-driven User Stories. Each story follows the format **"As a [Role], I want to [Action], So that [Value]"** and cross-references its source functional requirement (F-\*) per the canonical ID scheme.

**Roles used:** Guest (unauthenticated visitor), User (authenticated member), Admin (platform operator).

---

## 1. Authentication & Profile Management (F-AUTH)

- **US-AUTH-01 (Account Registration):**
  As a Guest, I want to register a new account using my Email Address, Full Name, and Password, So that I can access the system's personalized 2 GB storage and study tools.
- **US-AUTH-02 (Email Verification):**
  As a Guest, I want to verify my email using a one-time password (OTP) sent to my inbox (valid for 5 minutes), So that I can activate my account and log in securely.
- **US-AUTH-03 (Account Authentication):**
  As a Guest, I want to log in using my credentials or my Google account and receive a short-lived JWT access token (1 hour) plus a rotating refresh token (7 days), So that I can securely access my personal dashboard and materials without re-entering my password on every request.
- **US-AUTH-04 (Session Termination):**
  As a User, I want to log out of my active session (which blacklists my current access token), So that I can prevent others from accessing my account on a shared device.
- **US-AUTH-05 (Password Recovery):**
  As a User, I want to request a password reset email and set a new password using the reset token (valid for 15 minutes), So that I can recover access to my account if I forget my credentials.
- **US-AUTH-06 (Profile Customization):**
  As a User, I want to update my display name, set my onboarding tag preferences, and upload a profile picture, So that I can personalize my profile and receive relevant content recommendations.

---

## 2. Document Management & Storage (F-DOC)

- **US-DOC-01 (Document Upload):**
  As a User, I want to upload files up to **50 MB** in **`.pdf`, `.docx`, `.txt`, or `.md`** format, with duplicate uploads detected by content hash, So that I can store them in my personal cloud workspace without re-uploading identical files.
- **US-DOC-02 (Document Tagging):**
  As a User, I want to attach tags (public or private) to my documents, So that I can easily group and categorize my study materials by subject, chapter, or topic.
- **US-DOC-03 (Personal Document Library):**
  As a User, I want to view all my uploaded documents and see their approval status, So that I can manage file privacy and monitor my storage usage. Note: if my account enters `overlimitstorage`, I can still view, read, and manage my existing documents — only new uploads are blocked until I free space or upgrade.
- **US-DOC-04 (In-Browser Preview & Download):**
  As a User, I want to preview PDF and Word files directly in the browser and download any of my files via a time-limited link, So that I can read my materials immediately and keep local copies when needed.
- **US-DOC-05 (Keyword Search):**
  As a User or Guest, I want to search the public library by **title or description keyword** (case-insensitive match), So that I can quickly find documents relevant to my topic. *(For deep, semantic question-answering over a document's full content, see US-AI-01 / US-AI-02 — the RAG chat.)*
- **US-DOC-06 (File Sharing Link):**
  As a User, I want to generate a unique read-only sharing URL for my documents, So that I can share them with other students without changing the file's ownership.
- **US-DOC-07 (Metadata Update):**
  As a User, I want to update my document's title, tags, and privacy settings, So that I can maintain accurate information and control who has access to my documents. *(Switching a document from private to public re-triggers the moderation flow.)*
- **US-DOC-08 (Document Soft-Deletion):**
  As a User, I want to soft-delete my files (moving them to Trash, freeing my storage immediately), So that I have a safety period to recover them before they are permanently purged after **30 days**.
- **US-DOC-09 (Restore from Trash):**
  As a User, I want to restore a soft-deleted document from the Trash within the 30-day retention window, So that I can recover files I removed by mistake and return them to their previous status and visibility.
- **US-DOC-10 (Bookmark):**
  As a User, I want to save (bookmark) public documents and view my saved list, So that I can quickly revisit useful materials without searching for them again.
- **US-DOC-11 (Personalized Recommendations):**
  As a User, I want public documents recommended based on my onboarding tag preferences, So that I can discover relevant study materials tailored to my subjects of interest.

---

## 3. Social Learning & Interaction (F-SOC)

- **US-SOC-01 (Review and Rating):**
  As a User, I want to rate public documents with stars and write comments, So that I can share my feedback on their quality and help other students find useful materials.
- **US-SOC-02 (Content Reporting):**
  As a User, I want to report public documents that violate copyright policies or contain inappropriate content, So that I can help maintain a safe and legal shared library.

---

## 4. Contextual AI Assistant (F-AI-RAG)

- **US-AI-01 (Multi-Document Chat):**
  As a User, I want to select multiple documents and ask questions to the AI chatbot, So that I can get context-specific answers with page-number citations synthesized across my files.
- **US-AI-02 (Single-Document Summary):**
  As a User, I want to request summaries and ask questions about a single open document, So that I can understand long papers or notes in a fraction of the time.
- **US-AI-03 (Chat History Management):**
  As a User, I want to view my past chat sessions, rename them, or delete them, So that I can refer back to previous study sessions and keep my chat history organized.
- **US-AI-04 (Study-Material Generation):**
  As a User, I want to auto-generate quizzes and flashcards from a document, So that I can self-test and memorize key concepts faster. *(Quiz, flashcard, and chat requests share a single daily AI quota — 15/day on Free, 500/day on Premium. When the quota is exhausted the request is rejected with HTTP 429; if the model refuses to produce content, the API returns an empty list with a reason message rather than an error.)*

---

## 5. Content Moderation (F-MOD)

- **US-MOD-01 (Auto-Moderation Transparency):**
  As an Admin, I want the system to auto-triage public uploads through the OpenAI Moderation API (auto-approve clear content, auto-reject clear violations) and only surface borderline cases for manual review, So that I can focus my attention on genuinely ambiguous material instead of reviewing every upload by hand.

---

## 6. Admin Dashboard (F-ADM)

- **US-ADM-01 (Content Moderation):**
  As an Admin, I want to review the queue of borderline (pending) public documents and approve or reject them, So that I can make the final call on ambiguous content that the auto-moderation service could not decide.
- **US-ADM-02 (Abuse Report Management):**
  As an Admin, I want to resolve or reject reports against public files, So that I can enforce the platform's terms of service and remove illegal uploads.
- **US-ADM-03 (Account Penalization):**
  As an Admin, I want to warn or ban user accounts that violate platform policies (and reactivate them when appropriate), So that I can maintain a safe environment for all learners.
- **US-ADM-04 (System Overview Reports):**
  As an Admin, I want to view charts showing user growth, storage usage, and subscription revenue, So that I can monitor the system's performance and monthly business growth.

---

## 7. Subscriptions & Payments (F-MON)

- **US-MON-01 (Tier Upgrade):**
  As a User, I want to select a Premium plan and pay through **VNPay** (receiving a VNPay payment URL and an automated, webhook-confirmed transaction), So that I can upgrade to 10 GB of storage and 500 daily AI requests.
- **US-MON-02 (AI Usage Awareness):**
  As a User, I want to check my daily AI request count (shared across chat, quiz, and flashcard) and be warned when I approach my quota limit, So that I can budget my usage or decide to upgrade my subscription.
- **US-MON-03 (Subscription Expiry Warning):**
  As a User, I want to be notified before my Premium plan expires and guided if my account is downgraded and exceeds the Free storage limit, So that I know how to clear space or renew my subscription to regain full access.

---

## 8. In-App Notifications (F-NOT)

- **US-NOT-01 (Notifications):**
  As a User, I want to receive in-app notifications about my documents' moderation outcome (pending/approved/rejected/restored), new reviews on my documents, submitted reports, plan upgrades and expiries, and account-status changes (banned/warned/activated), So that I stay informed about important events without having to check each screen manually.
