# iOS → Android parity contract (2026-08-07)

## Authority and scope

- Reference implementation: `/Users/linlin/Desktop/X/XJie_IOS` current `main` worktree.
- Reference layers: shipped iOS 1.0 (22) plus the current uncommitted 2026-08-07 daily-score confidence changes.
- Committed iOS base: `db9b9c0d8843db7171c5fa211b7fae1185bd7549` (tree `9e61195a7cd891736d5cdcdbf16f1b521f88709f`). The binary diff of the seven uncommitted iOS XAGE production Swift files used for the current daily-score reference had SHA-256 `d6f5d409c9dad27fa59117feac24df295bc8328f8c66e1f3c19266c99215d406`; the iOS repository remained read-only for this Android delivery.
- Authorized mutation scope: `/Users/linlin/Desktop/X/XJie_And` only. The iOS repository is read-only.
- Android platform substitutions must be explicit: Health Connect replaces HealthKit; Android notification, picker, back, keyboard, safe-area, and accessibility semantics replace their iOS equivalents.
- `XAge` remains server-trusted and fail-closed. Local daily pressure, recovery, and inflammation estimates must never enable or populate `XAge`.
- `backend/analysis/` and `backend/analysis_outputs/` are unrelated user material and are excluded from parity delivery.

## Minimum reproduction of the current gap

1. Launch the Android XAGE shell and open the Data page.
2. Observe that pressure, recovery, and inflammation are hard-coded to `null` and render `--`, while the current iOS source renders a neutral daily reference value even with zero evidence and an independent completeness ring.
3. Swipe horizontally across Data / Chat / XAge. Android does not page because the shell uses a direct `when` state switch; current iOS uses a page container.
4. Open Reports, Medical Assistant, Meals, Medication, and Profile from XAGE. Android routes to legacy list/CRUD surfaces or incomplete trust screens instead of the current iOS dashboard/state-machine surfaces.
5. Switch account A → B → A while an Android request is in flight. The Android auth state has no monotonic generation, so late A results are not rejected consistently.
6. Run the Android repository backend. Current client trust endpoints are missing because that backend stops before iOS migrations and routes 0020–0026.

## Confirmed root causes

1. Android parity stopped at the 2026-07-05 XAGE shell and a 2026-07-15 trust-policy batch, while iOS continued through build 22 and the 2026-08-07 daily-score work.
2. Equivalent behavior is duplicated across feature-local view models instead of being owned by shared account-generation, upload, presentation, and navigation state machines.
3. Android has exact JVM/static gates but no deterministic Debug-only UI transport or Compose UI inventory, so interaction and release-presentation drift can remain invisible.
4. The Android repository still carries an old backend snapshot and a legacy release configuration (`api.example.com`, no release signing), so a debug build cannot be presented as a publishable Android release.
5. Historical Android configuration ended the public base URL in `/api` while all 155 Retrofit declarations also own `api/`; this produced `/api/api/...` and depended on an nginx compatibility rewrite instead of the canonical iOS route.
6. Android support/compliance remained pinned to the 2026-07-15 reachability repair (`personal-data`, policy date 2026-04-09) while current iOS moved to a shared `permissions` destination, policy version 2026.07 dated 2026-07-26, eight full privacy topics, and per-capability timing/purpose/refusal disclosures.
7. Android registration still submitted without explicit user-agreement/privacy acceptance and exposed the research subject switch that current iOS hides; legal content had no shared versioned source or fail-closed client guard.
8. Server trend names were converted to feature-local card IDs, truncated to four results, and then merged with a separate static Weight card. A real `体重` trend could therefore duplicate the card and route to the wrong destination; legacy combined blood pressure could also bypass the current two-metric identity policy.
9. The first deterministic transport matched too broadly and returned a retry-shaped synthetic status for unknown traffic. Support/device routes also prefetched account settings they did not consume, making an unrelated request part of those UI flows.
10. Compact-profile tests initially assumed every long state was already in the viewport and used system back while a notice dialog was still dismissing. Those observations were not stable evidence for scrollable Android layouts or explicit close ownership.

## Permanent invariants

### Identity, consent, and network

- Every account-bound async operation captures account scope, subject, and a monotonic auth generation before its first suspension and revalidates them before every mutation, retry, upload batch, and UI commit.
- A → B → A never makes a stale A result current. Logout and every account/subject transition advance generation.
- HTTP 403 never silently enables AI consent. Consent changes require an explicit user action and an authoritative successful response.
- Deterministic UI mode is Debug-only, recognizes an exact allowlist, fails closed for unknown requests, records all requests, and proves that no request escaped to the public network.
- Deterministic authenticated fixtures require the exact production origin, exact Debug JWT, method, path, query and body. Unknown or malformed requests return non-retryable `418`; support and device routes never prefetch account settings.
- Every Android network entry point resolves through `ApiEndpointPolicy`: a legacy base ending in one or more `/api` segments is reduced to the origin, every route owns exactly one relative `api/` prefix, and arbitrary base paths fail closed.

