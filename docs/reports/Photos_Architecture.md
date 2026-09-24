# Baroness Moments / Photo Cloud Architecture

**Status: READY FOR IMPLEMENTATION REVIEW**  
**Scope:** Shared photo import, temporary cloud retention, offline copies, Gallery export, and future album import  
**Out of scope:** FRIDAY, redesigning `MainActivity`, replacing navigation, complete Compose UI, camera/video capture, and Kotlin implementation

## 1. Final architecture decision

Baroness Photos becomes **Moments**: a private shared collection between the two Baroness users. Supabase/Postgres is authoritative for identity mapping, relationship membership, Moment metadata, retention timestamps, cleanup state, and audit. A private object-storage bucket contains originals and thumbnails. Android is local-first for reads, WorkManager owns durable transfers, Photo Picker is the V1 import surface, and MediaStore is the only permanent user-preservation path.

Use **Cloudflare R2 Standard** as the first object-storage provider behind `ObjectStorageGateway`. Use a **Supabase Edge Function boundary** for V1 signing, upload intent, object authorization, commit, and deletion. This fits the existing Baroness environment better than adding a Cloudflare Worker: the repository already uses Supabase Edge Functions, Postgres, RLS, and Realtime, so identity and authorization remain in one trusted system. R2 credentials and the service role never enter the APK.

Cloud storage is temporary. A Moment is deleted from cloud storage at `expires_at` unless the product later adds an explicitly approved preservation feature. V1 has **no `PROTECTED` retention state**. Exporting to the Android Gallery is the permanent user-controlled preservation mechanism. App-private copies are useful offline copies, not an archive and not a reason to retain cloud data.

The existing Photos route remains intact. The current `PhotoRepository` is a mock Unsplash source and `gallery_items` is a legacy URL table; neither becomes the Moments data model.

## 2. Current-codebase findings and integration boundaries

| Existing path | Finding and reuse |
|---|---|
| `app/src/main/java/com/baroness/app/MainActivity.kt` | Already exposes the `Photos` route and `PhotosScreen`; preserve it. Do not redesign activity/navigation. |
| `screens/PhotosScreen.kt` | Existing grid/viewer shell and `PhotoViewerOverlay`; later bind it to Moment summaries and local/signed image sources. |
| `viewmodels/PhotosViewModel.kt` | Existing `StateFlow` convention; replace demo loading with a Moments repository-backed state model. |
| `repository/PhotoRepository.kt` and `models/PhotoItem.kt` | Hard-coded Unsplash demo only; retire as the production source and add separate Moment models. |
| `data/local/database/AppDatabase.kt` | Room singleton/versioned migration pattern. Add Moments local entities with a non-destructive migration; remove `fallbackToDestructiveMigration()` before shipping user-owned Moment state. |
| `SyncQueueDao`, `SyncQueueItem`, `SyncManager`, `SyncWorker` | Existing offline synchronization pattern and WorkManager conventions. Do not put binary transfer state into the generic JSON wishlist queue; add dedicated Moment transfer entities/workers. |
| `config/SupabaseConfig.kt` | Existing PostgREST, Realtime, and Storage client. Reuse it for metadata/realtime; R2 access goes through Edge Function-issued URLs. |
| `modules/AuthManager.kt`, `StorageManager.kt`, `SessionManager.kt`, `UserSessionManager.kt` | Existing custom gate/persona/session surfaces. They must be bridged to a real Supabase Auth identity before Moments RLS is implemented. DataStore remains suitable only for small reminder preferences. |
| `services/FCMService.kt`, `utils/NotificationManager.kt` | Reuse for one consolidated expiry warning, not a notification per photo. |
| `app/build.gradle.kts` | Supabase, Room, WorkManager, Coil, OkHttp/Ktor, coroutines, DataStore, Compose, and navigation already exist. No core new dependency is currently justified. |
| `AndroidManifest.xml` | Existing broad media permissions must be audited. Moments V1 uses Photo Picker and MediaStore scoped storage, not unrestricted gallery scanning. |
| `supabase/schema.sql`, `supabase/migrations/`, `supabase/policies.sql` | Add a new Moments migration/policy set. Do not modify FRIDAY migrations. |

