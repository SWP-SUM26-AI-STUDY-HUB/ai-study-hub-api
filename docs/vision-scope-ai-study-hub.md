# VISION AND SCOPE DOCUMENT

## PROJECT: AI-POWERED STUDY DOCUMENT MANAGEMENT SYSTEM (AI STUDY HUB)

---

## 1. BUSINESS REQUIREMENTS

### 1.1. Business Background
In modern university environments, the volume of study and research documents for students is growing rapidly. These materials include e-textbooks, lecture notes, reference materials, past exams, group assignments, and personal study notes.

However, students face inefficient information management because their files are scattered across too many platforms such as Google Drive, Zalo, Messenger, Facebook Groups, personal emails, and local physical drives (USB, hard drives). This fragmentation leads to lost files, wasted time searching for materials, and a lack of knowledge transfer between student cohorts, which directly harms study efficiency and exam preparation.

### 1.2. Business Opportunity & Problem Statements
AI Study Hub was created to solve the core pain points of students and learners through a comprehensive technology solution:

| Current Problem | Practical Impact | Solution by AI Study Hub |
| :--- | :--- | :--- |
| **Scattered Data** | Users waste 15-30 minutes searching for old files; links and files are easily lost when Google Drive links are deleted or Zalo/Messenger chats are cleared. | A centralized cloud repository that allows scientific organization by subjects and flexible tagging, with content-hash dedup so the same file is never stored twice. |
| **Inefficient Discovery** | Users can only browse by file name, making it hard to surface relevant materials across a large library. | A fast **keyword search** across document titles and descriptions (case-insensitive) for quick discovery, plus **trending** and **tag-based recommendations**. Deep, semantic retrieval of concepts hidden inside documents is delivered through the **RAG chatbot** (BM25 + vector search), not the public search endpoint. |
| **Information Overload** | Students waste hours reading long PDFs to find a single formula, concept, or key point. | An AI Chatbot powered by **RAG (Retrieval-Augmented Generation)** to summarize and answer questions directly based on documents, with precise citations to file name and page. |
| **Passive Reading** | Students read documents end-to-end without actively testing their understanding. | **AI Study-Material Generation** that produces structured **quizzes** and **flashcards** from a document to support active recall. |
| **Manual & Fragmented Sharing** | Sharing is temporary via chat applications, lacking structured knowledge transfer between student generations. | A **Public Library** organized by author/university/major, with automated content moderation, community ratings, bookmarks, and abuse reporting. |
| **Hardware & Cost Limits** | Personal device storage is limited; upgrading personal cloud drives (Google One, iCloud) is expensive for students. | Optimized cloud storage (AWS S3) with flexible monetization plans (Free & Premium) designed for students. |

#### Comparison with existing market solutions:
*   **Google Drive / OneDrive / Dropbox:** Only offer static file storage and search based on file names or basic metadata. They do not have built-in AI assistants that can read, understand, and chat directly with documents.
*   **Zalo / Messenger / Facebook Groups:** Files expire and are deleted automatically after a short period (especially Zalo). They lack clear folder structures and structured discovery.
*   **Traditional document-sharing websites (tailieu.vn, 123doc, etc.):** Complicated paid download models, low-quality/spam documents, and a complete lack of interactive AI tools for personalized learning.

### 1.3. Business Objectives
To establish the practical value of the project, the business objectives are defined quantitatively as follows:
*   **Data Centralization:** Migrate and consolidate at least 80% of target students at partnered universities from scattered storage platforms to AI Study Hub within the first 6 months of operation.
*   **Efficiency Boost:** Reduce document search times by 70% and reduce reading/summarizing times for long academic papers by 50% for active users.
*   **Community Knowledge Sharing:** Build a shared public library with at least 5,000 high-quality, moderated academic documents in the first year.
*   **Sustainable Monetization:** Achieve a minimum conversion rate of 5% from free users to Premium subscribers within 6 months of launching payment features, ensuring positive cash flow for system operations.

### 1.4. Success Metrics
Project success will be measured by two sets of core metrics:

#### 1.4.1. System Metrics
*   **AI Latency:** Response time for the RAG-based AI Chatbot on typical study documents must be under 5 seconds.
*   **AI Accuracy:** Citation accuracy must exceed 90% to prevent AI hallucination outside the scope of the selected documents.
*   **Keyword Search Speed:** Document search queries (title + description, case-insensitive) must return matches in under 1.5 seconds.
*   **Preview Speed:** Online document preview rendering must load in under 3 seconds.
*   **Study-Material Generation:** Quiz and flashcard generation must complete within a responsive, user-acceptable window; each request counts against the shared daily AI quota.