### XAGE shell and daily scores

- Data / Chat / XAge support capsule taps and horizontal paging with a single selected-page source of truth; returning from a child destination restores the originating page.
- Active quick actions are exactly Meals, Weight, Reports, Medication, and Medical Assistant, with account-scoped persistent ordering. Profile remains in More. Hidden legacy shortcuts cannot reappear as duplicate destinations.
- Daily pressure, recovery, and inflammation use one versioned policy and only Health Connect samples or admitted trusted trend observations.
- Zero evidence produces display value 50 with 0% completeness; sparse evidence still produces a visibly low-confidence reference score. Unknown source, unit, range, or date fails closed.
- Completeness is based only on relevant fixed signal sets (pressure 6, recovery 5, inflammation 8 with a qualified laboratory anchor or 6 on the explicit proxy path), freshness, and evidence quality. Irrelevant sample volume cannot inflate it.
- The completeness arc is geometrically outside the score arc. Values below 60% expose a 44dp explanation control, dynamic missing-signal text, and an accessibility summary. Present signals are never also described as missing.
- Unloaded/unavailable states never claim that an estimate was generated. Server-trusted score fields remain separate from local daily-display fields.
- Every server trend passes through the complete versioned metric-identity registry before presentation. The registry rejects the legacy combined blood-pressure card, maps Weight to the one `bodyWeight` destination, preserves all admitted current metrics, and deduplicates by canonical ID and title rather than by a feature-local result limit.

### Reports, meals, medication, medical assistant, and profile

- Report entry points (dashboard, chat attachment, external import, camera, gallery, files) share one app-level, account/subject-bound, single-flight upload coordinator.
- Original report bytes are persisted atomically before network I/O in an account/subject namespace with digest and size verification. Asset-set ordering, duplicate handling, recovery, acknowledgement, retirement, and trace/history are explicit states.
- Release UI uses a presentation whitelist and never displays internal event, candidate, observation, trace, storage, or failure identifiers.
- The report dashboard distinguishes loading, empty, error, recognizing, awaiting review, committing, failed/recoverable, score-pending, and completed states and has one primary upload action.
- Meal recognition creates an account/subject-bound pending draft. Only explicit versioned confirmation creates a formal record. The business day boundary is Asia/Shanghai 04:00 and retries are idempotent.
- Medication keeps the existing trusted server policies but presents the current next dose as the dominant hero, exposes only real destinations, and reports actual notification scheduling state.
- Medical Assistant overview is server-authoritative, generation-bound, distinguishes stale/empty/error/generating/ready states, and never fabricates recency or source counts.
- Health Profile preserves multiple goals, management plans, revisions, and server-authoritative completeness. Safety fields and report-derived candidates never auto-confirm.

### More, account, support, and platform permissions

- Support destinations are exactly help, version, privacy, permissions, and feedback; policy/permission content is one versioned local source shared by every Android entry point.
- Android substitutes Health Connect, the system file/photo picker, notification/exact-alarm controls, package installer, and boot reminder recovery explicitly. Every requested capability states timing, purpose, and refusal impact; Bluetooth/NFC device binding remains visibly unsupported until real protocols and ownership/revocation contracts exist.
- Feedback accepts 2–2000 trimmed characters, protects non-empty drafts, and closes only after authoritative success. Logout always clears the current local session; account deletion clears it only after the server confirms deletion.

### Login and registration

- Phone login remains available without registration agreement state. Signup cannot start until both the user agreement and the shared versioned privacy policy are explicitly accepted, including the explicit confirmation path.
- The ordinary production login surface is branded `小捷` and does not expose the research-only subject login launcher. Legal documents remain readable before acceptance and closing them never mutates consent.

### Release and evidence

- Release must reject placeholder API URLs, broad cleartext, absent signing, debug signing, wrong package/version, and an unverified AAB/APK digest.
- Exact source and executed test inventories reject missing, extra, duplicate, skipped, failed, renamed, or stale results.
- Automated client tests do not prove production AI quality, production backend deployment, Health Connect authorization, notification delivery, OEM keyboard behavior, or real-device TalkBack.
- Scrollable UI assertions must first reveal long/off-screen content. Dialog dismissal must be observed through an explicit state tag before the owning close action is used; an immediate system-back race is not acceptable evidence.