Current conflicts to resolve during implementation:

- `PhotoRepository` and the screen append public URL transformation parameters. Private Moments must load local files or short-lived signed URLs.
- `gallery_items` has URL metadata and permissive policies. Treat it as legacy; migrate real data only after an audit, then retire its public policies.
- Existing policy output includes broad public access and persona literals. Moments must use `auth.uid()` through the identity bridge, never client-supplied persona IDs.
- `AuthManager` uses a custom access-key flow and is not proof that a Supabase Auth session exists. This is a prerequisite, not an assumption.

## 3. Component architecture

```text
Compose Moments UI
        |
        v
MomentsViewModel
        |
        v
Small feature use cases
  import selected media
  observe moments
  download for offline
  export to Gallery
  snooze warning
        |
        v
MomentsRepository
        |
  +----------------------+-----------------------+
  |                      |                       |
  v                      v                       v
Room local state   MomentFileStore       Supabase remote adapter
metadata/transfers cache/files           Postgres/RPC/Realtime
  |                      |                       |
  +----------------------+-----------------------+
                         |
                         v
              ObjectStorageGateway
                 R2 via signed URLs
```

The repository is the feature boundary. UI code does not call Supabase, R2, `ContentResolver`, or WorkManager directly. This is a feature-local architecture, not a new application-wide framework.

### V1 component map

- `MomentsRepository`
- `MomentsLocalDataSource` and Room DAOs
- `MomentsRemoteDataSource` for Postgres/RPC/Realtime
- `ObjectStorageGateway` and one R2 adapter
- `MomentFileStore` for cache, staging, and deterministic app-private files
- `GalleryExporter` using MediaStore
- `MediaImportSource` using Photo Picker
- `MomentUploadWorker` and `MomentDownloadWorker`
- `MomentsViewModel`
- `MomentsRealtimeCoordinator`
- Supabase Edge Functions for upload intent, signed downloads, commit, and scheduled cleanup

Do not add collections, a DI framework, a new Gradle module, a generic cloud manager, or ongoing album-sync workers to V1.

## 4. Storage provider decision

### Provider comparison

| Provider | Practical fit |
|---|---|
| **Cloudflare R2** | Recommended. S3-compatible private objects, signed URL support through a trusted signer, lifecycle rules, and no Internet egress charge. Current published Standard pricing lists $0.015/GB-month, Class A/B request pricing, and monthly free allowances. Good for temporary images and repeated downloads. |
| **Supabase Storage** | Viable fallback and operationally simple because it is already installed. Private buckets, Storage RLS, signed URLs, CDN, and resumable upload support are useful. Published plan storage/egress quotas are coupled to Supabase billing, making it less attractive as a growing archive. |
| **Backblaze B2 + CDN** | Viable lower-cost alternative, but adds another signing/CDN operational boundary. |
| **Amazon S3** | Durable and mature, but adds IAM/CloudFront complexity and more involved egress economics for this two-user product. |

