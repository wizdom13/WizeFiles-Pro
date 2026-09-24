# Priority-zero correctness risk register

This register turns high-risk inline TODOs into owned engineering categories. It
does not catalog cosmetic refactors or compatibility comments. Remove an entry
only when runtime tests cover the resolved behavior.

## P0 — no open entries

Distributable release and beta workflows enforce the unresolved entries through
`scripts/verify-release-risk-gates.sh` and `config/p0-release-gates.txt`. A gate may
be removed only in the same change that adds the outcome-based regression tests
required below. Debug builds and pull-request tests remain available so fixes can
be developed while release artifacts stay blocked.

| Resolved area | Resolution and regression coverage | Completed |
| --- | --- | --- |
| Browser search concurrency | Results are generation-checked again on the delivery executor; deterministic queued-result tests prove a superseded generation cannot publish. | 2026-08-25 |
| Destructive conflict replacement | Incompatible file/directory replacement is rejected before conflict resolution; local and remote-provider policy paths have outcome tests. | 2026-08-25 |
| Text editor write access | Provider write checks are centralized and repeated immediately before mutation; shared-storage access can be requested and denial, revocation, and read-only decisions are tested. | 2026-08-25 |
| SMB watch cancellation | The SMBJ cancel workaround is isolated behind a close-and-bounded-wait policy with structured lifecycle telemetry and deterministic completion/timeout tests. Revalidate the policy when SMBJ is upgraded. | 2026-08-25 |
| SMB copy metadata | Basic timestamps and DOS attributes are applied after content writes; unsupported ACLs and extended attributes, plus basic-metadata failures, produce structured warnings consumed by file-job completion UI. | 2026-08-25 |

## P1 — schedule in the next provider/browser hardening cycle

| Area | Existing marker / risk | Required exit criteria |
| --- | --- | --- |
| Browser state restoration | Search UI state uses `SavedStateHandle`, but process death may still lose navigation or selection state. | Specify safe navigation/selection restoration without persisting credentials or transient grants, then add process-recreation tests. |
| Document provider loading | Delayed and error results are now interpreted by a bounded pure policy; observer waits time out and unregister on completion or cancellation. | Add broader cross-process device fixtures as individual provider compatibility issues are discovered. |

Browser process restoration now has an explicit safe-state contract: search presentation is
restored, while navigation is reconstructed from the trusted launch route and selection is cleared.
Provider paths, credentials, transient URI grants, and selected provider objects are never written
to `SavedStateHandle`; deterministic tests enforce the durable-key allowlist.

## Triage rules

1. **P0:** possible data loss, stale concurrent state, privilege confusion, leaked resources, or silent write failure.
2. **P1:** lifecycle restoration, metadata fidelity, bounded provider behavior, or actionable error reporting.
3. Every fix needs an outcome-based test; source-text assertions alone are not sufficient.
4. New destructive-operation TODOs must state whether a mutation has already occurred and how retry/rollback behaves.
5. Provider work must cover cancellation, timeout, read-only state, and partial completion.
6. Source-contract tests are capped by CI and may grow only when an existing source assertion is
   replaced by an outcome-based test in the same change.