## Same-class entry points and states to scan

- Entry points: login/subject selection, XAGE quick actions, More, legacy home, chat attachment, external intents, camera, gallery, files, report history, push/deep links, notification taps, settings, and process relaunch.
- State transitions: first launch, logged out, account/subject switch, background/foreground, process death, offline, timeout, retry, duplicate tap, duplicate file, stale response, partial upload, permission revoke, and server revision change.
- Layout states: empty, loading, error, long content, keyboard shown/hidden, back/close, page switching, gesture navigation, compact phone, tablet, large font, edge-to-edge safe area, TalkBack traversal, and 44dp touch targets.

## Traceable delivery matrix

| Batch | Reference surface | Android target | Named regression anchor | Status |
|---|---|---|---|---|
| A | Auth/account generation, explicit AI consent, canonical API prefix | `core/auth`, network, account-bound view models | `AuthGenerationIsolationTest`, `ExplicitAiConsentTest`, `ApiEndpointPolicyTest` | implemented; final exact JVM/Python gates passed |
| B | XAGE daily score policy and confidence ring | `feature/xage` shared algorithm/presentation/UI | `XAgeDailyScoreAlgorithmTest`, `XAgeDailyScoreEvidenceContractTest`, `XAgeDailyScorePresentationPolicyTest` | implemented; exact JVM and all three UI profiles passed |
| C | Three-page shell, quick-action ordering, return restoration | `feature/xage`, navigation | `XAgeShellStateTest`, `XAgeShellSwipeUiTest` | implemented; capsule, swipe, keyboard, return and reorder flows passed in all three UI profiles |
| D | Report local originals, upload coordinator, dashboard, presentation whitelist | health-data model/API/repository/view-model/UI | `HealthReportLocalOriginalStoreTest`, `HealthReportUploadCoordinatorTest`, `HealthReportDashboardStateTest`, `ReleasePresentationWhitelistTest` | implemented; JVM, API 35 device-store and deterministic dashboard regressions passed |
| E | Medical Assistant overview | model/API/repository/view-model/UI/navigation | `MedicalAssistantOverviewContractTest`, `medicalAssistantUsesDeterministicOverviewAndReturnsToDataPage` | implemented; explicit notice dismissal/close return passed in all profiles |
| F | Trusted meal workflow | meal model/API/repository/view-model/UI | `DietaryDraftAdmissionTest`, `DietaryBusinessDayTest`, `DietaryIdempotencyTest` | implemented; exact JVM and scroll-safe deterministic UI passed |
| G | Medication dashboard parity | medication UI/reminder state | `MedicationDashboardPresentationTest`, `MedicationDashboardUiTest` | implemented; dominant hero, independent actions and real secondary destinations passed in all profiles |
| H | Full profile goals/plans/revisions | profile model/API/repository/view-model/UI | `HealthProfileCompletionParityTest`, `HealthProfileStateMachineTest` | implemented; exact JVM and long-content deterministic UI passed |
| I | Backend compatibility snapshot 0020–0026 | Android repository backend only | exact 392-test backend unit inventory | complete locally: iOS `db9b9c0` tracked backend 261/261 byte-identical; exact 392/392 passed |
| J | Debug deterministic UI transport and Compose UI inventory | debug source, androidTest, CI/quality | `DebugUiAutomationTransportContractTest`, exact UI inventory | implemented; exact 14/14 on standard, compact and large-text API 35 (42 executions), zero failure/skip/extra |
| K | Release configuration, signed artifact, hosted CI, GitHub main delivery | Gradle/manifest/quality/GitHub | `ReleaseConfigContractTest`, `ReleaseArtifactVerifierContractTest`, `test_ci_requires_kvm_before_emulator_and_keeps_evidence_upload_fail_closed` | local fail-closed guards implemented and fixture-tested; first exact-head run `31175685594` exposed missing hosted KVM permissions before any UI test; official KVM admission, no-software-fallback and failure-evidence contracts now pass locally; replacement exact-head run and merge/main CI pending; real Release remains blocked on explicit production URL, external signing and a verified candidate package |
| L | Structured/SSE health chat, evidence, citations, idempotent replay | chat model/API/repository/view-model/UI | `ChatStreamingParityTest`, `ChatRepositoryStreamingTest`, `ChatViewModelStreamingParityTest`, `ChatCitationReplayParityTest` | implemented; exact JVM/backend and deterministic native-Markdown UI passed |
| M | XAGE Weight detail, height admission, BMI, trend | weight/health-data model/repository/view-model/UI/navigation | `WeightDashboardParityTest`, `XAgeMetricIdentityPolicyTest`, deterministic weight UI | implemented; quick action and canonical `bodyWeight` server card share one flow and passed all profiles |
| N | More/account/security/privacy/permission/support/feedback | settings/account/support UI/navigation | `XAgeSupportComplianceParityTest`, `XAgeSettingsLoadPolicyTest`, deterministic support flow | implemented; current policy/permission routes, feedback, device honesty and no-prefetch contract passed |
| O | Login/register legal consent and production entry | login policy/view-model/UI | `LoginLegalConsentPolicyTest`, `LoginSingleFlightSubmissionTest`, deterministic registration UI | implemented; two explicit agreements, readable legal documents and production entry passed all profiles |