#### 1.4.2. Business Metrics
*   **User Engagement:** Reach a minimum of 1,000 Monthly Active Users (MAU) within 3 months of deployment.
*   **Payment Automation:** 100% automated subscription upgrades via **VNPay** (sandbox IPN webhook + browser callback redirect) without any manual intervention.
*   **Retention Rate:** Premium subscription renewal rate must exceed 60% per 30-day billing cycle.

### 1.5. Vision Statement
The long-term vision of the AI Study Hub project focuses on three core actors, delivering unique value to each of them:

*   **For the Guests (Unauthenticated Visitors):** AI Study Hub serves as an open, welcoming portal that showcases the power of AI-assisted learning. Through a beautiful landing page and interactive mockups, guests can quickly explore key features, understand the benefits of AI-assisted document chat, and easily register for a free account via a seamless 1-click Google OAuth2 sign-in. The platform aims to convert curious visitors into active learners instantly.
*   **For the Users (Students & Learners):** AI Study Hub is an intelligent, personalized study companion. Unlike static cloud storage tools, it empowers users to centralize all their learning materials, discover documents by keyword and tag, bookmark the ones they want to revisit, and directly converse with single documents or entire folders. The RAG-powered chatbot reads their notes and textbooks to provide instant summaries, answers, and precise citations, and can even turn a document into quizzes and flashcards — turning passive files into active knowledge and saving hours of exam preparation.
*   **For the Admins (System Administrators):** AI Study Hub provides a comprehensive, secure, and largely automated management center. Public uploads are triaged automatically by the OpenAI Moderation API (auto-approve / auto-reject / escalate to manual review), so admins focus only on borderline cases and community abuse reports. Admins can also manage users (warn/ban/reactivate), resolve copyright reports, and track system health and subscription revenue. By automating payments (VNPay) and content moderation, the system minimizes operational overhead, allowing admins to concentrate on quality control and community safety.

### 1.6. Business Risks
1.  **Copyright & Legal Risks:** Users might upload copyrighted books or confidential school exam papers to the Public Library.
    *   *Mitigation:* A **hybrid moderation workflow**. Every public upload (and every private→public visibility change) is scored automatically by the OpenAI Moderation API: a max category score **≥0.80** is auto-rejected, **<0.40** is auto-approved, and the **0.40–0.80** band is escalated to manual admin review. Admins retain full manual approve/reject control. A community **Report** tool supports rapid copyright takedown requests, and sanctioned accounts receive in-app notifications.
2.  **AI API Consumption Costs:** Using commercial LLM APIs (Gemini, OpenAI) can lead to extremely high costs if user requests spike without control.
    *   *Mitigation:* A strict **daily AI quota** enforced per user via Redis (shared counter across chat, quiz, and flashcard generation; overflow returns HTTP 429 without consuming quota). Quotas differ by plan (Free = 15/day, Premium = 500/day), answers are cached where feasible, and prompt token sizes are optimized.
3.  **User Adoption Resistance:** Students are used to old tools (Google Drive, Zalo) and might be reluctant to migrate.
    *   *Mitigation:* Design a modern, clean, and simple user experience (UX) with 1-click Google Login. Highlight the unique RAG chatbot and study-material generation features that general cloud storage platforms do not support.
4.  **Payment Processing Failures:** Network issues could prevent the VNPay server-to-server IPN from reaching the backend, causing subscription delays for Premium users.
    *   *Mitigation:* Rely on VNPay's signed (HMAC-SHA512) IPN as the source of truth, expose a transaction history dashboard for users, and run scheduled reconciliation jobs (plan-expiry notifications at 08:00 and a daily downgrade sweep) so missed webhooks are corrected within the same billing day.

### 1.7. Business Assumptions & Dependencies
*   **Assumptions:**
    *   Target users (students) own at least one personal device (computer or smartphone) with stable internet access and use modern web browsers (Chrome, Safari, Edge).
    *   Students are willing to share high-quality study documents with the community in exchange for reputation or short-term premium perks.
*   **Dependencies:**
    *   **LLM / Embedding API Providers (Gemini / OpenAI):** RAG chat, embeddings, quiz/flashcard generation, and automated moderation availability, speed, and pricing depend directly on these providers.
    *   **VNPay Payment Gateway:** Automated subscription upgrades depend on VNPay's sandbox API, IPN webhooks, and callback redirects.
    *   **External RAG Microservice:** Semantic retrieval and answer generation are served by a separate FastAPI microservice (`ai-study-hub-rag-service`) that this backend calls asynchronously.