Pricing and quotas change; verify them at provisioning time. References: [R2 pricing](https://developers.cloudflare.com/r2/pricing/), [Supabase pricing](https://supabase.com/pricing), [Supabase Storage](https://supabase.com/docs/guides/storage), [AWS S3 pricing](https://aws.amazon.com/s3/pricing/), and [Backblaze B2 pricing](https://www.backblaze.com/cloud-storage/pricing).

### R2 V1 configuration

- One private bucket; no public bucket or `r2.dev` access.
- Originals and thumbnails are immutable objects.
- Server-side encryption enabled.
- Client receives only short-lived, key-scoped signed upload/download URLs.
- R2 lifecycle is used only to abort abandoned multipart uploads; business expiry is decided by Postgres and the cleanup Edge Function.
- No R2 credentials, service-role key, or bucket-list permission in Android.
- `ObjectStorageGateway` keeps a future Supabase Storage adapter possible without changing domain code.

## 5. Authentication and authorization prerequisite

Before any Moments migration or RLS policy is implemented, confirm and document this exact mapping:

```text
Android authenticated session
        ↓
Supabase auth.users.id / auth.uid()
        ↓
Baroness persona mapping
        ↓
Moments relationship membership
        ↓
Moment authorization
        ↓
Edge Function-issued signed object access
```

The current repository contains a custom access-key/persona flow and profile IDs such as `phesty_official` and `baroness_official`; the inspected code does not establish that those IDs are Supabase Auth subjects. V1 must therefore include an identity bridge:

1. Establish the actual Supabase Auth sign-in/session refresh path used by Android.
2. Add a trusted mapping from `auth.users.id` to the existing Baroness persona/profile identity.
3. Resolve the active `relationship_id` from authenticated membership, not from a request parameter.
4. Make RLS predicates and Edge Functions use `auth.uid()`.
5. Reject requests that supply a different owner/persona/relationship than the authenticated membership.

If the existing gate cannot produce a valid Supabase Auth JWT, Moments authorization is blocked until that bridge exists. Do not implement Moments RLS on the assumption that the custom gate is equivalent to Supabase Auth.

## 6. Final V1 database schema

V1 deliberately contains only tables needed for authorization, uploads, deduplication, retention, cleanup reliability, configurable policy, and local device state. Device file paths are not stored in Postgres. Collections, server-side device state, and generalized album sources are later features.

The deduplication decision is **scoped to a Baroness relationship**, not global. A private shared relationship should not cause one relationship to reference another relationship's object key or reveal that the same binary exists elsewhere. This makes the database constraint and key layout consistent.

### 6.1 Relationship and membership

If the existing database has a compatible relationship table, reuse it rather than creating a duplicate. The following is the required logical contract; the proposed names apply only if no compatible table exists:

```sql
create table moments_relationships (
    id uuid primary key default gen_random_uuid(),
    created_at timestamptz not null default now(),
    status text not null default 'active'
        check (status in ('active', 'suspended', 'closed'))
);

create table moments_relationship_members (
    relationship_id uuid not null references moments_relationships(id) on delete cascade,
    subject_id uuid not null references auth.users(id) on delete cascade,
    joined_at timestamptz not null default now(),
    left_at timestamptz,
    primary key (relationship_id, subject_id)
);
```

The relationship bootstrap/membership write is administrative or server-controlled. Do not allow an arbitrary client to add itself to a relationship.

### 6.2 Object metadata, Moment metadata, and upload idempotency

```sql
create table moment_objects (
    id uuid primary key default gen_random_uuid(),
    relationship_id uuid not null references moments_relationships(id) on delete cascade,
    content_hash_sha256 bytea not null,
    byte_size bigint not null check (byte_size > 0),
    mime_type text not null check (mime_type in (
        'image/jpeg', 'image/png', 'image/webp', 'image/heic', 'image/avif'
    )),
    width integer check (width is null or width > 0),
    height integer check (height is null or height > 0),
    original_storage_key text not null unique,
    thumbnail_storage_key text not null unique,
    storage_provider text not null,
    created_at timestamptz not null default now(),
    unique (relationship_id, content_hash_sha256, byte_size, mime_type)
);

create table moments (
    id uuid primary key default gen_random_uuid(),
    relationship_id uuid not null references moments_relationships(id) on delete cascade,
    object_id uuid not null references moment_objects(id),
    owner_id uuid not null references auth.users(id),
    filename text not null,
    caption text,
    taken_at timestamptz,
    created_at timestamptz not null default now(),
    uploaded_at timestamptz not null default now(),
    expires_at timestamptz not null,
    warning_at timestamptz not null,
    retention_status text not null default 'active'
        check (retention_status in ('active', 'warning', 'expired', 'deleting', 'deleted')),
    visibility text not null default 'relationship'
        check (visibility = 'relationship'),
    deleted_at timestamptz,
    delete_reason text
);

create table moment_uploads (
    id uuid primary key default gen_random_uuid(),
    moment_id uuid references moments(id) on delete set null,
    object_id uuid references moment_objects(id) on delete set null,
    relationship_id uuid not null references moments_relationships(id) on delete cascade,
    requested_by uuid not null references auth.users(id),
    source_name text not null,
    content_hash_sha256 bytea,
    byte_size bigint,
    state text not null default 'local_selected'
        check (state in (
            'local_selected', 'staged', 'queued', 'uploading',
            'uploaded', 'committing', 'committed', 'failed', 'cancelled'
        )),
    provider_upload_id text,
    attempt_count integer not null default 0,
    last_error_code text,
    last_error_message text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table moment_settings (
    relationship_id uuid primary key references moments_relationships(id) on delete cascade,
    retention_days integer not null default 60 check (retention_days > 0),
    warning_days integer not null default 7 check (warning_days >= 0),
    updated_by uuid not null references auth.users(id),
    updated_at timestamptz not null default now(),
    check (warning_days < retention_days)
);
```

`expires_at` and `warning_at` are calculated at commit from the relationship settings and stored on each Moment. Later settings changes apply to new uploads; V1 does not silently rewrite existing expiry dates.

### 6.3 Retention audit and cleanup retry state

These two tables are V1 because cleanup must be idempotent, observable, and recoverable when object storage and Postgres operations complete at different times:

```sql
create table moment_retention_events (
    id bigint generated always as identity primary key,
    moment_id uuid not null references moments(id) on delete cascade,
    from_status text,
    to_status text not null,
    actor_type text not null check (actor_type in ('scheduler', 'system')),
    reason text,
    created_at timestamptz not null default now()
);

create table moment_cleanup_attempts (
    id bigint generated always as identity primary key,
    moment_id uuid not null references moments(id) on delete cascade,
    object_id uuid not null references moment_objects(id),
    storage_key text not null,
    attempt_no integer not null,
    result text not null check (result in ('started', 'deleted', 'not_found', 'failed')),
    error_code text,
    created_at timestamptz not null default now()
);
```

### 6.4 V1 indexes, RLS, and constraints

```sql
create index moments_relationship_created_idx
    on moments (relationship_id, created_at desc)
    where deleted_at is null;

create index moments_retention_idx
    on moments (retention_status, warning_at, expires_at)
    where deleted_at is null;

create index moment_uploads_work_idx
    on moment_uploads (state, updated_at);

create index moment_cleanup_attempts_idx
    on moment_cleanup_attempts (moment_id, created_at desc);

create unique index moment_objects_relationship_hash_idx
    on moment_objects (relationship_id, content_hash_sha256, byte_size, mime_type);
```

Enable RLS on every V1 table. A security-definer `is_relationship_member(target_relationship uuid)` helper checks `auth.uid()` against active membership. Members may read Moments and settings for their relationship. Clients may not directly set `owner_id`, `relationship_id`, object keys, expiry, retention status, cleanup state, or deletion timestamps. Upload intent, object deduplication, commit, retention transition, and cleanup completion are server-side RPC/Edge Function operations.

The `moment_objects` unique constraint is relationship-scoped. Every deliberate user import creates one Moment row, even when its bytes reuse an existing relationship object; a retry of the same `upload_id` does not create a second Moment. This deduplicates storage without silently collapsing two deliberate imports into one user-visible event.

An object may be referenced by more than one Moment row. Cleanup therefore expires the Moment first, then deletes its object variants only when no non-deleted Moment in the same relationship still references that `object_id`. If another Moment still references it, only the expiring Moment is tombstoned; the object remains available to the remaining Moment. The final reference deletion is the operation that removes the object bytes.

### 6.5 Local Room state (not Supabase tables)

The app stores device-specific state locally because it is not shared business data:

```text
moment_local_state
  moment_id              TEXT PRIMARY KEY
  local_state             TEXT NOT NULL
      -- absent, downloading, available, stale, corrupt, evicted
  last_verified_at        INTEGER NULL
  exported_at             INTEGER NULL
  updated_at              INTEGER NOT NULL

moment_transfer
  upload_id               TEXT PRIMARY KEY
  moment_id               TEXT NULL
  staging_file_name       TEXT NULL
  state                   TEXT NOT NULL
  attempt_count           INTEGER NOT NULL
  last_error              TEXT NULL
  updated_at              INTEGER NOT NULL
```

The actual Room entities should follow the existing Kotlin naming/type conventions and use a foreign-key relation to the local Moment metadata entity where practical. `MomentFileStore` derives paths from IDs; these tables never store absolute paths or Gallery URIs as authoritative cloud state. Reminder snooze/dismissal is local preference state, not a shared Moment property.

## 7. Object identity and storage keys

Canonical identity is SHA-256 of the exact original bytes plus byte size and MIME type, scoped to `relationship_id`. Filename, EXIF, taken time, and perceptual similarity are not identity. Recompressed/resized files are different binaries.

Use relationship-scoped, UUID-based immutable keys:

```text
relationships/{relationship-id}/objects/{object-id}/original
relationships/{relationship-id}/objects/{object-id}/thumbnail-v1
```

This key layout agrees with the relationship-scoped uniqueness constraint. The object ID is generated by the server and is not the hash or filename. A global deduplication service is intentionally not introduced in V1.

## 8. Exact local-storage lifecycle

```text
Cloud Moment: ACTIVE -> WARNING -> EXPIRED -> DELETING -> DELETED
Local:       cache -> app-private offline copy -> optional Gallery export
```

### Cache

Thumbnails, signed URL downloads, and temporary `.part` files live under `cache/moments`. The cache can be cleared at any time. It is never authoritative and is regenerated from the cloud while the Moment is available.

### App-private Moment copy

Full-resolution offline copies live under deterministic paths derived from `moment_id`, for example:

```text
files/moments/{moment-id}/original
files/moments/{moment-id}/thumbnail
files/moments/staging/{upload-id}/source
```

`MomentFileStore` owns these paths. Room does **not** store absolute paths. Room stores local state only:

```text
ABSENT, DOWNLOADING, AVAILABLE, STALE, CORRUPT, EVICTED
last_verified_at
exported_at
```

On download, stream to a `.part` file, verify size and SHA-256, then atomically rename to the deterministic path. On export, `exported_at` is recorded locally for UX only; it does not change cloud retention.

An app-private copy is not a permanent preservation mechanism. It may be removed by an app storage-reclamation policy, Clear Storage, uninstall, or a cloud-expiry reconciliation. It must not be used to justify extending `expires_at`.

### Gallery export

Use `MediaStore.Images` with `RELATIVE_PATH = Pictures/Baroness Gallery/`, MIME type, collision-safe display name, and `IS_PENDING` on Android 10+. Write the original bytes when possible, then clear `IS_PENDING`. Gallery files are user-visible external media and normally survive Clear Cache, Clear Storage, and uninstall. Cloud cleanup never deletes them.

### Required lifecycle behavior

1. **Moment downloaded offline:** cache/full-resolution app-private copy is available; cloud retention timestamps remain unchanged.
2. **Later exported to Gallery:** Gallery becomes the permanent user copy; app-private copy may remain temporarily for fast viewing.
3. **Cloud Moment expires:** backend deletes cloud objects. The Gallery copy remains untouched.
4. **App still has an app-private copy:** after authoritative expiry/deletion is observed, mark it stale and delete it during reconciliation or the next storage-maintenance pass. It must not remain a hidden archive or be shown as an active Moment.
5. **App reclaims storage:** evict app-private copies by age/size policy, retaining only metadata needed to reconcile; never evict/delete Gallery files.
6. **User clears app data/uninstalls:** Room, cache, staging, and app-private copies are lost; Gallery exports remain user media subject to Android/user action.
7. **User never exports before expiry:** cloud Moment is deleted and the local app-private copy is eventually removed or made unavailable. No permanent preservation is implied.

If the client is offline at expiry, it cannot execute server deletion. It must stop presenting the Moment as guaranteed beyond its known `expires_at`, then reconcile with the server on reconnect. The server remains authoritative and deletes it even if the client was offline.

## 9. Upload and download pipelines

### Upload state machine

```text
LOCAL_SELECTED -> STAGED -> QUEUED -> UPLOADING -> UPLOADED
                                      -> COMMITTING -> COMMITTED

Any active state -> FAILED (retryable/permanent)
QUEUED or UPLOADING -> CANCELLED
```

1. Photo Picker returns selected URIs.
2. Validate MIME, readable size, dimensions, and decodability.
3. Copy to app-private staging so WorkManager does not depend on temporary URI permission.
4. Stream SHA-256 and metadata extraction.
5. Insert a local transfer row; enqueue unique WorkManager work with network constraints.
6. Edge Function validates the Supabase session and relationship membership, then creates an upload intent and server-generated key.
7. Upload via short-lived signed URL/multipart session. Verify byte count/checksum.
8. Call idempotent commit with `upload_id`; server reuses the relationship-scoped object on matching hash and creates the Moment row once.
9. Realtime/local reconciliation makes the committed Moment visible.
10. Delete staging only after commit; retain it temporarily for retryable failures.

App termination is safe because state and staged bytes are durable. Authentication expiry gets one refresh/retry. Uploaded-but-uncommitted objects are found by an orphan scanner and either committed through the idempotency record or deleted after a safety period.

### Download state machine

```text
ABSENT -> THUMBNAIL_LOADING -> THUMBNAIL_AVAILABLE
       -> FULL_DOWNLOAD_QUEUED -> DOWNLOADING -> VERIFYING -> AVAILABLE

DOWNLOADING -> RETRY_WAIT -> DOWNLOADING
VERIFYING -> CORRUPT -> FULL_DOWNLOAD_QUEUED
```

Room metadata is read first. Coil uses a deterministic local thumbnail/full-resolution file when available; otherwise the repository requests a short-lived signed URL. Full-resolution downloads stream to `.part`, verify SHA-256 and size, then atomically rename. Signed URL expiry, network errors, cancellation, corruption, and disk exhaustion are retryable/user-visible states.

## 10. Retention, warning, cleanup, and failure behavior

### Retention state machine

```text
ACTIVE -> WARNING -> EXPIRED -> DELETING -> DELETED
```

- `ACTIVE`: available cloud Moment.
- `WARNING`: `now >= warning_at`; repeated but rate-limited reminders are allowed during the warning period.
- `EXPIRED`: `now >= expires_at`; no V1 protection state exists.
- `DELETING`: cleanup claimed the Moment and is deleting both object variants.
- `DELETED`: object deletion succeeded or returned not-found; metadata becomes an audit tombstone/hidden record according to query policy.

The warning period is configurable through `warning_days`; retention duration is configurable through `retention_days`. The client can offer Gallery export, select/bulk export, snooze, and dismiss. It must not offer “protect in cloud” in V1.

### Server-authoritative cleanup

Use a scheduled Supabase Edge Function with service credentials stored in Supabase secrets:

1. Claim eligible `active/warning` rows whose `expires_at <= now()` using a conditional transaction/lock and set `deleting`.
2. Write a retention event and mark the Moment `deleting`.
3. In the same protected transaction, check whether another non-deleted Moment references the same `object_id` and prevent a new commit from referencing an object being finalized for deletion.
4. If references remain, mark only this Moment `deleted`; retain the shared object.
5. If no references remain, delete original and thumbnail through `ObjectStorageGateway`.
6. Treat not-found as success and record each attempt.
7. Mark the Moment `deleted` only after the reference decision and any required object deletion complete.
8. Retry `deleting` failures with bounded backoff; leave the row retryable and observable.

If object deletion succeeds but the database update fails, the next run sees not-found and completes the row. If the database moves to `deleting` but object deletion fails, the row remains `deleting` and is retried; the client does not report successful deletion until the object outcome is known. An orphan scanner removes uploaded objects with no committed row after a safety period. Provider lifecycle rules only clean abandoned multipart uploads and are not the business retention engine.

### Warning delivery

On app open and through a modest scheduled refresh, query local metadata for Moments in the warning period. Group counts and issue one consolidated notification/banner per reminder interval. Store snooze/dismissal in local DataStore or a local Room preference; do not create a server table for per-device reminders in V1. If the device is offline, warning display is best effort; the server expiry still occurs.

## 11. Android media access and album scope

### V1: individual import only

Use `ActivityResultContracts.PickMultipleVisualMedia`. It provides least-privilege user-selected access and avoids scanning the entire gallery. Copy selected content promptly into staging. V1 does not implement background album monitoring.

Gallery export uses scoped `MediaStore`; no `MANAGE_EXTERNAL_STORAGE` and no broad permission solely for export. Existing `READ_MEDIA_IMAGES`, video/audio, and legacy storage permissions must be audited against other features before removal; they are not required by the Moments V1 import path.

### V2: album features

- **Import once:** a user-selected snapshot can be added later without changing V1 retention/storage semantics.
- **Ongoing sync:** postponed. If eventually implemented, it requires explicit permission, periodic WorkManager checks, MediaStore checkpointing, revocation handling, and best-effort album identification. It must never become silent whole-gallery scanning, and Photo Picker selection must not be advertised as unrestricted future album monitoring.

## 12. Security boundary

```text
Android Supabase JWT
        |
        v
Supabase Edge Function
  - validates JWT/auth.uid()
  - resolves persona and relationship membership
  - validates Moment/object ownership
  - creates signed upload/download URL
  - performs idempotent commit or deletion
        |
        v
Private R2 object
```

Phesty can access an authorized Moment only when the Edge Function resolves Phesty's authenticated `auth.uid()` through the persona mapping and active relationship membership. The function then signs the server-owned object key for a short duration. An unauthorized user cannot enumerate keys, read metadata under RLS, choose a relationship ID, or mint a URL for another relationship.

RLS still protects Postgres reads/writes. Realtime is a change signal, not authorization. Signed URLs are short-lived and scoped. Server-side MIME, size, checksum, and image-dimension validation is mandatory. EXIF/GPS handling is a product privacy decision that must be settled before upload implementation.

## 13. V1 versus V2

### Required for V1

- Supabase Auth identity bridge and persona mapping
- Relationship membership/access contract
- `moments_relationships` and `moments_relationship_members` if no compatible existing model exists
- `moment_objects` with relationship-scoped hash uniqueness
- `moments`
- `moment_uploads`
- `moment_settings`
- `moment_retention_events`
- `moment_cleanup_attempts`
- Room-local Moment metadata, transfer state, local state, and reminder state
- Private R2 bucket and Supabase Edge Function signing/commit/cleanup boundary
- Photo Picker individual import
- WorkManager upload/download workers
- deterministic app-private files and MediaStore Gallery export
- Realtime merge plus periodic reconciliation
- server cleanup, orphan handling, retry, and audit
- strict RLS and no client-supplied authorization identity

### Deliberately postponed to V2

- `moment_collections`/albums as first-class cloud entities
- server-side `moment_device_state` (device paths/state remain local)
- ongoing device-album synchronization
- perceptual-hash similarity detection
- permanent cloud protection/`PROTECTED` retention
- multi-provider runtime switching
- advanced CDN image transformations
- video, camera capture, and shared editing
- generalized notification/reminder tables

## 14. Gradle and manifest requirements

No core dependency addition is currently required. Existing Supabase PostgREST/Realtime/Storage, Room/KSP, WorkManager, Coil, OkHttp/Ktor, coroutines, DataStore, Compose, and navigation cover V1. Do not add an R2 SDK to Android, a second database, a second image loader, a DI framework, or a new Gradle module.

Only add an EXIF library or resumable-transfer library if implementation validation proves Android platform APIs and the existing OkHttp/Ktor stack insufficient.

Manifest work is implementation-phase cleanup: prefer Photo Picker, remove broad Moments-only media requirements, retain legacy permissions only where an audited existing feature needs them, and do not add `MANAGE_EXTERNAL_STORAGE`.

## 15. Failure and recovery matrix

| Failure | Required behavior |
|---|---|
| Offline before upload | Keep staged bytes and local transfer state; WorkManager retries when connected. |
| App killed | Resume from Room and staging; never depend on in-memory URI lists. |
| Auth token expired | Refresh once; then expose sign-in-required failure. |
| Multipart interrupted | Resume valid provider upload or safely abort/restart. |
| Duplicate retry | `upload_id` plus relationship-scoped SHA-256 commit is idempotent. |
| Object uploaded, commit failed | Retry commit; orphan scanner deletes abandoned objects after a safety period. |
| Signed URL expired | Request a new URL and retry once. |
| Corrupt download | Remove `.part`, redownload, verify; then surface a persistent error. |
| Disk full | Stop transfer and request space; never delete Gallery media. |
| Cleanup delete fails | Keep `deleting`, audit the failure, and retry. |
| Object delete succeeds, DB update fails | Retry sees not-found and completes the tombstone transition. |
| DB says deleting, object delete fails | Keep retryable `deleting`; do not claim deletion succeeded. |
| Realtime event missed | Reconcile metadata on app entry and periodic connected work. |
| Cloud expiry observed locally | Mark local copy stale/unavailable and remove it during reconciliation; Gallery is unaffected. |
| Permission revoked | V1 selected imports remain valid; future album functionality is paused (V2 only). |

## 16. Implementation order

1. Confirm the Android Supabase Auth JWT/session path and map `auth.users.id` to Baroness persona/profile identity.
2. Confirm or add the relationship membership contract; prohibit client-supplied persona/relationship authorization.
3. Add the V1 Supabase migration, strict RLS, commit/upload-intent RPC boundaries, and configurable retention settings.
4. Add the Supabase Edge Functions that validate JWT/membership, sign R2 URLs, commit idempotently, and delete objects.
5. Provision the private R2 bucket, multipart-abort safety rule, secrets, and orphan-scanner schedule.
6. Add Room Moment metadata/transfer/local-state entities and a non-destructive database migration.
7. Implement Photo Picker staging, relationship-scoped SHA-256 identity, and `MomentUploadWorker`.
8. Implement signed thumbnail/full-resolution downloads, checksum verification, deterministic `MomentFileStore`, and `MomentDownloadWorker`.
9. Implement local-first repository reads and Realtime/periodic reconciliation.
10. Implement server retention warning/expiry cleanup and the local stale-copy deletion rule.
11. Implement MediaStore Gallery export and bulk warning actions; do not add cloud protection.
12. Replace mock `PhotoRepository` behavior behind the existing Photos route/screen shell.
13. Audit and reduce broad media permissions; do not implement album sync in this phase.
14. Audit `gallery_items`; migrate only real required data, then retire its permissive policies.

## 17. Decisions made in this refinement pass

1. **Cloud expiry remains authoritative.** An unexported Moment is deleted at expiry; an app-private copy cannot defeat retention.
2. **Gallery export is the only V1 permanent preservation mechanism.**
3. **No V1 `PROTECTED` state.** Permanent cloud protection is postponed rather than silently implied by export.
4. **Deduplication is relationship-scoped.** The uniqueness constraint includes `relationship_id`, and object keys are relationship-scoped.
5. **Room does not store absolute file paths.** `MomentFileStore` derives deterministic paths from IDs; Room stores local state and verification/export timestamps.
6. **The V1 server schema excludes collections and server-side device state.** Those are V2; cleanup/audit/upload tables remain because they provide correctness and recovery.
7. **Supabase Edge Functions are the V1 R2 signing boundary.** They minimize new infrastructure and can validate the existing Supabase identity/RLS model.
8. **Supabase Auth identity validation is the first implementation step.** Custom gate/persona IDs are not accepted as authorization claims.
9. **Photo Picker individual import is the entire V1 media-import scope.** Ongoing album synchronization is separately gated for V2.
10. **No new Gradle module or generic architecture framework is justified.**

## 18. Final implementation-review checklist

- [ ] Auth/session bridge to `auth.uid()` is demonstrated on Android.
- [ ] Persona-to-relationship membership is server-resolved.
- [ ] RLS tests reject client-supplied foreign relationship/persona IDs.
- [ ] R2 bucket is private and Edge Function secrets are not shipped.
- [ ] Relationship-scoped duplicate constraint and key layout are implemented together.
- [ ] Room paths are deterministic and not authoritative database values.
- [ ] Cloud expiry deletes originals/thumbnails and eventually removes local app-private copies.
- [ ] Gallery export survives app data clear/uninstall in device testing.
- [ ] Cleanup retries and object/DB divergence are tested.
- [ ] V1 contains no background album scanner and no `PROTECTED` state.
