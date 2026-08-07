# Health report dashboard and Release presentation regression contract

## Minimum reproduction and root cause

1. A first report-history request fails. The legacy Android view model converts the failure to an
   empty list, so Release UI claims that no report exists instead of exposing a readable error.
2. Open a completed report interpretation whose server payload contains internal IDs or dictionary
   keys. The legacy Compose view directly renders candidate, observation, confirmation-event,
   algorithm, evidence, missing-input, and failure-code values.
3. Start the same report from Health Data and a chat attachment. Each legacy view model owns its own
   upload state and calls the legacy multipart endpoint, so page recreation and sibling entry points
   do not share the versioned local-first upload lifecycle.

The root cause is the absence of one account/subject-owned dashboard state machine and one Release
projection boundary. Network DTOs, internal workflow identity, and user-facing text were previously
mixed in feature-local Compose branches.

## Permanent invariants

- Dashboard content is exactly one of loading, available, first-load error, or empty. A cached report
  remains available during refresh failure.
- Workflow presentation distinctly covers recognizing, awaiting review, committing, recoverable,
  score pending, completed, and unknown states. A failure never masquerades as recognizing.
- The report dashboard exposes exactly one primary upload action. Health Data and future chat,
  camera, gallery, file, and external-import entry points share one singleton upload state owner and
  the versioned `HealthReportUploadCoordinator`.
- The singleton state is bound to immutable account scope, numeric subject, and auth generation.
  Old-account responses and A -> B -> A callbacks cannot update the visible state.
- Release Compose consumes only `HealthReportReleasePresentation`. It never renders internal event,
  candidate, observation, trace, workflow, asset, storage, snapshot, algorithm, or failure IDs/codes,
  raw JSON, or arbitrary server dictionary keys.
- Unknown server text and unknown failure/status codes fail closed to stable user wording.

## Sibling entry points and states scanned

- Health Data report dashboard and legacy exam history.
- XAGE/chat attachment use of `HealthDataViewModel` (shared state integration only; Chat UI is not
  modified in this batch).
- Report review, interpretation, original-file availability, failure recovery, score pending, and
  completed interpretation.
- Loading, empty, first-load error, cached refresh error, recognizing, awaiting confirmation,
  committing, recoverable failure, score pending, completed, unknown future status, duplicate,
  concurrent upload, account switch, and page recreation.

## Named verification

- `HealthReportDashboardStateTest`
- `ReleasePresentationWhitelistTest`
- `HealthReportDashboardUiTest` (deterministic Compose fixture; no public network)
- Adjacent `HealthReportUploadCoordinatorTest`, `ReportTrustPresentationTest`, and report-review tests.

Focused commands are intentionally deferred until the shared tree is compile-safe. The eventual
commands must compile main/unit/androidTest sources, run the two named JVM classes and adjacent
health-data JVM tests, then run the repository exact inventory and impacted gates. Connected UI is a
separate coordinated run and is not implied by JVM or compile evidence.