---

## 2. SCOPE & LIMITATIONS

### 2.1. Major Features
To track and manage requirements, each major feature is labeled with a unique ID (consistent across all engineering documents):

*   **FEAT-AUTH: Authentication & Account Management**
    *   Email registration with OTP verification (OTP valid for 5 minutes).
    *   Secure login using JSON Web Tokens — short-lived **access token (1 hour)** plus a rotating **refresh token (7 days)**.
    *   Fast login integration via Google OAuth2.
    *   Forgot-password / reset-password flow (reset token valid for 15 minutes).
    *   Profile management (basic info, avatar upload, password changes).
*   **FEAT-DOC: Document Management & Categorization**
    *   Uploads limited to **`.pdf`, `.docx`, `.txt`, and `.md`** files, up to **50 MB** each.
    *   **Content-hash deduplication:** re-uploading an identical file for the same user is rejected (HTTP 409) instead of storing a duplicate.
    *   Smart **tagging** system (public or private tags) to categorize documents by subject, chapter, or topic.
    *   Flexible **privacy controls** (Private for personal use / Public to share with the community).
    *   **Soft-delete with Trash & restore:** deleted documents move to a 30-day Trash from which the owner can restore them; a nightly purge job hard-deletes them after retention expires.
    *   **Tag-based recommendations** driven by the user's onboarding `preferred_tag_ids`.
*   **FEAT-STG: Cloud Storage & Previews**
    *   Integration with AWS S3 for secure, distributed static file storage, fully isolated from the application server via presigned URLs.
    *   Interactive built-in **preview** tool to read documents online without downloading.
*   **FEAT-FTS: Search & Discovery**
    *   **Keyword search** matching document **title + description** via case-insensitive SQL (`ILIKE`) — fast discovery across the user's own library and the public catalog.
    *   **Trending** documents feed and **tag-based recommendations**.
    *   *Note:* deep, semantic, full-content retrieval (scanning the actual text inside files) is delivered by the **RAG chatbot** (BM25 + pgvector), not by this search endpoint.
*   **FEAT-AI-RAG: Contextual AI Assistant (Retrieval-Augmented Generation)**
    *   Quick summarization of any selected document.
    *   Contextual chat based on a single file or a group of files (in a folder/subject) selected as the Context Window.
    *   Precise citations showing file names and page numbers for easy verification of AI responses.
    *   Chat session management (create, view, rename, and delete sessions) and per-user daily quota tracking.
*   **FEAT-AI-MAT: AI Study-Material Generation**
    *   Generate structured **quizzes** and **flashcards** (JSON) from a selected document via Gemini.
    *   Each generation request counts against the **shared daily AI quota** (the same counter as chat). If the source document is unsuitable, the API returns a graceful empty result with a reason rather than failing.
*   **FEAT-SOC: Community Sharing & Social Learning**
    *   Public document library for student-to-student sharing, with per-author public profile pages.
    *   **Bookmarks:** save / unsave documents to a personal saved list for later.
    *   Community interactions: 1-5 star ratings and review threads under public documents.
    *   **Reporting** tool for inappropriate content or copyright violations.
*   **FEAT-MOD: Content Moderation (Automated + Manual)**
    *   **Automated moderation** via the OpenAI Moderation API on a durable **Redis Streams** queue: the maximum category score across text chunks and embedded images drives triage — **≥0.80 auto-reject, <0.40 auto-approve, 0.40–0.80 manual PENDING**. Failed messages retry up to 5 times before a dead-letter queue.
    *   **Manual admin moderation:** approve / reject (with reason) for borderline or reported content.
*   **FEAT-MON: Subscriptions & Payments (VNPay)**
    *   Subscription Dashboard displaying real-time storage usage (used / limit) and the daily AI request quota.
    *   **VNPay** payment flow: `create-payment` returns a signed VNPay sandbox URL; a server-to-server **IPN** webhook finalizes the upgrade, and a browser **callback** redirects the user back to the frontend.
    *   Backend **daily AI quota** middleware (Redis `INCR`, 24h TTL) that blocks further AI requests with HTTP 429 once the plan limit is exceeded.
    *   Scheduled **plan-expiry notification** and **automatic downgrade** of expired Premium accounts back to Free.
