# BUSINESS REQUIREMENT DOCUMENT (BRD)

## PROJECT: AI-INTEGRATED STUDY DOCUMENT MANAGEMENT SYSTEM (AI STUDY HUB)

---

## 1. PROJECT OVERVIEW

### 1.1. Context

During their studies at university, students often face information overload and inefficient data management. Study materials (lecture slides, past exams, reference materials, coursework projects) are scattered across many different platforms including Google Drive, Messenger, Facebook Groups, personal Email, and physical storage devices (USB). This situation leads to lost data, wasted time searching, and reduced study productivity.

### 1.2. Problem Statements

- **Scattered data:** There is no central repository; documents are spread across many different communication and storage channels.
- **Low search efficiency:** The lack of a systematic classification tool and a smart search mechanism makes it difficult to retrieve old documents.
- **Information overload:** Students spend a lot of time reading and filtering core knowledge from long documents (PDF, Docx).
- **Manual sharing:** The process of sharing documents among students, study groups, or courses is fragmented and has no continuity.
- **Hardware limitations:** Storage capacity on personal devices is limited, requiring a centralized cloud storage solution.

### 1.3. System Goals

- Build a centralized Web platform that allows managing, storing, and classifying study documents in a systematic structure.
- Integrate Artificial Intelligence technology (AI Chatbot) based on the RAG (Retrieval-Augmented Generation) architecture to support direct interaction and Q&A on document content.
- Optimize the document-sharing process within the student community.
- Apply a real-world Fullstack software development process, ensuring the scalability and security of the system.

---

## 2. ACTORS & ROLES

The system includes 4 main actors:

| Actor              | Actor Type    | Role Description                                                                                                                                     |
| :----------------- | :------------ | :--------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Guest**          | User          | Unauthenticated user. Can only view the Landing Page, and register and log in to the system.                                                         |
| **User**           | User          | An authenticated student or learner. Has full control over personal documents, can share documents publicly, and interact with the AI Chatbot.       |
| **Admin**          | User          | System administrator. Has permission to manage users, moderate public documents, configure the system, and monitor operational logs.                 |
| **ChatbotService** | System        | System Actor. Responsible for natural language processing, document vectorization, and executing RAG queries to answer users.                        |

---

## 3. FUNCTIONAL REQUIREMENTS

### 3.1. Authentication & Profile

- **F-AUTH-01: Account registration:** Allows users to create a new account via Email. Supports registration verification through an activation link or an OTP code.
- **F-AUTH-02: Login & Logout:** Authenticates the user using a JWT (JSON Web Token) mechanism to maintain a secure session. Supports quick login via a third party (Google OAuth2).
- **F-AUTH-03: Password recovery:** Provides a secure password reset process via Email when the user uses the forgot password feature.
- **F-AUTH-04: Profile management:** Allows changing basic information including display name, avatar, and password.

### 3.2. Document Management

- **F-DOC-01: Document upload:** Accepts files in `.pdf`, `.docx`, `.txt`, and `.md` formats only, up to a **50 MB** per-file limit (a 60 MB multipart ceiling returns a clean 400 instead of a 500). Duplicate uploads are detected by a content hash (HTTP 409).
- **F-DOC-02: Document Tagging:** When uploading a file, the user selects available (public) tags or creates their own personal (private) tags to help classify and manage documents. When an Admin creates a new public tag, the system automatically maps any private tag with the same name into that public tag to standardize them.
- **F-DOC-03: Search:** Public documents are searchable by keyword over **title and description** (case-insensitive SQL match). Deep, content-aware question answering is handled by the RAG AI chatbot, not the search box.
- **F-DOC-04: Privacy configuration:** The user can set the document status:
    - _Private:_ Only the owner can view it and use it to chat with AI.
    - _Public:_ Shared to the system's shared document repository (requires an Admin moderation step).

### 3.3. Cloud Storage

- **F-STG-01: Distributed storage:** Integrates with standard cloud storage services (AWS S3) to store static files, fully separated from the application server.
- **F-STG-02: Document preview:** Renders and displays PDF, Word (`.docx`), Text, and Markdown content directly in the browser (via an S3 presigned URL) without requiring a download. Guests see only a 30% truncated preview for public/shared documents.

### 3.4. AI Chatbot (RAG Architecture)

- **F-AI-01: Contextual Chat:** The user can go to the My Documents page to use a specific group of documents as the background dataset (Context Window) for the Chatbot. The AI only processes and answers questions based on this specified data scope.
- **F-AI-02: Document-level query:** The user selects any document and asks questions, requesting the AI to summarize the content of that document. The AI only processes the data of the specified document.
- **F-AI-03: Citations:** The AI response must include accurate reference information (file name, page number, specific text segment) about the source of the data used to generate the answer, in order to minimize model hallucination (AI Hallucination).
- **F-AI-04: Session Management:** Stores chat history by conversation segments (Chat Session). The user can review, rename, or delete old chat sessions.

---

## 4. PROPOSED SYSTEM ENHANCEMENTS

To optimize the user experience and increase the practical value of the system, the following features are proposed for integration into the development roadmap:

- **Social Learning Framework:** Adds interaction mechanisms between members including star ratings (1-5), Comments, and Reports for documents in Public mode. Documents with high positive interaction will be prioritized for display in the Trending category.
---

## 5. Monetization & Payment

**F-MON-01: Subscription Dashboard:**

- The system provides an interface displaying the service plans (Free, Premium) along with detailed information about the benefits (storage limit, AI Request limit).

- Allows the user to monitor the real-time status of the current account: used/remaining storage, and the number of Requests used/remaining per day.

- The number of requests is reset every day, and the subscription billing cycle is 30 days. If after 30 days the user does not renew, the user will be downgraded from the Premium plan to Free.

- If a user's storage exceeds the Free plan limit (**2 GB**) after being downgraded from Premium to Free, the account enters the `overlimitstorage` state, which **blocks uploads only** — the user can still view and read their own documents. To restore full access, the user renews the Premium plan or deletes files until usage fits the Free limit.

**F-MON-02: Payment Gateway Integration:**

- Integrates **VNPay** as the sole payment gateway (HMAC-SHA512 signed via `VNPayUtil`) to process transactions.

- The system creates a pending invoice and returns a **VNPay payment URL** containing the exact amount and a unique transaction reference so the user can pay on the VNPay checkout page.

**F-MON-03: Subscription Automation via Webhook:**

- The system deploys Webhook endpoints to listen for transaction status responses (Callback) from the payment gateway.

- When a transaction is successful, the system automatically updates the user's plan (e.g., from FREE to Premium), and immediately expands the storage limit and resets/allocates a new AI Token quota.

**F-MON-04: AI Guard & Rate Limiting:**

- Builds a Middleware layer at the Backend to check access permission and the remaining quota (Request) of the account before forwarding the request to the ChatbotService.

- If the user exceeds the allowed quota of the current plan, the system blocks the request, does not send it to the LLM API to optimize cost, and returns a message guiding the user to upgrade their plan on the interface.

**F-MON-05: Transaction History:**

- Stores the entire plan upgrade history of the user including: Transaction ID, amount, payment method, time, and status (Success/Failed/Processing).