## Verification plan

For every batch:

1. Add/update the named regression so the old behavior fails.
2. Run the focused JVM/static/UI test, the adjacent subsystem tests, exact inventory, `assembleDebug`, and `lintDebug`.
3. For UI batches, run deterministic Compose tests on at least compact phone and standard phone; capture and inspect screenshots for the layout states listed above.
4. At stabilization, run the full Android gate from a final tree, then hosted GitHub CI on the exact candidate SHA.
5. Record exact commands, results, artifact digests, remaining risks, and real-device limitations in Android quality evidence, development history, and workspace memory.

Baseline on the pre-parity dirty worktree (2026-08-07): tools/static 11/11; exact JVM source and executed inventory 80/80; `testDebugUnitTest`, `assembleDebug`, and `lintDebug` passed. This baseline is not completion or release evidence.

## Final local verification snapshot

- Exact JVM inventory and execution: `232/232`, with zero missing, extra, duplicate, failed or skipped results.
- Exact Android quality-tool Python inventory and execution: `43/43`, with zero failure, error, skip or expected failure.
- Exact connected UI inventory: `14/14` on each of `standard_api35`, `compact_api35` and `large_text_api35`; `42/42` total executions, zero failure/error/skip/extra.
- Exact backend inventory and execution: `392/392`; the 261 files tracked by iOS reference `db9b9c0d8843db7171c5fa211b7fae1185bd7549` are present and byte-identical in the Android repository backend.
- `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug` and `git diff --check` passed. The generated Debug APK is development evidence only.
- Visual review used the current iOS login/XAGE references and final Android API 35 captures. The login structure and XAGE hierarchy align; Health Connect is the explicit Android substitution for Apple Health. Fixture/state differences mean the composites are semantic/layout evidence, not pixel equality or real-health-data evidence.
- The first full connected run passed standard `14/14`, then failed three of the 14 compact tests; large text did not run because the script failed closed. The three preserved failures exposed an oversized medication-hero boundary, a medical-assistant dialog/back race, and off-screen meal content observed without scrolling. Explicit responsive bounds, dialog-state/close ownership and scroll-to observation were then locked by named regressions before the complete three-profile rerun passed.
- The first hosted exact-head run `31175685594` passed backend `392/392` and JVM `232/232`, then failed before UI instrumentation because `/dev/kvm` was not accessible and the runner silently used `-accel off`; the software-emulated device became unresponsive and started `0` tests. The workflow now requires the official KVM udev rule/read-write probes, explicitly forbids Linux hardware-acceleration fallback, disables the future metrics prompt, always records failure evidence, and clears raw results before every profile. The named KVM regression rejects the old workflow; local Python `43/43` and all three API 35 profiles `42/42` passed after the correction.
- Detailed commands, artifact digests, visual paths, preserved red results and remaining boundaries are recorded in `quality/evidence/ios_android_parity_20260807.md`.

## Delivery and Release boundary

- Main-based feature branch `codex/android-ios-parity-20260807` was first pushed as `f9f581f2cec1cf44a2fae0a5524d58cd13e81e8d`; PR [#1](https://github.com/doyoulikelin-wq/Xjie_andro/pull/1) targets `main`. Its first exact-head run [31175685594](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31175685594) concluded **failure** at the pre-test emulator boundary described above. The repair is locally verified; its replacement exact-head SHA/run, merge SHA and post-merge `andro/main` CI remain pending and must not be inferred.
- `verifyReleaseConfiguration` correctly exits `1` because no explicit production `API_BASE_URL_RELEASE`, external release signing credentials or verified signed candidate AAB/APK was supplied. The verifier's seven fixture tests prove fail-closed inspection behavior only; they are not a real signed-package attestation.
- Therefore the Android software replication is locally verified and bound to an open GitHub PR, but exact-head CI success, merge/main delivery and a signed production Android release are not claimed by this snapshot.