*   **FEAT-NOT: In-App Notifications**
    *   Event-driven notifications covering document lifecycle (pending / approved / rejected / restored), new reviews, report submissions, document violation deletions, plan upgrades and expiry warnings, and account sanctions (warning / ban / activation).
    *   Mark-as-read support; notifications surface in the user's inbox.
*   **FEAT-ADM: Admin Dashboard**
    *   System statistics (dashboard), pending-moderation queue, report review and resolution, user management (ban / warn / reactivate), and tag management.

### 2.2. Scope of Initial Release (MVP)
The MVP delivers core individual study management, AI capabilities, automated moderation, and payments as a single, production-ready system:

*   **Authentication:** Email/password signup with OTP verification + Google OAuth2; JWT access (1h) / refresh (7d, rotating).
*   **Document Management:** Upload `.pdf`, `.docx`, `.txt`, and `.md` files up to **50 MB**, with content-hash dedup, manual tagging, soft-delete + Trash + restore, and Private/Public privacy settings.
*   **Storage & Display:** AWS S3 integration (presigned URLs) and smooth online document preview.
*   **Search & Discovery:** Keyword search by title + description, trending feed, and tag-based recommendations (from onboarding preferred tags).
*   **AI Chatbot (RAG):** chat over a single file or folder context, quick summaries, precise source citations (file name, page number), multi-turn chat session history, and a visible daily quota.
*   **AI Study-Material Generation:** quiz and flashcard generation from a document (counted against the shared daily AI quota).
*   **Community (core):** public library, bookmarks (save/unsave), ratings & reviews, and abuse reporting.
*   **Content Moderation:** automated OpenAI Moderation triage (auto-approve / auto-reject / manual) on a durable queue, plus manual admin moderation.
*   **Notifications:** in-app notifications for document lifecycle, reviews/reports, plan events, and account sanctions.
*   **Monetization:**
    *   Dashboard for storage and daily AI usage tracking.
    *   Plans: **Free Plan** (**2 GB** storage, **15** AI requests/day) and **Premium Plan** (**10 GB** storage, **500** AI requests/day). The daily AI quota is shared across chat, quiz, and flashcard generation.
    *   **VNPay** sandbox payments with automated IPN-driven activation.
    *   AI quota middleware (HTTP 429 on overflow, counter not incremented).
    *   Scheduled plan-expiry notification and automatic downgrade after the billing cycle. The `overlimitstorage` state **blocks upload only** — affected users can still list, read, and chat with their existing documents until they free up space or renew.

### 2.3. Scope of Subsequent Releases
Future phases will introduce advanced AI capabilities and community-focused monetization:

*   **Smart AI Enhancements:**
    *   **Auto-tagging:** AI analyzes document text to suggest appropriate tags on upload.
    *   **Multimodal RAG:** support AI extraction and analysis of charts and images embedded in documents.
    *   One-click automatic **mind-map** generation from study documents.
*   **Advanced Monetization:**
    *   **Group / Class Study Plans** for shared storage and collaboration.
    *   **Gamified Points System:** reward students with points when their public documents receive high downloads/ratings; points can be redeemed for extra AI requests or short-term Premium access.
*   **Deeper Community:** trending-algorithm tuning on the public Home page, enhanced author profiles, and richer discovery.

### 2.4. Limitations & Exclusions
*   **No Online Editing:** AI Study Hub is a repository and reading companion. It does **not** include online document editors like Google Docs or Word Online. Users must download files to edit them locally.
*   **Limited Accepted Formats:** Uploads accept only `.pdf`, `.docx`, `.txt`, and `.md`. Presentation slides, spreadsheets, audio, video, and compressed archives (`.zip`, `.rar`) are **not** supported by the AI RAG pipeline.
*   **Context & Cost Guidance (soft, not a hard cap):** To keep AI latency and token cost predictable, users are encouraged to scope their RAG sessions to a focused set of relevant documents rather than dumping an entire semester's library into one context. There is no hard page-count cutoff in the current API; session scoping is a usage guideline, not an enforced limit.
*   **AI Quota & Storage Gating:** All AI features (chat, quiz, flashcard) are gated by the daily plan quota; uploads are gated by the plan storage cap. Quota and storage are plan-dependent and reset / expand on upgrade.

---

## 3. BUSINESS CONTEXT

### 3.1. Stakeholder Profiles
Detailed breakdown of roles, attitudes, and expectations of the primary stakeholders:

