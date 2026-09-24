# Modularization, fault-injection, fuzzing, and performance roadmap

This document defines incremental exit criteria. A module is not considered
extracted merely because code was copied: `:app` must consume its public API and
the extracted module must not depend on Android UI or a concrete provider.

## Module sequence

1. **Complete:** `:core-files-api` owns provider-neutral `FileNode`,
   `FileMetadata`, and provider capability contracts.
2. **In progress:** `:core-files-api` now owns path-independent provider failures,
   retry and user-action decisions, conflict policies, cancellation, resumable and
   partial results, metadata-preservation reports, and transfer/sync plans. Provider
   adapters should translate at their boundary rather than recreate these policies. Local atomic
   moves and ordinary local deletions now use `ProviderOperationRunner`; recursive native fast-path
   deletion and copy/move streaming still need specialized progress-aware adapters before they can
   migrate without losing performance or cancellation fidelity. Remote adapters remain incremental.
   Common bounded JVM exception/cause-chain classification is now centralized in
   `ProviderJvmFailureClassifier`; adapters still translate protocol-specific failures before using it.
3. **In progress:** `:feature-vault-domain` owns framing, lock/session transitions,
   mutation admission, and recovery transitions. Android Keystore, JSON persistence, crypto
   adapters, and activities remain in `:app`. Sibling-name uniqueness is now a domain invariant;
   the app's JSON-backed entry model implements the minimal provider-neutral identity contract
   directly without changing the persisted Vault format.
4. **In progress:** `:feature-transfer-domain` owns transfer, sync-run, and Nearby session
   state machines without Service, WorkManager, or database dependencies. Already-resolved
   `FileOperationRequest` batches now pass through a bounded, capability-aware neutral planning
   policy before Nearby send persistence. Concrete URI/path resolution, provider enumeration,
   receive-side sibling probing and effects, database persistence, scheduling, and optimized
   execution remain in `:app` and migrate only where their decisions become provider-neutral.
   Durable-work resume sequencing, the shared transfer execution limit, and Nearby payload
   correlation/admission now live here as provider-neutral policies. Transfer Center
   primary/secondary actions, state sections, filters, queue-reorder eligibility, and history-clear
   eligibility are neutral policies here; RecyclerView presentation, resources, repositories,
   services, Activity routing, and concrete destination resolution remain in `:app`.
5. Split FTP, SFTP, SMB, and rclone behind the provider API one at a time. Do not
   move all providers in one change. The local NIO mutation adapter is the first migrated
   provider and must continue satisfying its cancellation, timeout, disk-full, permission,
   conflict, stale-resource, cleanup, and metadata contract tests.
   The concrete SFTP copy/move adapter now has deterministic protocol-fault coverage for
   interruption, timeout, authorization, conflicts, stale resources, connection loss, partial
   writes, capacity failures, cleanup failures, rename fallback, symlinks, and bounded metadata
   reporting. SFTP byte channels remain seekable, but the mutation adapter does not advertise or
   implement checkpoint-safe resume. FTP copy/move now has a deterministic protocol seam and
   covers interruption, conflicts, stale sources, partial writes, cleanup, rename fallback, and
   bounded metadata reporting. Rclone filesystem mutations now use a deterministic RPC seam and
   cover interruption, conflicts, stale resources, directory delegation, cross-remote copy/move,
   and file-versus-directory deletion. SMB copy/move now uses a deterministic protocol seam and
   covers interruption, conflicts, partial-copy interruption, rename fallback, fallback cleanup,
   and bounded metadata warnings.

## Android component decomposition

- `:feature-browser-domain` now owns browser command vocabulary, availability, dispatch,
  selection cardinality, and provider-neutral Back/up/restoration decisions without Android
  resources or concrete paths. App adapters resolve local, archive, SAF, and remote hierarchy
  facts; `BrowserNavigationCoordinator` executes selection, search, and concrete navigation effects.
  `FileListFragment` retains lifecycle, view binding, fragment transactions, menu-ID adaptation,
  and concrete UI effects. Its `onViewCreated` entry point now only orchestrates focused workspace,
  content-view, and state-observer binding phases. Navigation and operation effects remain behind
  coordinators in `:app`.
- `NearbyTransferService` retains foreground-service and Google Nearby callbacks. Its domain
  controller now projects UI and durable operation state and safely rejects duplicated or late
  callbacks. A transport-free payload ledger now owns duplicate/overlap admission, metadata/stream
  correlation, and serializable checkpoints; Android still owns Google `Payload` and stream handles.
  Versioned, bounded payload checkpoints now persist through the session store and are reconciled
  against durable operation state after process recreation. Because Google Nearby stream handles
  cannot survive process death, recovery deliberately discards every old transport correlation;
  reconnect and fresh payload delivery are required before transfer can become active again.

