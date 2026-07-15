# Repository Guidelines

Guide for AI assistants working in `ai-study-hub-api`. Everything below is grounded in the current source tree.

## Project Overview

**AI Study Hub API** — a Spring Boot 4.0.6 / Java 21 (LTS, virtual threads enabled) backend for a smart study-document platform: upload/store documents, chat with an AI study assistant over document content (RAG), manage storage plans + VNPay billing, auto-moderate public documents via the OpenAI Moderation API, let users bookmark/save documents, surface in-app notifications (document lifecycle, reviews/reports, plan expiry, account sanctions), and handle reviews/reports. It is a stateless JSON REST API (port `8080`) backed by PostgreSQL 16 + pgvector, Redis 7, AWS S3, and an **external FastAPI RAG microservice**.

## Architecture & Data Flow

Layered servlet (WebMVC) app, base package `vn.ai_study_hub_api` (`src/main/java/.../`).

```
HTTP request
  → JwtAuthenticationFilter   (validate Bearer → Redis blacklist check → load user by email → set SecurityContext)
  → SecurityFilterChain        (path-based authz: /api/v1/admin/** = ADMIN; public allow-list; else authenticated)
  → Controller                 (thin: pull userId from CustomUserDetails principal, delegate to service)
  → Service interface → *Impl   (@Transactional, manual builder DTO↔entity mapping, throws AppException)
  → Repository (JPA)  /  external (WebClient→FastAPI, AWS S3, Redis)  /  @Async taskExecutor
  → common/ApiResponse<T> envelope {success, message, data, timestamp}
  → GlobalExceptionHandler      (AppException→its status; validation→400 field map; auth→401; generic→500)
```