| Stakeholder Group | Value Received | Attitude / Interest | Key Features of Interest | Constraints & Concerns |
| :--- | :--- | :--- | :--- | :--- |
| **Guest (Visitor)** | Get an overview of the platform and explore AI capabilities. | Curious, wants a quick trial without complicated setups. | Modern landing page, 1-click registration/login with Google. | Dislikes long signup processes or slow visual loading. |
| **User (Student/Learner)** | Save exam prep time, organize documents, summarize long texts, generate quizzes/flashcards, study efficiently. | Extremely high interest, expects high utility at an affordable price. | AI RAG Chatbot, Study-Material Generation, Keyword Search, Document Preview, Bookmarks, custom tags. | Concerned about cost of Premium, daily AI limits on the Free plan, and data privacy of Private files. |
| **Admin (System Admin)** | Easily manage users, moderate the public library, track automated billing. | Demands high security, clear dashboard controls, and fast moderation turnaround. | Moderation queue (auto-triaged + manual), report resolution, user management (warn/ban/reactivate), revenue stats, system logs. | Concerned about copyright violations on Public uploads; relies on automated moderation to keep manual workload manageable. |
| **AI / RAG Microservice** | Provide precise semantic retrieval and accurate, cited answers plus study-material generation. | Requires stable hosting, high-bandwidth connection, and an optimized vector store. | Hybrid retrieval (BM25 + pgvector), Jina reranking, Gemini LLM, OpenAI moderation, citation extraction. | Dependent on third-party LLM/embedding providers (Gemini, OpenAI) uptime and avoiding model hallucinations. |
| **Development & Ops Team** | Build a successful, modern product with commercial viability. | Eager, expects clean maintainable code and cost-optimized infrastructure. | Daily AI quota middleware, durable moderation queue, VNPay webhook pipeline, scheduled jobs, pgvector indexing. | Limited initial budget for API consumption and tight MVP schedules. |

### 3.2. Project Priorities
Project dimensions mapped using the Karl Wiegers Priority Matrix:

| Project Dimension | Driver | Constraint | Degree of Freedom | Detailed Description |
| :--- | :--- | :--- | :--- | :--- |
| **Features** | | **X** (For MVP) | **X** (For Future) | For the MVP, the core features (Document Management, AI RAG, Study-Material Generation, Moderation, Notifications, and VNPay Payments) are strict constraints and must be fully functional. Advanced AI (auto-tagging, multimodal RAG) and group/gamified monetization are degrees of freedom for future releases. |
| **Quality** | **X** | | | Quality is a major driver to build user trust. AI accuracy (no hallucinations), reliable citations, S3 preview speed, durable moderation, and strict private-file security are critical. |
| **Schedule** | | **X** | | The system must launch its MVP within 8-12 weeks to capture students' final exam seasons. |
| **Cost** | | | **X** | A 15% budget overflow on API costs is accepted during the initial launch phase to gather real user interaction data. |
| **Staff** | | **X** | | The team size is fixed (e.g., 2-3 Fullstack developers) and cannot be expanded during the MVP phase. |

### 3.3. Deployment Considerations
*   **Geographic Access:** Mostly accessed by university students within Vietnam. Traffic peaks are expected in evenings (19:00 - 24:00) and before final exams. Servers must use CDN configurations to optimize local response speeds.
*   **Infrastructure:** Requires AWS S3 for physical document storage; **PostgreSQL 16 + pgvector** (1536-dim embeddings, HNSW cosine index) for semantic vector retrieval; and **Redis 7** for caching, OTP/quota/blacklist keys, and the durable **Redis Streams** moderation queue.
*   **Asynchronous Processing:** Document RAG indexing runs on a virtual-thread `@Async` executor; the OpenAI moderation consumer group (`moderation-cg`) processes the moderation stream with manual ACK and a dead-letter queue after 5 failed attempts.
*   **Scheduled Jobs:** three daily business jobs — **plan-expiry notification** (08:00), **plan downgrade sweep** (08:00), and **document purge** of soft-deleted docs older than 30 days (03:00) — plus a 60-second moderation stream idle-message reclaim.
*   **Data Security:** Implement full SSL (HTTPS) encryption. Private documents must be fully secured using S3 presigned URLs to prevent unauthorized access. JWT access tokens are short-lived and blacklistable; the VNPay payment flow is signed with HMAC-SHA512; internal callbacks require an `X-Internal-Secret` header.
*   **Data Migration:** Provide simple Drag & Drop upload tools so students can easily migrate collections from Google Drive.
*   **User Training:** Include a brief interactive tutorial on first-time login to teach users how to choose study contexts, set preferred tags, and write high-quality prompts for the AI RAG chatbot.