## Source-contract test migration

The Nearby integration source test and APK-signing workflow source contract have been replaced by
payload/state-machine tests and a durable codec contract respectively. The APK contract serializes a
representative operation and verifies its exact metadata schema cannot contain signing secrets;
ephemeral one-use secret behavior is exercised directly. Dependency and API use are compile-time
enforced, while Android component declarations remain covered by Android tooling rather than class-
name/call-spelling assertions. Existing source-reading tests outside this architectural slice remain
technical debt: migrate behavioral wiring checks to fakes/contracts, build configuration checks to
Gradle rules, provider claims to adapter conformance suites, and retain only narrowly documented
source-policy bans for which compilation or runtime tests cannot express the invariant.

Selected browser restoration/Back wiring and Transfer Center destination contracts now exercise
the app coordinators and concrete resolution behavior instead of Fragment/Activity source spelling.
SMB address/authentication edge cases and rclone native-error detail now have direct outcome tests;
their corresponding source assertions were removed. CI caps source-contract test files at 90 so this
debt cannot grow without first migrating an existing assertion. Resource and manifest structure,
build policy, security prohibitions, and native/API boundaries remain intentionally static where
runtime tests would not protect the same artifact or defense-in-depth invariant.

## Fault-injection matrix

Every file/provider engine must accept deterministic fakes at stream, clock, and
provider boundaries and cover:

| Fault | Required assertion |
| --- | --- |
| Interruption/cancellation | No new mutation starts; resumable work becomes recoverable and terminal state is preserved. |
| Timeout | Classified as transient, resources close, and bounded retry does not duplicate completed bytes. |
| Disk full / short write | Partial output is removed or explicitly recoverable; source remains untouched. |
| Permission revocation | No automatic retry loop; operation waits for an explicit user decision. |
| Process death | Durable running work reconciles to recoverable state; secrets and ephemeral URI grants are not serialized. |

Existing runtime tests already cover interruption, timeout, access-denied
classification, transfer recovery, and several parser limits. New tests should use
faulting streams/providers rather than source-text assertions.

The neutral runner tests are deliberately named runner contracts rather than provider conformance
tests. The local NIO adapter has its own fault-injected suite; provider conformance claims apply only
after a concrete adapter executes the matrix.

Provider-returned delayed state is bounded: document queries interpret provider
errors before loading state, cap delayed refreshes, unregister observers on every
completion path, and bound provider-controlled diagnostic text before surfacing it.

## Fuzz targets

Seed corpora and bounded fuzz targets are required for:

1. Vault encrypted-payload framing.
2. Nearby compressed control messages.
3. Archive entry names, link targets, counts, and declared sizes.
4. Document-provider cursor metadata and loading/error bundles.
5. JNI archive, 7-Zip, and MOBI entry points under ASan/UBSan on the host where
   the upstream library supports it.

Crashes, hangs, allocations over the target budget, traversal outside the staging
root, and uncaught native exceptions are failures. Corpus regressions must be
checked in without sensitive documents.

The scheduled hardening workflow now replays checked-in host corpora with wall-clock bounds and
performs deterministic bit-flip mutation of vault frames, archive paths, and document-provider
diagnostics. Android instrumentation supplies cross-package oversized metadata and permission-
revocation fixtures; JNI corpus replay remains in the API 30 device lane.

Jazzer now drives coverage-guided vault-frame and archive-path targets on the scheduled host lane.
The fixed-seed tests remain the fast regression layer, while Jazzer explores new branches under a
bounded wall-clock and memory budget.

## Performance benchmark contracts

Benchmarks must record median and tail latency plus allocations where available:

| Scenario | Dataset and first budget to establish |
| --- | --- |
| Large directory rendering | 10k mixed entries; diff/sort and bind measured separately. |
| Search indexing | 100k paths; initial build and incremental rename/delete batches. |
| Recursive operations | 10k files / 1 GiB synthetic tree; scan and transfer separated. |
| Remote enumeration | 100 paged responses with injected 50 ms latency; time-to-first-page and completion. |
| Archive browsing | 10k entries with deep but valid paths; open and filtered listing. |
| Thumbnail generation | Cold/warm batches for image, audio, video, APK, and document inputs. |

Use AndroidX Benchmark for device/UI/media work and JMH for pure JVM planners.
Benchmarks start as non-blocking artifacts; promote a regression budget to a CI
gate only after at least 20 stable baseline runs on a fixed runner/device class.

Host baseline collection writes schema-validated CSV and environment metadata as a retained
scheduled-workflow artifact. These heterogeneous hosted-runner results remain informational.
Collection/allocation calibration scenarios are explicitly named `harness.*`; only scenarios that
invoke production domain policies are treated as product-code measurements.
