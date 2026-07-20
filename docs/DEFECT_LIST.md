# Defect List — AI Study Hub API

> This defect list was collected during a source code review (Spring Boot 4.0.6 / Java 21). Each defect is linked to a specific module and component so the team can track it easily.
>
> **Severity**: Critical / Major / Minor / Trivial
> **Priority**: P1 (urgent) / P2 (high) / P3 (medium) / P4 (low)
> **Status**: Open / In Progress / Fixed / Re-opened / Deferred / Closed / Assigned

| Defect ID | Module | Summary | Severity | Priority | Status | Assignee |
|---|---|---|---|---|---|---|
| DEF-001 | Auth — `JwtAuthenticationFilter` | A logged-out refresh token still works for ~5-10s due to a race condition between the Redis blacklist write and the request being processed | Critical | P1 | Open | nguyenvana |
| DEF-002 | Auth — `AuthServiceImpl` | Google OAuth uses `RestClient` instead of the shared `WebClient` bean, so it does not follow the app's shared thread-pool / connection management | Minor | P3 | Deferred | letrongb |
| DEF-003 | Auth — `RedisTokenServiceImpl` | Refresh token rotation does not invalidate the old token immediately on concurrent requests, so the old refresh token can be reused twice | Major | P2 | In Progress | phamthic |
| DEF-004 | Auth — `JwtTokenProvider` | The 1h access token has no `tokenType` claim, which makes it easy to confuse with the refresh token during debugging/logging | Trivial | P4 | Open | nguyenvana |
| DEF-005 | Auth — `CustomUserDetailsService` | The lazy `OVERLIMITSTORAGE→ACTIVE` downgrade only checks plan expiry on login; a user who does not log in again keeps the wrong status until 08:00 the next day | Major | P2 | Assigned | tranvand |
| DEF-006 | Document — `DocumentServiceImpl.initiateUpload` | `max-file-size-bytes` is validated after the multipart file is written to a temp file, which wastes I/O when the file is too large | Minor | P3 | Open | lehoangf |
| DEF-007 | Document — `DocumentServiceImpl.processDocumentAsync` | When the RAG `/process` call times out, the document is stuck in `PROCESSING` forever because there is no reconciliation job | Critical | P1 | Open | lehoangf |
| DEF-008 | Document — `DocumentServiceImpl.deleteDocument` | Subtracting `storageUsed` is not in the same transaction as updating `UserEntity.status`, so storage can become inconsistent if a crash happens in between | Major | P2 | Re-opened | tranvand |
| DEF-009 | Document — `DocumentServiceImpl.restoreDocument` | Restoring a PUBLIC document when its S3 preview file was already purged makes `getSharedDocument` return 404 even though the status is COMPLETED | Major | P2 | Open | lehoangf |
| DEF-010 | Document — `DocumentMapper` | A PRIVATE tag is still rendered for a guest viewer because the fallback branch is missing the `isOwner` check | Major | P2 | Fixed | phamthic |
| DEF-011 | Document — `DocumentPreviewGenerator` | Previewing a DOCX larger than 20MB causes OOM because `Docx2txtLoader` loads the whole file into memory | Major | P2 | In Progress | lehoangf |
| DEF-012 | Document — `DocumentRagClient` | A 5xx error from RAG is propagated to the user as a raw message, which leaks the internal FastAPI endpoint | Minor | P3 | Open | nguyenvana |
| DEF-013 | Document — `DocumentController` | The public endpoint `GET /documents/user/*` is not paginated, so spam requests can fetch all public docs of a single user | Major | P2 | Open | phamthic |
| DEF-014 | Document — `DocumentServiceImpl.getRecommendedDocuments` | In-memory pagination over 100 candidate ids returns a wrong offset when `preferredTagIds` is empty | Minor | P3 | Assigned | tranvand |
| DEF-015 | Document — `DocumentImageExtractor` | Parsing a PDF with recursive XObjects throws `StackOverflowError`, and moderation falls into the DLQ instead of deferring to PENDING | Major | P2 | Open | lehoangf |
| DEF-016 | Chat — `ChatServiceImpl` | Building `history` takes the 10 newest messages without sorting, so the turn order is reversed when many sessions run concurrently | Major | P2 | Fixed | nguyenvana |
| DEF-017 | Chat — `ChatServiceImpl` | Parsing `material_payload` JSONB throws `JsonProcessingException` that is not caught, so chat history with a bad payload returns 500 | Major | P2 | In Progress | nguyenvana |
| DEF-018 | Chat — `ChatServiceImpl.loadAccessibleDocument` | A `DELETED` document is still reachable from an old session if its `documentId` still exists in `session_documents` | Major | P2 | Open | phamthic |
| DEF-019 | Chat — `ChatbotClientImpl` | `.block()` has no explicit `timeout()`, so it falls back to the default WebClient value and hangs the request when RAG is slow | Major | P2 | Open | nguyenvana |
| DEF-020 | Chat — `ChatController` | There is no per-user session limit, so spamming session creation bloats the `chat_sessions` table | Minor | P3 | Deferred | tranvand |
| DEF-021 | StudyMaterial — `StudyMaterialServiceImpl` | Quota is consumed even when RAG returns `refused`, but the error message is unclear, so the user thinks they still have quota left | Minor | P3 | Assigned | letrongb |
| DEF-022 | StudyMaterial — `StudyMaterialServiceImpl` | The `loadAccessibleDocument` logic is duplicated from `ChatServiceImpl`, so the two copies have diverged and are easy to break when only one is fixed | Minor | P3 | Open | letrongb |
| DEF-023 | StudyMaterial — `StudyMaterialClientImpl` | Mapping `correct_index` from snake_case to camelCase does not validate the 0-3 range, so the quiz renders wrong answers | Major | P2 | Open | letrongb |
| DEF-024 | StudyMaterial — `StudyMaterialController` | `count` greater than 20 (quiz) / 30 (flashcard) is not rejected early in the controller; it is pushed to RAG and clamped only there | Trivial | P4 | Open | phamthic |
| DEF-025 | Moderation — `AutoModerationServiceImpl` | When text batches larger than 30 chunks are skipped, nothing is logged, which makes it hard to debug which document was skipped | Minor | P3 | Open | tranvand |
| DEF-026 | Moderation — `AutoModerationServiceImpl` | When image moderation fails, the document is deferred to PENDING but the admin is not notified, so the doc stays PENDING forever | Major | P2 | In Progress | tranvand |
| DEF-027 | Moderation — `ModerationStreamListener` | A consumer crash between `process()` and ACK causes a redelivery, but `rejectDocument` already ran, so `REJECTED` overwrites `COMPLETED` | Critical | P1 | Open | tranvand |
| DEF-028 | Moderation — `ModerationStreamProducer` | The `app.moderation.consumer-name` default `${HOSTNAME}` is not set on local Windows, so consumers share a name and PEL reclaim works incorrectly | Minor | P3 | Assigned | nguyenvana |
| DEF-029 | Moderation — `ModerationDlqHandler` | Counting delivery-count with `XPENDING` is wrong when the message was never delivered, which causes an NPE in the dead-letter logic | Major | P2 | Open | tranvand |
| DEF-030 | Moderation — `ModerationStreamConfig` | The consumer group is created in-bean without checking whether Redis is ready at boot, so the app fails to start | Major | P2 | Re-opened | lehoangf |
| DEF-031 | Payment — `PaymentServiceImpl` | The VNPay IPN is not idempotent, so a duplicate invoice is created when VNPay retries the notification | Critical | P1 | Open | phamthic |
| DEF-032 | Payment — `PaymentController` | `vnpay-callback` redirects to `app.frontend-url` without encoding params, which creates an open-redirect risk if the config is overridden | Major | P2 | Open | phamthic |
| DEF-033 | Payment — `VNPayUtil` | HMAC-SHA512 verification is not constant-time, so a timing attack on the signature is possible | Major | P2 | Deferred | phamthic |
| DEF-034 | Payment — `PaymentController` | `history` returns a `List` with no pagination, so a user with many invoices gets a payload of several MB | Minor | P3 | Open | tranvand |
| DEF-035 | Notification — `NotificationController` | The notification list has no cursor and only does `findAll` + sort, so it is slow when a user has more than 1000 rows | Minor | P3 | In Progress | letrongb |
| DEF-036 | Notification — `DocumentServiceImpl` | A PENDING admin notification is sent to a former `ROLE_ADMIN` who was BANNED, which creates orphan notifications | Minor | P3 | Open | nguyenvana |
| DEF-037 | Notification — `PlanExpirationScheduler` | The idempotent check uses a Vietnamese `title`, which can collide when the wording changes, so users who should be notified are skipped by mistake | Minor | P3 | Assigned | letrongb |
| DEF-038 | User — `UserServiceImpl` | `updateProfile` does not validate a unique email atomically, so a race can create two users with the same email | Major | P2 | Open | tranvand |
| DEF-039 | User — `UserSanctionServiceImpl` | Banning a user does not clear the Redis `active_tokens` set, which leaks memory for long-inactive users | Minor | P3 | Open | nguyenvana |
| DEF-040 | Admin/AI-Metrics — `AiMetricsServiceImpl` | `Executors.newVirtualThreadPerTaskExecutor` is unbounded; it fans out 15 queries per call, so spamming the endpoint can spawn thousands of virtual threads | Major | P2 | Open | letrongb |
| DEF-041 | Admin/AI-Metrics — `LangfuseMetricsClient` | The fail-open path swallows 401/403 (wrong key) too, so the admin thinks it is "not configured" when the key is actually wrong | Minor | P3 | Assigned | letrongb |
| DEF-042 | Admin/AI-Metrics — `AiMetricsResponse` | `@Cacheable` deserialization needs `@NoArgsConstructor`; only the main DTO was fixed, the nested `MetricRow` / `TimeSeriesPoint` still lack it | Major | P2 | Open | letrongb |
| DEF-043 | Admin — `AdminStatsController` | `/ai-metrics` has no rate limit, so an admin spamming refresh thrashes the Langfuse quota and defeats the 5m cache | Minor | P3 | Open | phamthic |
| DEF-044 | Report/Review — `ReviewServiceImpl` | A new review notifies the uploader but does not skip the case where the user reviews their own document | Trivial | P4 | Open | tranvand |
| DEF-045 | Report/Review — `ReportServiceImpl.resolveReport` | When an admin deletes a document, the owner is not notified if `deleted_by_admin=true` and the user is INACTIVE | Minor | P3 | Open | tranvand |
| DEF-046 | Scheduling — `DocumentPurgeScheduler` | Hard-delete does not re-check `status_before_deletion`, so it can delete a document that an admin is restoring at the same time | Major | P2 | In Progress | lehoangf |
| DEF-047 | Scheduling — `PlanDowngradeScheduler` | The downgrade runs in one big transaction for all expired users, which locks the `users` table at 08:00 | Minor | P3 | Open | tranvand |
| DEF-048 | Tag — `TagServiceImpl.createPublicTag` | `@CacheEvict` clears both caches but not after commit, so the new tag is invisible if the cache is evicted before the transaction commits | Minor | P3 | Assigned | phamthic |
| DEF-049 | Storage/S3 — `S3UploadProvider` | The 10-minute GET presigned URL is too long for sensitive files and should be reduced to 2-3 minutes | Minor | P3 | Deferred | lehoangf |
| DEF-050 | Config — `SecurityConfig` | The path `/api/internal/**` (missing `v1`) is also `permitAll` in the allow-list, so a typo can leak the internal endpoint if routing is wrong | Major | P2 | Open | nguyenvana |

---

## Breakdown by module

| Module | Number of defects |
|---|---|
| Document | 10 |
| Moderation | 6 |
| Auth | 5 |
| Chat | 5 |
| StudyMaterial | 4 |
| Admin/AI-Metrics | 4 |
| Payment | 4 |
| Scheduling | 3 |
| Notification | 3 |
| Report/Review | 2 |
| User | 2 |
| Tag | 1 |
| Storage/S3 | 1 |
| Config/Security | 1 |
| **Total** | **50** |

## Breakdown by severity

| Severity | Count |
|---|---|
| Critical | 5 |
| Major | 22 |
| Minor | 19 |
| Trivial | 4 |

## Breakdown by status

| Status | Count |
|---|---|
| Open | 30 |
| In Progress | 7 |
| Assigned | 7 |
| Fixed | 2 |
| Re-opened | 2 |
| Deferred | 4 |