Key cross-process integrations are abstracted behind interfaces:
- **`ChatbotClient` → `ChatbotClientImpl`**: synchronous blocking `WebClient` POST to `${fastapi.rag-chat-url}` (`.timeout(chat-timeout-seconds).block()`). The `/chat` body includes the session's prior turns as `history` (`{role, content}`, oldest first, ≤10) for multi-turn RAG memory — built by `ChatServiceImpl` from `chat_messages` and passed via the 4-arg `ChatbotClient.chat(query, userId, documentId, history)`. `DocumentServiceImpl` POSTs to `${fastapi.rag-process-url}`. One shared `WebClient` bean (`config/WebClientConfig`).
- **`UploadProvider` → `S3UploadProvider`** (sole impl): AWS SDK v2, presigned URLs (10-min GET).
- **`AiQuotaService`**: Redis-backed daily per-user AI request counter (`user:ai_limit:{userId}:{date}`); throws HTTP 429 on overflow.
- **`RedisTokenService`**: refresh-token storage/rotation + access-token blacklist + OTP (`refresh_token:`, `blacklist_token:`, `otp:` keys).
- **`UserSanctionService`**: tracks live tokens in `active_tokens:{userId}` so `banUser` can mass-blacklist every session.
- **`AutoModerationService` → `AutoModerationServiceImpl`**: triages **PUBLIC** documents via the OpenAI Moderation API (shared `WebClient` bean, text in batches of <=30 chunks). Reads chunks read-only via `DocumentChunkRepository`, **and also classifies embedded images (PDF/DOCX) extracted from the original file in S3 via `DocumentImageExtractor` (`UploadProvider.download`) — base64-encoded and sent to the same `omni-moderation-latest` endpoint as `image_url` inputs; per-doc count capped (`app.moderation.image.max-per-doc`)**. Drives `DocumentService.approveDocument` / `rejectDocument(id, reason)` **internally** (same Spring context — no HTTP/JWT). Three-zone triage by max category score across **all text chunks + images**: `>= 0.80` -> auto-reject (generated reason), `< 0.40` -> auto-approve, `0.40-0.80` -> left `PENDING` for manual admin review. **Any failure in the image-moderation flow (S3 download / parse / OpenAI) is caught and defers the document to manual review (`PENDING`) instead of auto-approving on text alone — auto-approve additionally requires images were checked cleanly (`imagesChecked`); text-moderation failures still propagate for retry/DLQ.** **Execution is a Redis Streams consumer** (`stream:moderation`), not `@Async` — see "Redis Streams (moderation)" below. Skips (stays `PENDING`) when `openai.api-key` is empty/`mock_key` or chunks are missing/empty; transient failures propagate (message left unacked → retried, then DLQ'd after `app.moderation.max-attempts`).

Async is centralized on the `taskExecutor` pool (`config/AsyncConfig`, `app.async.*`: core 5 / max 20 / queue 100 / prefix `doc-async-`) and applied to `DocumentServiceImpl` (`@Async("taskExecutor")` on `processDocumentAsync`/`triggerFastApiAsync`/`updateFastApiVisibilityAsync`/`deleteFastApiVectorsAsync`). **Auto-moderation is NOT `@Async` anymore** — it runs on the `stream:moderation` consumer (see "Redis Streams (moderation)" below). `EmailService` (JavaMailSender) and `PaymentService` are **synchronous**, not `@Async`. Since Java 21 the executor runs on **virtual threads** (`spring.threads.virtual.enabled: true` + `setVirtualThreads(true)`) so blocking RAG/S3 I/O never pins OS threads (Tomcat request threads are virtual too); it also does graceful shutdown (`waitForTasksToCompleteOnShutdown`, 60s) and caller-runs back-pressure on queue overflow.

**Notifications & scheduling.** In-app notifications are `NotificationEntity` rows written **synchronously** (immediate `notificationRepository.save`, no queue) from many sites: `DocumentServiceImpl` (PENDING → admins; approve/reject → owner), `ReviewServiceImpl` (new review → uploader), `ReportServiceImpl` (report → admins; admin delete → uploader), `PaymentServiceImpl` (plan upgrade → user), `UserSanctionServiceImpl` (ban/warn/reactivate → user), and `PlanExpirationScheduler`. Each row carries a `type` (e.g. `DOCUMENT_PENDING`, `DOCUMENT_APPROVED`, `DOCUMENT_REJECTED`, `NEW_REVIEW`, `REPORT_SUBMITTED`, `DOCUMENT_VIOLATION_DELETED`, `PLAN_UPGRADED`, `PLAN_EXPIRING`, `ACCOUNT_BANNED`/`ACCOUNT_WARNING`/`ACCOUNT_ACTIVATED`) + `targetId`. Read/mark-read via `NotificationController` (`/api/v1/notifications`, authenticated). `PlanExpirationScheduler` is the sole `@Scheduled` job — daily 08:00 cron, notifies users whose plan expires within 3 days (idempotent per day via `existsByUserIdAndTitleAndCreatedAtAfter`); `@EnableScheduling` lives in `config/SchedulingConfig` (also present on `ModerationStreamConfig` for the PEL reclaim).

Data model: `User 1—N Documents`; `Document N—M Tags` (join `document_tags`); `User N—M Documents` via `saved_documents` (bookmarks); `User 1—N ChatSessions`, `ChatSession N—M Documents` (`session_documents`), `ChatSession 1—N ChatMessages`; `User N—1 StoragePlan`, `User 1—N Invoices`; `Review`/`Report` link `User`+`Document`; `User 1—N ViolationHistory`/`Notifications`. `UserEntity` also carries a scalar `preferred_tag_ids integer[]` (onboarding-survey tag ids) that powers document recommendations. `document_chunks` (with `embedding vector(1536)` + HNSW cosine index) is **managed by the FastAPI service**, not mapped by JPA.

## Key Directories

```
src/main/java/vn/ai_study_hub_api/
├── controller/         REST endpoints + request/ (input DTOs) + response/ (output DTOs)
├── service/            service interfaces; service/impl/ the @Service implementations
├── repository/         Spring Data JPA repos; projection/ interface projections
├── model/              JPA entities (*Entity) + enums (UserRole, UserStatus, DocumentStatus, ...)
├── security/           SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter, OAuth2, session tracking
├── config/             AsyncConfig, AwsS3Config, CacheConfig, JacksonConfig, ModerationStreamConfig, SchedulingConfig, WebClientConfig, WebConfig (CORS), OpenApiConfig, logging filter
├── common/             ApiResponse<T> envelope, VNPayUtil (HMAC-SHA512 signing)
├── exception/          AppException + GlobalExceptionHandler (centralized)
└── AiStudyHubApiApplication.java   @SpringBootApplication entry (normalizes JVM TZ → UTC)
src/main/resources/     application.yaml (+ -dev, -prod); static/ & templates/ empty
src/test/java/.../      JUnit 5 + Mockito unit tests mirroring main packages
docs/                   BRD, functional requirements, acceptance criteria (BDD Gherkin), user stories, business process, vision/scope, moderation-streams design notes, IMPROVEMENTS.md
initdb.sql              full DDL: 8 native PG enums, 14 app tables + `document_chunks` (RAG-managed), pgvector ext + vector(1536) HNSW index
```

## Development Commands

Java 21 + Maven (wrapper included). **There is no Node/Bun — this is a pure JVM project.**

```bash
# Run infra only (PostgreSQL + Redis) for local dev
docker compose up -d postgres redis

# Run the app locally (dev profile is the default-active Maven profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run the whole stack via Docker (builds JAR in-image, serves :8080)
docker compose up --build -d

# Tests (pure unit — no DB/Redis/S3 needed)
./mvnw clean test

# Build (also produces target/*.jar)
./mvnw clean package
```

- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`. Actuator is locked down: only `health`/`info` are exposed over HTTP, and `/actuator/**` (except `/actuator/health`) requires `ROLE_ADMIN` (see `SecurityConfig`).
- **Profile wiring**: Maven `dev` profile (default) / `prod` set `spring.profiles.active`, injected into `application.yaml` via the `@spring.profiles.active@` resource-filtering placeholder. Both profiles use `ddl-auto: update`.
- **CI** (`.github/workflows/workflow.yml`): a `build-test` job runs `mvn -B clean test` on JDK 21 for every push to `main` and every PR; the `deploy` job (`needs: build-test`, main-only) SSH-deploys to the VPS and runs `docker compose up --build -d`. The Dockerfile builds with tests enabled (no `-DskipTests`), so a failing test blocks both the image build and the deploy.

## Code Conventions & Common Patterns

**Layering & wiring.** Controller → `Service` interface → `*Impl` (`@Service`). Exceptions propagate as `throw new AppException(HttpStatus.X, "message")` — there is **no error-code enum**; the message string is the sole payload. Controllers are thin and never contain business logic.

**Response envelope.** ~every controller returns `ApiResponse<T>` (`common/ApiResponse`: static `success(data,msg)` / `success(msg)` / `error(msg)`). Errors all reuse `ApiResponse.error(...)`. Note `PaymentController` is a **partial anomaly** — its VNPay endpoints (`create-payment`, `vnpay-ipn`, `vnpay-callback`) return bare `ResponseEntity`/`RedirectView` (VNPay flow constraints), but `GET /payments/history` follows the `ApiResponse` convention; prefer `ApiResponse` for new endpoints.

**Authorization.** **No method-level security** (`@PreAuthorize`/`@Secured` are absent). Authz is purely path-based in `SecurityConfig`:
- `/api/v1/admin/**` → `hasRole(ADMIN)`
- `/actuator/**` → `hasRole(ADMIN)` (except `/actuator/health`, which is `permitAll` for health probes)
- `permitAll`: `/api/v1/auth/**`, public document search/preview/shared + `GET /api/v1/documents/user/*` (an author's public docs), GET reviews, **`/api/v1/internal/**` and `/api/internal/**`**, `/actuator/health`, `/login/oauth2/**`, swagger
- everything else → authenticated

`admin` vs `user` vs `internal` is separated **only by URL prefix**. The internal FastAPI callback (`/api/v1/internal/documents/callback`) is `permitAll` and instead guarded manually by comparing the `X-Internal-Secret` header to `${app.internal.secret}` (403 on mismatch).

**Current-user access.** Get the acting user from the principal: extract `CustomUserDetails` from the `SecurityContext` and read its `userId`/`role`. `CustomUserDetails.build(UserEntity)` maps `role → ROLE_<NAME>`; `ACTIVE` + `OVERLIMITSTORAGE` are treated as enabled, `INACTIVE`/`BANNED` as disabled. `OVERLIMITSTORAGE` blocks only **upload** (`initiateUpload` → 400); users can still list/read their own documents (`getPersonalDocuments`), and deleting files that bring `storageUsed` back under the plan limit restores `OVERLIMITSTORAGE → ACTIVE` (`deleteDocument`).

**Transactions & mapping.** Public service methods are `@Transactional` (use `readOnly=true` for queries). DTO↔entity mapping is **manual via Lombok builders — no MapStruct**.

**Entity conventions.** All entities: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` (note: `@Data` is **not** used on entities, only on DTOs). `@Table` names are plural snake_case.
- **ID strategies split**: `@GeneratedValue(strategy = GenerationType.UUID)` (DB-assigned) for `UserEntity`, `InvoiceEntity`, `ReviewEntity`, `ReportEntity`, `ViolationHistoryEntity`, `NotificationEntity`, `SavedDocumentEntity`; **app-assigned UUID** with `Persistable<UUID>` + a `@Transient isNew` flag reset in `@PostPersist/@PostLoad` for `DocumentEntity`, `ChatSessionEntity`, `ChatMessageEntity`; `GenerationType.IDENTITY` (Integer PK) for `StoragePlanEntity` + `TagEntity`.
- **Enums** use `@Enumerated(EnumType.STRING)` + Hibernate `@ColumnTransformer` to bridge Java UPPER-case names to lowercase native PG enum literals (`read="UPPER(status::text)"`, `write="cast(LOWER(?) as <pg_enum>)"`).
- **JSONB**: `ChatMessageEntity.citations` is `@JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")` typed as `String`.
- **PG array**: `UserEntity.preferredTagIds` is `@JdbcTypeCode(SqlTypes.ARRAY) @Column(columnDefinition = "integer[]")` typed as `List<Integer>` (onboarding-survey tag ids).
- **Audit cols**: `createdAt` stays `insertable=false, updatable=false` (DB `DEFAULT now()`); `updatedAt` is now Hibernate-managed via `@UpdateTimestamp` (`@Column(name="updated_at")`), so it auto-refreshes on every flush — previously insert-time only. Applied to all entities that carry `updated_at` (`User`, `Document`, `ChatSession`, `Invoice`, `Review`, `Report`, `StoragePlan`).
- Relationships are `@ManyToOne(LAZY)`; M:N via join tables; no JPA `cascade` (only SQL-level `ON DELETE CASCADE`).

**Repositories.** Extend `JpaRepository`. Custom queries mix JPQL (`@Query`) and native SQL. Projections: DTO constructor-expression (`new ...ReportedDocumentResponse(...)`) and Spring Data interface projection (`TrendingStatsProjection`). Pagination via `Page<>` + `PageRequest.of(page, size)` (defaults 0/10).

**Async.** Use `@Async("taskExecutor")` (the configured `ThreadPoolTaskExecutor`) for fire-and-forget background work — currently only document processing (RAG calls). Do not spawn raw threads. Auto-moderation uses a Redis Streams consumer instead (see below). There are now **three business `@Scheduled` jobs** (daily crons): `PlanExpirationScheduler` (08:00 — notifies users whose plan expires within 3 days), `PlanDowngradeScheduler` (08:00 — proactively downgrades expired premium users to the free plan via `UserService.downgradeToFreePlan`, the same helper the lazy downgrade in `CustomUserDetailsService.loadUserByUsername` uses on auth), and `DocumentPurgeScheduler` (03:00 — permanently deletes S3 files + DB row of documents soft-deleted >`app.document.retention-days` ago via `DocumentService.hardDeleteDocument`; storage + RAG vectors are already reconciled at soft-delete time). The moderation stream's `@Scheduled reclaimStale()` is an internal maintenance task; `@EnableScheduling` is in `config/SchedulingConfig` (also present on `ModerationStreamConfig`).

**Caching.** Spring's cache abstraction backs **read-heavy, slowly-changing reads** with Redis via `@Cacheable`/`@CacheEvict` (`config/CacheConfig` → `RedisCacheManager`, per-cache TTL under `app.cache.*`, JSON values via `GenericJackson2JsonRedisSerializer`). This is distinct from the ephemeral `StringRedisTemplate` usage (tokens/quota/blacklist). Two caches: `trendingDocuments` (key `page:size`, TTL 10m) and `publicTags` (TTL 30m). **`@Cacheable` is proxy-based → silently no-ops on self-invocation**, so the trending cacheable read lives in its own bean `TrendingDocumentCacheLoader` and its cached payload holds only PUBLIC tags (the user-agnostic view); `TrendingDocumentServiceImpl` then enriches the current owner's PRIVATE tags per request via one cheap `findOwnedDocumentsWithTags` query (no-op for guests/non-owners) — this split keeps the cache globally shareable (one entry per page) without leaking private tags across viewers. `PageImpl` is not JSON-friendly, so the cached value is the flat `TrendingPage` holder DTO. Eviction: `createPublicTag` clears both caches; `approveDocument`/`rejectDocument`/`deleteDocument` clear `trendingDocuments` (`allEntries=true`, since ranking shifts across all pages); new reviews rely on TTL self-heal (no eviction coupling). Cache annotations are **inert in pure-Mockito unit tests** (no proxy/context), so they're verified structurally, not via a Redis round-trip.

**Redis Streams (moderation).** Auto-moderation runs as a **durable job queue** on `stream:moderation` (replaces the old `@Async` fire-and-forget — a crash no longer silently drops the job and leaves the doc stuck `PENDING`). Same Redis instance as the cache/token usage; no new dependency (Spring Data Redis ships the Streams API). Components: `ModerationStreamProducer` (`XADD {document_id}`, called from both trigger sites), `ModerationStreamListener` (`StreamMessageListenerContainer` consumer-group `moderation-cg`, `autoAcknowledge=false` → runs `AutoModerationService.process()`, ACKs on success/idempotent-skip, leaves unacked on failure), `ModerationDlqHandler` (reads delivery-count via `XPENDING`; after `app.moderation.max-attempts` re-posts to `stream:moderation:dlq` + ACKs the original), `ModerationStreamConfig` (`@EnableScheduling`; creates the group idempotently **in-bean** before the `SmartLifecycle` container starts polling; the listener's `@Scheduled reclaimStale()` re-claims PEL messages idle > `app.moderation.reclaim.min-idle-ms` and reprocesses them — this is what actually retries failed/crashed jobs, since `XREADGROUP … >` only returns never-delivered messages). At-least-once, but idempotent: `process()` is a no-op when status ≠ `PENDING`, so redelivery is safe. `app.moderation.consumer-name` MUST differ per instance (`${HOSTNAME}`). Design docs: `docs/moderation-streams-before.md` / `-after.md`.

**External HTTP.** Use the shared `WebClient` bean for the FastAPI RAG service. (Google OAuth token exchange in `AuthServiceImpl` uses `RestClient` instead — a pre-existing inconsistency.)

**DTOs.** Input DTOs in `controller/request/`, output DTOs in `controller/response/`, both Lombok `@Data + @Builder`. Some controllers also declare static-inner request DTOs. Validate inputs with `@Valid`; validation failures become a 400 field→message map via `GlobalExceptionHandler`. Paginated endpoints return an empty `*PageResponse` subclass (`DocumentPageResponse`/`TrendingDocumentPageResponse`/`UserPageResponse`) extending `ApiResponse<Page<...>>` — these exist purely so Springdoc generates a correct schema for the generic `Page<T>` payload (the body is set manually via the inherited setters).

## Important Files

| File | Purpose |
|------|---------|
| `src/main/java/vn/ai_study_hub_api/AiStudyHubApiApplication.java` | Entry point; normalizes JVM TZ to `UTC` (both `main` and `@PostConstruct`) |
| `config/JacksonConfig.java` | Global Jackson `JavaTimeModule`: serializes `LocalDateTime` as ISO-UTC `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`. Pairs with `spring.jackson.time-zone: UTC` + `spring.jpa.properties.hibernate.jdbc.time_zone: UTC`. |
| `security/SecurityConfig.java` | Filter chain: stateless, CSRF off, path-based authz, OAuth2 wiring |
| `security/JwtTokenProvider.java` | jjwt HMAC tokens: access 1h (`app.jwt.access-expiration-ms`=3600000), refresh 7d |
| `security/JwtAuthenticationFilter.java` | Bearer validation + Redis blacklist check + user load |
| `service/impl/AuthServiceImpl.java` | Login (issue + store refresh), rotate-on-refresh, logout blacklist |
| `service/impl/DocumentServiceImpl.java` | Core document business orchestrator (upload validation, upload→extract/index state machine, owner/admin lifecycle mutations, read queries). Preview rendering, RAG HTTP, and entity→response mapping are delegated to the 3 collaborators below (SRP split of the former ~1177-line god class). Also owns the save/unsave bookmark flow (`saveDocument`/`unsaveDocument`/`getSavedDocuments`/`getPublicDocumentsByUser` over `SavedDocumentEntity`), trash bin retrieval (`getTrashDocuments`), tag-based recommendations (`getRecommendedDocuments` — caps candidate ids at 100 via `findRecommendedDocumentIds`, then paginates in memory over `preferredTagIds`), increments `download_count` on successful download url requests, and fans out `NotificationEntity` writes to admins (PENDING) and the owner (approve/reject). |
| `service/impl/DocumentPreviewGenerator.java` | Truncated preview generation (PDF/DOCX/TXT ~30%) + preview-path helper. No DB deps. |
| `service/impl/DocumentRagClient.java` | Thin blocking `WebClient` client for every FastAPI RAG call (`/process`, `/extract`, `/index`, `/visibility`, `/documents/{id}`). Propagates errors; the @Async orchestrator owns status transitions. |
| `service/impl/DocumentMapper.java` | `DocumentEntity`→`DocumentResponse` projection + per-viewer tag visibility. Replaces 5 duplicated inline mapping blocks. |
| `service/impl/TrendingDocumentCacheLoader.java` | `@Cacheable` Redis-backed builder of the trending page (user-agnostic, PUBLIC-tags-only) — split out of `TrendingDocumentServiceImpl` so the cache proxy engages (avoids self-invocation). Returns the flat `TrendingPage` holder. |
| `config/CacheConfig.java` | `@EnableCaching` + `RedisCacheManager` (per-cache TTL, JSON serializer). Cache-name constants: `trendingDocuments`, `publicTags`. |
| `service/impl/AutoModerationServiceImpl.java` | OpenAI Moderation API triage of public docs over **text chunks + embedded images** (auto-approve / auto-reject / leave PENDING). Synchronous `process(UUID)` invoked by the stream consumer; reads chunks read-only + extracts images via `DocumentImageExtractor`, drives `DocumentService.approve/reject` internally. Text-moderation failures propagate (no swallow); image-flow failures defer to PENDING. |
| `service/impl/DocumentImageExtractor.java` | Extracts embedded raster images from a document's S3 original (PDF via PDFBox XObjects, DOCX via POI `getAllPictures`), dedupes (SHA-256) and caps the count; rethrows whole-file parse errors so moderation defers to manual review. |
| `service/UploadProvider.java` / `service/impl/S3UploadProvider.java` | S3 abstraction (upload / presigned URL / public URL / delete / storage path / **`download(storagePath)` → raw bytes, used for image extraction**). |
| `service/impl/ModerationStreamProducer.java` | `XADD stream:moderation {document_id}` — durable replacement for the old `moderateDocumentAsync` fire-and-forget. Called from both trigger sites (callback `EXTRACTED` + `updateDocument` PRIVATE→PUBLIC). |
| `service/impl/ModerationStreamListener.java` | Consumer-group `moderation-cg` reader: runs `process()`, ACKs on success, leaves unacked on failure (→ retry), moves to DLQ at `max-attempts`. Owns the `@Scheduled` PEL reclaim. |
| `service/impl/ModerationDlqHandler.java` | Delivery-count lookup (`XPENDING`) → dead-letter decision; re-posts failed messages to `stream:moderation:dlq`. |
| `config/ModerationStreamConfig.java` | `@EnableScheduling` + `StreamMessageListenerContainer` (consumer-group reader, manual ACK); creates the group idempotently in-bean before the container starts. |
| `service/impl/ChatServiceImpl.java` | Chat sessions, AI-quota enforcement, multi-turn `history` construction (last 10 `chat_messages` → RAG), citation extraction from RAG JSON |
| `service/impl/RedisTokenServiceImpl.java` | Redis keys: `refresh_token:`, `blacklist_token:`, `otp:` |
| `service/impl/S3UploadProvider.java` | Sole `UploadProvider` impl (AWS SDK v2, presigned URLs) |
| `service/impl/PlanExpirationScheduler.java` | Daily 08:00 `@Scheduled`: notifies users whose storage plan expires within 3 days (`PLAN_EXPIRING`); idempotent per day via `existsByUserIdAndTitleAndCreatedAtAfter`. |
| `service/impl/PlanDowngradeScheduler.java` | Daily 08:00 `@Scheduled`: proactively downgrades expired premium users to the free plan via `UserService.downgradeToFreePlan` (shared with the lazy downgrade in `CustomUserDetailsService`). |
| `service/impl/DocumentPurgeScheduler.java` | Daily 03:00 `@Scheduled`: permanently deletes S3 files + DB row of documents soft-deleted >`app.document.retention-days` ago via `DocumentService.hardDeleteDocument` (per-document transactions; storage + RAG vectors already reconciled at soft-delete time). |
| `config/SchedulingConfig.java` | `@EnableScheduling` for the plan-expiry cron (and the moderation PEL reclaim). |
| `controller/NotificationController.java` | `/api/v1/notifications` (authenticated): list a user's notifications + mark-as-read; maps `NotificationEntity`→`NotificationResponse`. |
| `controller/PaymentController.java` | `/api/v1/payments`: `create-payment` (VNPay URL), `vnpay-ipn` (server-to-server IPN), `vnpay-callback` (browser redirect → `app.frontend-url`), and `history` (`ApiResponse<List<TransactionHistoryResponse>>` from `InvoiceRepository.findAllByUserIdOrderByCreatedAtDesc`). |
| `model/SavedDocumentEntity.java` | Bookmark join `User`↔`Document` (`saved_documents`, unique `user_id`+`document_id`); `saved_at` DB-defaulted. |
| `repository/SavedDocumentRepository.java` | `existsByUserIdAndDocumentId` / `findByUserId(Pageable)` / `deleteByUserIdAndDocumentId`. |
| `repository/DocumentChunkRepository.java` | Read-only `document_chunks` query (`ChunkContentProjection`) — moderation input. Never writes (RAG owns the table). |
| `common/ApiResponse.java` | Universal response envelope |
| `exception/GlobalExceptionHandler.java` | Centralized error mapping → `ApiResponse.error` |
| `exception/AppException.java` | The one custom exception (`HttpStatus` + message) |
| `src/main/resources/application.yaml` | Base config + `${ENV}` placeholders. **No secret defaults** — `JWT_SECRET`, `INTERNAL_API_SECRET`, `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` fail fast if unset. Actuator exposes only `health`/`info`. |
| `initdb.sql` | Full DDL (8 enums, 14 app tables + RAG-owned `document_chunks`, pgvector `vector(1536)` HNSW cosine index) |
| `pom.xml` | Maven build, `dev`/`prod` profiles, dependency versions |

**Externalized config groups** (`application.yaml`): `aws.s3.*`, `openai.*` (`api-key`, `moderation-url`), `fastapi.*` (`base-url`, `rag-process-url`, `rag-chat-url`, `chat-timeout-seconds:30`), `app.jwt.*`, `app.internal.secret`, `app.async.*`, `app.cache.*` (`trending-documents`/`public-tags`/`default` TTL minutes), `app.moderation.*` (stream-key/group/consumer-name/poll/batch/max-attempts/dlq/reclaim), `app.upload.max-file-size-bytes` (50MB), `app.document.retention-days` (default 30; soft-delete retention before permanent purge), `app.share-url-prefix`, `app.frontend-url` (VNPay callback redirect target, default `http://localhost:5173`), `vnpay.*`, `spring.security.oauth2.client` (Google), `spring.mail.*`, plus `spring.jackson.time-zone: UTC` + `spring.jpa.properties.hibernate.jdbc.time_zone: UTC` (UTC-everywhere; see `JacksonConfig`). Secrets (`JWT_SECRET`, `INTERNAL_API_SECRET`, `AWS_*`, `OPENAI_API_KEY`, `VNPAY_*`) come **only** from env (no hardcoded fallback). `spring.threads.virtual.enabled: true` enables Java 21 virtual threads.

## Sibling Service: RAG Pipeline (FastAPI)

The RAG/AI engine is a **separate Python service** in a sibling repo: `~/code/ai-study-hub-rag-service` (FastAPI + uvicorn on port `8000`, container `ai-study-hub-rag-service`, joined to the same external `ai-study-hub-network`). This Java API calls it over HTTP; it is **not** part of this repo's build. The two services share one PostgreSQL database (`aistudyhub`) and one `INTERNAL_API_SECRET`.

**Stack**: Python 3.11, FastAPI, **LangChain 0.3.x (pinned — code targets the 0.3 API; do not bump to 1.x)**, Google Gemini (`gemini-2.5-flash-lite` LLM + `gemini-embedding-001` @ **1536 dims**), Jina reranker (`jina-reranker-v3`, top-5), a **custom `PostgresVectorStore` over pgvector** (ChromaDB is intentionally unused), psycopg2 with a `ThreadedConnectionPool`.

### Wire contract between the two services

All RAG ingest endpoints live under `fastapi.base-url` (default `http://localhost:8000/api/v1/rag`); chat lives at `fastapi.rag-chat-url` (`/api/v1/chat`). Calls from this API use the shared `WebClient` bean (10s timeout, blocking inside `@Async("taskExecutor")` workers).

| Direction | Endpoint | Body | Purpose |
|---|---|---|---|
| API → RAG | `POST {base}/process` | `{document_id, file_url}` | PRIVATE docs: extract + index + summary in one background job (no moderation). |
| API → RAG | `POST {base}/extract` | `{document_id, file_url}` | PUBLIC docs: extract only — chunks stored with `embedding=NULL`. Returns 202. |
| API → RAG | `POST {base}/index` | `{document_id}` | After approval: embed pending chunks + rebuild BM25. Idempotent. |
| API → RAG | `PATCH {base}/documents/{id}/visibility` | `{visibility}` | Stamp visibility into chunk metadata (metadata only; this API gates retrieval). |
| API → RAG | `DELETE {base}/documents/{id}` | — | Delete all chunks + parent docs + rebuild BM25 (reject / delete flow). |
| API → RAG | `POST /api/v1/chat` | `{query, user_id, document_id, history}` | Chat. `history` = the session's prior turns (`{role, content}`, oldest first, ≤10) for multi-turn memory. Response envelope mirrors `ApiResponse` (`data.llm_response` + `data.debug.documents` for citations). RAG **deterministically** routes SMALLTALK / SUMMARY / QA (no LLM); smalltalk returns a canned reply with no `documents` (→ 0 citations), and the QA branch short-circuits when retrieval is empty. |
| RAG → API | `POST /api/v1/internal/documents/callback` | `{document_id, status, summary}` + header `X-Internal-Secret` | Guarded by `InternalDocumentController` (`app.internal.secret`); mismatch → 403. `status` ∈ `SUCCESS` (→ `COMPLETED` if `PROCESSING`), `EXTRACTED` (stores summary, status stays `PENDING`, then appends a job to `stream:moderation` via `ModerationStreamProducer`), `FAILED` (→ `FAILED`). Retried 3× with backoff. |

> **Moderation reads `document_chunks` directly** (shared DB) via a read-only Java repository — `DocumentChunkRepository` returns a `ChunkContentProjection`. There is intentionally **no `/chunks` endpoint on RAG**: the moderation service lives in this backend, so it queries the shared DB instead of an HTTP hop.

### Document lifecycle & moderation flow (two-phase extract/index)

Uses the existing `DocumentStatus` values — **no new statuses were added**. Moderation is implemented **in this backend** as `AutoModerationService` (`AutoModerationServiceImpl`): it reads chunks read-only from `document_chunks` via `DocumentChunkRepository`, classifies them with the OpenAI Moderation API, and drives `DocumentService.approveDocument` / `rejectDocument(id, reason)` internally (no HTTP/JWT — same Spring context). This API also wires the RAG seams (extract so chunks exist, index on approve, purge on reject).

```mermaid
flowchart TD
    UP_PRIV([Upload PRIVATE]) --> PROC["PROCESSING -> POST /process (extract+index)"]
    PROC -->|callback SUCCESS| DONE1([COMPLETED])
    UP_PUB([Upload PUBLIC]) --> EXT["PENDING -> POST /extract (chunks, embedding deferred)"]
    EXT -->|callback EXTRACTED| PEND(["PENDING — chunks ready"])
    PEND -->|"auto-moderation (OpenAI) on document_chunks -> triage"| DEC{max score}
    DEC -->|"approve (<0.40)"| APR["PROCESSING -> PATCH /visibility=public + POST /index"]
    APR -->|callback SUCCESS| DONE2([COMPLETED])
    DEC -->|reject (>=0.80)| REJ["REJECTED -> DELETE /documents/{id}"]
    DEC -->|"yellow 0.40-0.80"| PENDMAN(["PENDING — manual admin review"])
    UPD([Update PRIVATE->PUBLIC]) --> PEND2(["PENDING — chunks already embedded (no /extract)"])
    PEND2 -->|"auto-moderation reads existing chunks -> triage"| DEC
```

- **PRIVATE** (`processDocumentAsync`): `/process` → extract + index immediately → callback `SUCCESS` → `COMPLETED`. No moderation.
- **PUBLIC upload** (`processDocumentAsync`): → `PENDING`, notify admins, call `/extract` (chunks created, `embedding=NULL`). On the RAG `EXTRACTED` callback, `handleFastApiCallback` appends to `stream:moderation` (`ModerationStreamProducer.enqueue`) — a durable job the consumer processes async. That reads chunks read-only via `DocumentChunkRepository`, **also extracts embedded images from the S3 original via `DocumentImageExtractor` (PDF/DOCX, base64-encoded as `image_url` inputs)**, calls the OpenAI Moderation API (text in batches of <=30 chunks, images in smaller batches), and triages by the **max category score across all chunks and images**: `>= 0.80` -> auto-reject (`rejectDocument(id, reason)` with a generated Vietnamese reason), `< 0.40` -> auto-approve (`approveDocument(id)`), `0.40-0.80` -> left `PENDING` for manual admin review. It stays `PENDING` (skipped) when `openai.api-key` is empty/`mock_key` or chunks are missing/empty; a transient OpenAI failure leaves the message unacked (retried, then DLQ'd after `app.moderation.max-attempts`). **An image-moderation-flow failure (download / parse / OpenAI) is caught and defers the document to `PENDING` (manual review) rather than auto-approving on text alone — auto-approve requires `imagesChecked`.**
- **Approve** (`approveDocument`): `PENDING → PROCESSING` → `PATCH /visibility=public` + `POST /index` (embeds pending chunks; no-op if already embedded, e.g. the update-visibility case) → callback `SUCCESS` → `COMPLETED`.
- **Reject** (`rejectDocument`): → `REJECTED` + `DELETE /documents/{id}` (purge extracted/indexed chunks).
- **Update PRIVATE→PUBLIC** (`updateDocument`): chunks already exist (indexed as private) → set to `PENDING` + notify admins, then **appends to `stream:moderation`** via `ModerationStreamProducer` (`triggerModeration`) — chunks are already embedded, so moderation reads them immediately without an `/extract` round-trip and without an `EXTRACTED` callback. RAG visibility is flipped to public **only at approve** (`approveDocument`), not at update time. This path does **not** call RAG `/extract` (no new extraction runs); moderation is enqueued in-process instead of via a callback. (So moderation has **two** entry points that both enqueue the same `stream:moderation` job: the `EXTRACTED` callback for fresh public uploads, and `updateDocument` for PRIVATE→PUBLIC updates.)

### RAG ingestion pipeline

Two-phase, in `app/services/ingestion.py`: `_extract` and `_index`. `process_document_task` (private) calls both; `extract_document_task` calls only `_extract`; `index_document_task` calls only `_index`.

- **`_extract`**: download presigned file → load by extension (`PyPDFLoader` / `TextLoader` for `.txt`+`.md` / `Docx2txtLoader` for `.docx`; else `FAILED`) → enrich metadata (page/chunk citations + `document_id`) → **Parent-Child chunking** (parent 1000/200, child 200/50) via `ParentDocumentRetriever._split_docs_for_adding` → store parent docs in `LocalFileStore` (`parent_docs_store/`) → insert child chunks into `document_chunks` with **`embedding=NULL`** (`PostgresVectorStore.add_texts_without_embedding`). Children carry `metadata.doc_id` = parent uuid so parent-fetch retrieval still works after embedding.
- **`_index`**: `embed_pending_chunks(document_id)` — embed all `embedding IS NULL` chunks for the doc in one `embed_documents` call (1536-dim Gemini) + per-row `UPDATE` → `update_bm25()`. Idempotent.
- Summary is generated at extract time and carried in both the `EXTRACTED` and `SUCCESS` callbacks.

### Retrieval (QA branch)

Hybrid search: **BM25** (over parent docs, filtered to the requested `document_id`s) **+ dense** pgvector cosine (HNSW, `k=25`), combined via `EnsembleRetriever`, then **Jina cross-encoder re-rank** → top context → Gemini generation. `similarity_search_by_vector` filters `embedding IS NOT NULL`, so extracted-but-not-indexed (public, pre-approval) chunks are never surfaced. The generator emits **numeric citation markers `[N]`** mapping 1:1 to the `documents` list in `debug`, and consumes the sent `history` to resolve follow-up references (still cites `[N]` only from retrieved context). Routing is **deterministic (regex, no LLM)** — SMALLTALK → SUMMARY (needs a selected doc) → QA (default); the QA branch short-circuits with a fixed message when retrieval is empty. **Multi-query OFF by default** (`ENABLE_MULTI_QUERY=1`; costs ~6s/extra LLM call).

### Shared state & gotchas

- **Shared DB**: RAG reads `documents` (`summary`, `title`, `uploader_id`) and **owns** `document_chunks` (writes embeddings + metadata). This API reads it **read-only** via `DocumentChunkRepository` (`@Immutable` `DocumentChunkEntity` + `ChunkContentProjection`) for moderation — never writes. Keep both `initdb.sql` in sync — `document_chunks.embedding vector(1536)` + the HNSW cosine index.
- **Leak prevention is two-layered**: (1) RAG `similarity_search` filters `embedding IS NOT NULL`; (2) this API only passes `COMPLETED` document_ids to RAG chat, so `PENDING`/`REJECTED` public docs are never queried even though their (NULL-embedding) chunks exist in the store.
- **Embedding dim fixed at 1536** (Gemini `output_dimensionality` forced in `CustomGoogleEmbeddings`); must match the `vector(1536)` column.
- **`INTERNAL_API_SECRET` must be identical** in both services (`app.internal.secret` ↔ RAG `INTERNAL_API_SECRET`), else every callback → 403.
- **`fastapi.base-url`** (`${FASTAPI_BASE_URL:http://localhost:8000/api/v1/rag}`) is the base for all RAG ingest endpoints (`/process`, `/extract`, `/index`, `/documents/{id}/...`); `fastapi.rag-process-url` (private `/process`) and `fastapi.rag-chat-url` are retained for those two specific calls.
- **RAG clients are process-wide singletons** (LLM, embeddings, reranker) warmed at startup to avoid a ~14s Gemini cold-start.
- **RAG config** (`app/core/config.py` + `.env`): `DATABASE_URL`, `BACKEND_CALLBACK_URL`, `INTERNAL_API_SECRET`, `JINA_API_KEY`, Google API key, `ENABLE_MULTI_QUERY`, `DB_POOL_MAX` (20), `TEMP_DIR`.
- **RAG endpoints for reference**: `POST /api/v1/rag/process`, `POST /api/v1/rag/extract`, `POST /api/v1/rag/index`, `PATCH /api/v1/rag/documents/{id}/visibility`, `DELETE /api/v1/rag/documents/{id}`, `POST /api/v1/chat`. (The old `/api/v1/chat/retrieve` debug endpoint was removed.)

## Runtime / Tooling Preferences

- **Runtime**: Java 21 (JDK 21 LTS) — virtual threads power Tomcat request threads + the `@Async` `taskExecutor`. No JavaScript runtime is involved.
- **Build tool**: Maven via the `mvnw` wrapper (note: `mvnw` is gitignored; use a locally installed `mvn` if the wrapper is absent — `mvnw.cmd` exists for Windows).
- **Package manager**: Maven (there is no npm/yarn/pnpm/bun).
- **`dev` is the default-active profile** — running plain `./mvnw spring-boot:run` uses `dev`.
- **Containers**: Docker multi-stage build (`Dockerfile`): `maven:3.8.8-eclipse-temurin-17` → `eclipse-temurin:17-jre-alpine`. `docker-compose.yaml` runs `postgres` (pgvector/pgvector:0.8.2-pg16-trixie, mounts `initdb.sql`), `redis:7-alpine`, and the backend, on an **external** `ai-study-hub-network`. `docker-compose.local.yaml` is a dev-only postgres+redis stack (no backend, gitignored).
- **Upload size**: Spring multipart ceiling is **60MB**, intentionally above the **50MB** business cap (`app.upload.max-file-size-bytes`) so the service-layer check returns a clean 400 rather than a generic 500. Keep multipart ≥ business cap when changing limits.
- **Secrets**: `.env` is gitignored and holds `DATABASE_*`, `REDIS_*`, `JWT_SECRET`, `GOOGLE_*`, `AWS_*`, `MAIL_*`, `INTERNAL_API_SECRET`, `FASTAPI_*`, `OPENAI_*` (the `OPENAI_API_KEY` consumed by auto-moderation). Never commit it; never print secret values.

## Testing & QA

- **Framework**: JUnit 5 (Jupiter) + Mockito only. Managed by the Spring Boot 4.0.6 parent; test deps are `spring-boot-starter-data-jpa-test`, `-security-test`, `-webmvc-test`.
- **Style**: pure unit tests, `@ExtendWith(MockitoExtension.class)` (or manual `MockitoAnnotations.openMocks(this)`), `@Mock`/`@InjectMocks`/`when`/`verify`/`ArgumentCaptor`. **No Spring slices** (`@SpringBootTest`/`@WebMvcTest`/`@DataJpaTest`), **no MockMvc**, **no `@WithMockUser`**, **no test DB/H2/Testcontainers**, and **no `src/test/resources`**. All external deps (Redis, S3, FastAPI `WebClient`, JPA repos, even `EntityManager`) are mocked. `@Value` fields are set with `ReflectionTestUtils.setField`.
- **Assertions**: JUnit5 `Assertions.*` only — AssertJ is on the classpath but unused.
- **Naming**: classes `*Test`; packages mirror `main`. Method naming is inconsistent across files (`method_scenario_expectation` in services, `_Success` in controllers, `testDeserialize*` in the DTO test) — match the file you're editing.
- **Run**: `./mvnw clean test`. No extra infra required.
- **Coverage**: 176 unit tests across 25 classes (`AiQuota`, `Chat`, `User`, `Report`, `Review`, `Document` + extracted `DocumentPreviewGenerator`/`DocumentRagClient`/`DocumentMapper`, `Trending` (+`TrendingDocumentCacheLoader`), `Tag`, `AdminStats`, `Auth`, `S3UploadProvider`, `AutoModeration` (+`ModerationStreamProducer`/`ModerationStreamListener`/`ModerationDlqHandler`), `Payment`, `PlanExpirationScheduler`, `PlanDowngradeScheduler`, `DocumentPurgeScheduler`, controller + DTO tests). **Untested service impls**: `RedisTokenServiceImpl`, `ChatbotClientImpl`, `UserSanctionServiceImpl`, `GoogleOAuth2UserServiceImpl`. The security filter chain remains untested. `@Cacheable`/`@CacheEvict` are proxy-based and thus **inert in the pure-Mockito unit tests** (no Spring context) — verified structurally, not via Redis round-trip; likewise the `stream:moderation` `StreamMessageListenerContainer` + group creation are not unit-tested (need a real Redis + Spring context). **CI now gates deploys on `mvn test`** (`build-test` job). Prefer the existing Mockito-unit style when adding tests.
