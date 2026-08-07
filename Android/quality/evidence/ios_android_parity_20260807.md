# Android iOS parity evidence — 2026-08-07

## Evidence status

- Scope: Android-only replication of the current iOS XAGE product behavior and its compatible backend snapshot.
- Status: source/GitHub delivery is complete. The parity change and evidence head passed exact-head runs `31188223697` and `31190901068`, then PR #1 merged to protected `main` as `1c09cc31d5fd725dc172762bf68067ed73553cb8`. The first post-merge run `31193076541` remains a preserved fail-closed incident: one standard-profile assertion observed the intentionally composed loading placeholder before authoritative loaded-empty state. The permanent readiness repair passed exact-head run `31195706886`, merged through PR #2 as `bebf94afdd702f7dee5d930ee8083a457f89a9ea`, and passed independent post-merge `main` run `31196747858`. Production Release remains blocked by required external inputs.
- Android repository: `/Users/linlin/Desktop/X/XJie_And`.
- Merged parity branch: `codex/android-ios-parity-20260807`; first pushed feature SHA `f9f581f2cec1cf44a2fae0a5524d58cd13e81e8d`; qualified behavior head `8263cefbaabfbc5cc7b3b1b0bfb38bf9934591fa`; qualified evidence head `5dd78092de5d295b7031d58782c8585a35efc068`; first merge SHA `1c09cc31d5fd725dc172762bf68067ed73553cb8`. Readiness repair branch `codex/android-postmerge-loaded-empty-readiness-20260807`, repair head `f49b038fb9d344d0742f409e25cb81bbf429b2e4`, final behavior merge/main SHA `bebf94afdd702f7dee5d930ee8083a457f89a9ea`.
- Target remote and branch: `andro` (`doyoulikelin-wq/Xjie_andro`) → `main`. The `origin` remote points to the iOS repository and is forbidden for Android delivery.
- GitHub delivery: [PR #1](https://github.com/doyoulikelin-wq/Xjie_andro/pull/1) merged after all three exact checks passed on evidence head `5dd7809` in [run 31190901068](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31190901068). Historical exact-head failures `31175685594`, `31179377876`, and `31181786778` and deliberately cancelled run `31184274631` remain preserved. The first exact post-merge run [31193076541](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31193076541) remains **failure** on `1c09cc3`. [PR #2](https://github.com/doyoulikelin-wq/Xjie_andro/pull/2) permanently repaired that readiness boundary; exact-head [run 31195706886](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31195706886) and final post-merge [run 31196747858](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31196747858) both concluded **success**.

## Reference authority and exclusions

- iOS committed base: `db9b9c0d8843db7171c5fa211b7fae1185bd7549`, tree `9e61195a7cd891736d5cdcdbf16f1b521f88709f`, corresponding to the Build 22 source record.
- Current daily-score authority: the seven modified iOS production XAGE/score Swift files above that base. Their binary diff used during final comparison had SHA-256 `d6f5d409c9dad27fa59117feac24df295bc8328f8c66e1f3c19266c99215d406`.
- The iOS repository was read-only. No iOS code, test, quality, history or memory file was changed for this Android task.
- `backend/analysis/` and `backend/analysis_outputs/` are excluded user material. They were not read as product authority, modified, cleaned or included in delivery.
- No `.env`, password, token, private signing key, production credential or raw user medical material is recorded here.

## Reproduction and confirmed root causes

Before the repair, the Android product stopped at an older XAGE shell and trust-policy checkpoint:

1. Daily pressure, recovery and inflammation stayed `--` instead of showing the current neutral `50` reference with an independent `0%` completeness boundary.
2. Data / Chat / XAge did not share the current iOS page state and gesture behavior.
3. Reports, Medical Assistant, Meals, Medication, Weight, Profile, More/support and registration reached legacy or incomplete surfaces.
4. Account-bound asynchronous work lacked a universal monotonic generation lease, so A → B → A could admit stale mutations.
5. Android's backend stopped before current migrations/routes, while the release build carried a placeholder URL/no formal signing contract.

The mechanism was duplicated feature-local ownership: account state, uploads, navigation, presentation and trust admission were each reimplemented in multiple view models or screens. Android also lacked an exact Debug-only UI transport and connected-test inventory, so response-shape, long-content, back/close and profile drift could escape the JVM gates.

The final same-class scan additionally found three cross-cutting causes:

- Server metric names had no complete canonical identity registry; `体重` could be duplicated or miss the dedicated Weight route, legacy combined blood pressure could reappear, and a four-card truncation hid admitted metrics.
- Deterministic traffic matched too broadly and used a retry-shaped unknown status; support/device pages issued an unrelated account-settings preload.
- Compact UI tests assumed long content was already visible and used system back during dialog dismissal, confusing observation races with product behavior.

## Permanent invariants and constraint owners

- Account/subject/generation is captured before suspension and revalidated before every mutation, retry, upload and UI commit. Logout and every account/subject transition advance generation.
- Explicit AI consent is authoritative; HTTP 403 never silently enables consent.
- `ApiEndpointPolicy` owns canonical origin/one `api/` prefix resolution and fails closed on arbitrary base paths.
- Daily scores consume only admitted, relevant, deduplicated and fresh evidence. Zero evidence is `50/0%`; service-trusted score fields and XAge remain separate and fail closed.
- `XAgeMetricIdentityPolicy` is the one server/candidate metric registry. It maps the real Weight trend to `bodyWeight`, rejects legacy combined blood pressure, preserves admitted metrics and deduplicates canonical identities.
- Report entry points share one generation-bound, local-original-first, single-flight coordinator and one Release presentation whitelist.
- Meal drafts require explicit versioned confirmation; medication occurrences/prefills/reminders remain typed by trust level; profile completeness remains server-authoritative.
- More/support/legal/permission content has one current local policy source. Android substitutions name Health Connect, system picker, notifications/exact alarms, package installer and boot recovery honestly; unsupported Bluetooth/NFC is not simulated.
- `DebugUiAutomationTransport` exists only in Debug, requires exact origin/JWT/method/path/query/body, returns non-retryable `418` for unknown traffic, records requests and asserts zero escape. Release excludes the transport.
- Support/device routes never prefetch account settings. Network-backed text assertions wait for that exact text, retain the merged-semantics lookup, then scroll and assert visibility. Lazy-list assertions wait for a persistent loaded-state tag on an always-composed non-lazy owner, wait for and use the named scroll root, scroll to the exact target tag, and assert it is displayed. Readiness tags are scoped to the owning product state and must not leak into child editors, add list items, shift indices, or replace the scroll root tag. Dialog tests observe dismissal before using the owning close action.
- Daily-score nodes are always composed so users and accessibility services receive an honest `待更新` loading state; their existence is never a readiness signal. Any assertion of loaded score semantics must first await the authoritative always-composed `xage.data.metrics.loaded` owner through the shared `waitForLoadedXAgeData` helper. Loaded-empty remains exactly `50/0%`; loading is never relabelled as loaded-empty to satisfy a test.
- Release configuration rejects placeholder/non-HTTPS API input, absent or debug signing, cleartext, wrong identity/version/certificate, fixture markers and digest mismatch.

## Named regression anchors

The exact `232`-test JVM inventory includes the following permanent anchors:

- Identity/network: `AuthGenerationIsolationTest`, `ExplicitAiConsentTest`, `ApiEndpointPolicyTest`, `DebugUiAutomationTransportContractTest`.
- XAGE: `XAgeDailyScoreAlgorithmTest`, `XAgeDailyScoreEvidenceContractTest`, `XAgeDailyScorePresentationPolicyTest`, `XAgeShellStateTest`, `XAgeMetricIdentityPolicyTest`, `XAgeInformationArchitectureTest`.
- Reports: `HealthReportLocalOriginalStoreTest`, `HealthReportUploadCoordinatorTest`, `HealthReportDashboardStateTest`, `ReleasePresentationWhitelistTest`, `ReportReviewPolicyTest`.
- Chat: `ChatStreamingParityTest`, `ChatRepositoryStreamingTest`, `ChatViewModelStreamingParityTest`, `ChatCitationReplayParityTest`, `ChatRequestGenerationIsolationTest`.
- Meals/medical/medication/profile: `DietaryDraftAdmissionTest`, `DietaryBusinessDayTest`, `DietaryIdempotencyTest`, `MedicalAssistantOverviewContractTest`, `MedicationDashboardPresentationTest`, `HealthProfileCompletionParityTest`, `HealthProfileStateMachineTest`.
- Weight/support/login: `WeightDashboardParityTest`, `XAgeSupportComplianceParityTest`, `XAgeSettingsLoadPolicyTest`, `LoginLegalConsentPolicyTest`, `LoginSingleFlightSubmissionTest`.
- Release: `ReleaseConfigContractTest` and the seven `ReleaseArtifactVerifierContractTest` APK/AAB fixtures.

The exact `14`-test connected inventory includes XAGE shell/state tests for page switching, daily scores and quick-action ordering; deterministic Medical Assistant, Meals/Profile and More/support flows; two Medication flows; two Weight flows; login legal consent; report dashboard; and API 35 local-original storage.

The post-merge repair raises the exact Python inventory to `45` with `DeterministicAndroidUiTransportTest.test_zero_evidence_score_waits_for_loaded_empty_state_before_assertion`. It scans every connected-test method that accesses a pressure, recovery or inflammation score tag, requires exactly one shared loaded-state wait before the first access, rejects direct score-tag waiting, and currently proves that the sole affected method is `XAgeShellSwipeUiTest.zeroEvidenceShowsNeutralDailyScoreAndIndependentWarning`.

## Final exact verification

All local counts below describe the assertion-hardening behavior head `8263cefbaabfbc5cc7b3b1b0bfb38bf9934591fa`. Its separate hosted results are recorded after the incident history and do not replace the local device evidence.

| Gate | Exact command | Result |
|---|---|---|
| Android quality tools | `cd Android && python3 tools/verify_python_test_inventory.py --run` | `44/44`, no failure/error/skip/expected failure |
| JVM build/test/lint | `cd Android && ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` | passed |
| JVM inventory | `cd Android && python3 tools/verify_jvm_test_inventory.py --results app/build/test-results/testDebugUnitTest` | exact `232/232`, no missing/extra/duplicate/failure/skip |
| UI profiles | `cd Android && bash tools/run_connected_ui_profiles.sh` | exact `14/14` in each required profile, `42/42` total |
| Backend inventory | `python -I Android/tools/verify_backend_python_test_inventory.py --run --junit <result>` in the bound backend environment | exact `392/392`, no missing/extra/duplicate/failure/skip |
| Backend parity | enumerate `git -C /Users/linlin/Desktop/X/XJie_IOS ls-files backend`, then byte-compare each current iOS worktree file with its Android counterpart | `261/261` present, `0` missing, `0` mismatched |
| Formatting | `git diff --check` | passed |

The exact UI result files report:

| Profile | Configuration | Tests | Failures | Errors | Skipped | Time | JUnit SHA-256 |
|---|---|---:|---:|---:|---:|---:|---|
| `standard_api35` | API 35 emulator default profile | 14 | 0 | 0 | 0 | 52.071 s | `ab92d48435e170ee6a756f42d0b567534de9417b95ffe62acd8ccffb81bf80d3` |
| `compact_api35` | API 35, `700x1280`, density `320` | 14 | 0 | 0 | 0 | 44.160 s | `abeb73282a50352b71dd3efe951d63fe4cc4c3810b6ba64a924be1f5b1483553` |
| `large_text_api35` | API 35, font scale `1.3` | 14 | 0 | 0 | 0 | 50.827 s | `ed3017a1f0e57ed98f69d0ade66530f5693edde163e4bcd19a836f3b4976a4f5` |

Result roots are local ignored build evidence under `Android/build/quality/android-ui/{standard_api35,compact_api35,large_text_api35}`. The inventory validator requires exact equality in all three sets.

## Preserved failing evidence and permanent corrections

The first complete profile script passed standard `14/14`, then stopped on compact with three failures; large text correctly did not run after the red result.

| Initial failure | What it exposed | Permanent correction |
|---|---|---|
| `MedicationDashboardUiTest.nextDoseIsDominantAndEveryPrimaryActionRemainsIndependentlyAccessible` | The dominant hero could exceed the compact viewport contract and secondary actions needed reachability evidence rather than an assumed coordinate. | The iOS-derived hero minimum remains responsive, its maximum/reachability boundary is asserted, and every primary action is independently revealed through the tagged medication root. |
| `XAgeShellSwipeUiTest.medicalAssistantUsesDeterministicOverviewAndReturnsToDataPage` | System back raced the `无信息更新` notice dismissal and could act on the wrong presentation owner. | `medicalAssistant.notice` and `medicalAssistant.close` are explicit state/owner tags; the test waits for notice absence, then uses the owned close action and verifies return restoration. |
| `XAgeShellSwipeUiTest.mealsAndProfileUseAllowlistedEmptyStatesAndReturnToDataPage` | Compact long content existed but was below the viewport; `assertIsDisplayed` without scrolling was invalid evidence. | Meal and profile empty-state assertions call `performScrollTo()` before checking visibility. |

Later focused red/green checks also locked these same-class repairs:

- native Markdown answer text is observed through Espresso while Compose owns the surrounding evidence UI;
- feedback cancellation waits for the dialog owner instead of matching a transition-era duplicate label;
- the data-manager back control stays outside the scroll body, and the real canonical `bodyWeight` server card opens the same Weight flow as the quick action;
- exact settings loading and deterministic request matching reject support/device prefetch and unknown traffic.

The final full rerun, rather than focused reruns alone, is the completion evidence: `232/232` JVM, `44/44` Python, `392/392` backend and all `42/42` connected executions passed.

### Hosted exact-head UI incident — minimum reproduction and repair contract

- Minimum reproduction: run the `API 35 deterministic UI profiles` job from exact-head run [31175685594](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31175685594), job `92858966622`, on the unmodified feature SHA `f9f581f2cec1cf44a2fae0a5524d58cd13e81e8d`.
- Observed failure: `reactivecircus/android-emulator-runner@v2` reported that hardware acceleration was unavailable and launched emulator `37.1.11` with `-accel off`. Boot took from `12:00:35Z` to `12:15:06Z`; Gradle then reported `ShellCommandUnresponsiveException`, `Starting 0 tests`, and an instrumentation process crash. The evidence upload also failed because no non-empty profile evidence directory existed.
- Confirmed root cause: the UI job did not make `/dev/kvm` readable and writable or require hardware acceleration before launching the emulator. This silently admitted a software-emulated API 35 device that became unresponsive before instrumentation; no product test ran, so the failure is not evidence of an app assertion regression.
- Permanent invariant: hosted API 35 UI jobs must fail before emulator launch unless `/dev/kvm` is a readable, writable character device, must explicitly forbid the runner's Linux hardware-acceleration fallback, and must disable the emulator's interactive metrics prompt. A profile run must always leave a non-empty status/partial-report evidence bundle, including on failure.
- Affected sibling states: the shared standard, compact and large-text profiles all use this one runner and were all blocked; the `if: always()` evidence upload path also lacked diagnostic content on an early first-profile failure.
- Constraint owners: `.github/workflows/ci.yml` owns KVM admission and emulator options; `Android/tools/run_connected_ui_profiles.sh` owns failure evidence and per-profile result isolation; `DeterministicAndroidUiTransportTest.test_ci_requires_kvm_before_emulator_and_keeps_evidence_upload_fail_closed` owns the regression contract.
- Verification plan: prove the strengthened named Python regression fails against the old workflow/script and passes after the repair; rerun the exact Python inventory, JVM/build/lint, all three local API 35 profiles, and a new hosted exact-head run. Hosted completion requires all `42/42` UI executions plus an uploaded evidence artifact, and the successful log must not contain the old `-accel off` fallback.

### Hosted exact-head deterministic-origin incident — minimum reproduction and repair contract

- Minimum reproduction: run the repaired `API 35 deterministic UI profiles` job from exact-head run [31179377876](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31179377876) on feature SHA `43157811daf5db8c9ad4b59ae916234a04e21b76`, using GitHub's clean checkout without an untracked `Android/local.properties`.
- Observed failure: KVM admission passed, the runner explicitly kept Linux hardware acceleration enabled, the API 35 emulator booted, and all `14` standard-profile tests executed. The uploaded JUnit reports `tests="14" failures="13" errors="0" skipped="0"`; every network-using test ended at the shared runtime ledger, while the local-original device test passed. Preserved logcat shows requests such as `GET http://10.0.2.2:8000/api/users/me` receiving the deterministic transport's non-retryable `418`. The fail-closed script stopped before compact and large-text profiles and preserved `exit_code=1`, `profile=standard_api35` plus raw results and logs. GitHub artifact `android-ui-profile-results` is ID `8994705825`, size `195483`, digest `sha256:c16577c9468423001795440d76dcfb494663ceb9faeeb377e386d6636b833584`.
- Confirmed root cause: `Android/app/build.gradle.kts` intentionally resolves the Debug base URL from `local.properties`, then the process environment, then the localhost emulator default. Local verification had an untracked `API_BASE_URL_DEBUG=https://www.jianjieaitech.com/api`, but the UI workflow supplied neither source. The clean hosted build therefore used `http://10.0.2.2:8000`; `DebugUiAutomationTransport.isProductionOrigin` correctly rejected that origin even though the method/path fixtures existed. This is a CI input-contract failure, not thirteen independent UI regressions and not a real-network dependency.
- Permanent invariant: the hosted deterministic UI job must explicitly pin the non-secret production-shaped Debug origin `https://www.jianjieaitech.com/api` before any Gradle build, must never depend on a developer's untracked `local.properties`, and must keep the exact scheme/host/port predicate, `418` unknown response, shared-client installation and zero-escape runtime assertion fail closed. The production-shaped URL identifies the request contract only; the Debug interceptor must answer every request locally and no public network response may satisfy the UI gate.
- Affected sibling entry points and states: anonymous `/api/auth/subjects` and every authenticated API route share the same exact-origin predicate; standard, compact and large-text profiles share one built Debug application and therefore the same base URL. The same clean-checkout dependency would affect every current and future deterministic UI test that exercises network-backed state.
- Constraint owners: `.github/workflows/ci.yml` owns the explicit job input; `Android/app/build.gradle.kts` owns local-properties/environment/default precedence; `DebugUiAutomationTransport.isProductionOrigin` and `assertNoRequestEscapedStub` own exact matching and zero escape; `DeterministicAndroidUiTransportTest.test_ci_pins_deterministic_origin_without_local_properties_dependency` owns the clean-checkout regression.
- Verification plan: first prove the new named regression rejects the old workflow, then add the explicit UI-job environment input and update the exact Python inventory. Run syntax/JSON checks, exact Python inventory, JVM tests plus exact executed inventory, assemble/lint, and the shared three-profile API 35 gate. Finally push a new exact feature SHA and require backend, JVM/build and all `14/14` standard + `14/14` compact + `14/14` large-text executions to pass with an uploaded evidence artifact, no `-accel off`, no `10.0.2.2` request and no `418`/unknown/escaped request.

### Hosted exact-head network-backed semantics incident — minimum reproduction and repair contract

- Minimum reproduction: run `XAgeShellSwipeUiTest.mealsAndProfileUseAllowlistedEmptyStatesAndReturnToDataPage` in the standard profile from exact-head run [31181786778](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31181786778), attempt 1, job `92878688639`, on repair head `5d12df5cfd90e89036cee3eeb683563860e82e98`.
- Observed failure: backend `392/392`, Python `44/44`, JVM `232/232`, assemble/lint, KVM admission, accelerated emulator boot and the explicit `API_BASE_URL_DEBUG=https://www.jianjieaitech.com/api` input all passed. All `14` standard tests executed; `13` passed and the meals/profile test alone failed because `performScrollTo()` searched for `本日暂无已确认餐食；识别草稿不会自动进入这里` before that network-backed semantics node existed. Logcat proves the production-shaped dietary dashboard, recent and daily-summary requests were intercepted locally and returned `200`; it contains no app parse error, `10.0.2.2`, `418`, unknown or escaped request. The fail-closed script stopped compact/large and preserved artifact `8995625515`, size `312459`, digest `sha256:f531250ffc9608a1665bd013df890cf955e18760cc9098867aa969cca92b4138`; `run-status.txt` is `exit_code=1`, `profile=standard_api35` and the JUnit is `tests=14`, `failures=1`, `errors=0`, `skipped=0`.
- Confirmed root cause: the test treated the static `饮食记录` header as proof that the asynchronous dashboard state had committed, then immediately queried a deep loaded-state string. Compose idling does not make the app's background repository coroutine a readiness contract, so the hosted cold run observed the shell before the loaded/empty semantics node. Repeated local runs had not reproduced that ordering window. This is a test-observation synchronization bug, not a dietary product response or deterministic-origin failure.
- Permanent invariant: network-backed text must be awaited by exact text, then resolved through the merged semantics tree, scrolled, and asserted visible. For lazy containers, an off-screen target may not exist in the semantics tree until the container composes it; those paths must first await a persistent readiness tag on an always-composed non-lazy owner, then await and select the caller-provided root tag, call `performScrollToNode` with the exact target tag, and assert the target is displayed. A readiness marker must represent the exact owning screen state, must not leak into a child editor, become a lazy-list item, alter `firstVisibleItemIndex`, shift a product row, or replace the scroll owner's existing tag. A static shell/root title is never a loaded-state signal, and sleeps are prohibited.
- Affected sibling entry points and states: the Meals empty-state and Profile long-term-medication empty-state used direct text scrolls. The completed `performScrollTo`/`performScrollToNode` scan also found the network-produced canonical Weight `bodyWeight` card and Medication plans/reactions destinations inside lazy containers. Synchronous login/forms, already-tagged support pages, and medication hero controls are not in this asynchronous class. Standard, compact, and large-text profiles share all five repaired assertions.
- Constraint owners: `DeterministicXjieUiTest.waitForAndScrollToText` owns exact-text readiness plus merged visibility; `waitForAndScrollToTag` owns readiness → caller-provided root → exact-lazy-target reveal. `XAgeMainScreen` exposes `xage.data.metrics.loaded` on its always-composed data-page column, while `MedicationListScreen` exposes `xage.medication.loaded` on its outer scaffold only when `editor == null`, loading is complete, and trusted today state exists; the existing lazy scroll roots keep their own tags and item indices. The Meals/Profile, Weight, and Medication connected tests use the appropriate helper. `DeterministicAndroidUiTransportTest.test_connected_tests_share_real_app_factory_profile_gate_and_runtime_ledger` strips comments and rejects missing helpers, unmerged final text lookup, ignored/hard-coded roots, direct bypass, duplicate/missing or child-editor readiness tags, lazy sentinel items, or absent final visibility assertions.
- Local red/green: the named Python command `python3 -m unittest Android.tools.tests.test_deterministic_ui_transport_contract.DeterministicAndroidUiTransportTest.test_connected_tests_share_real_app_factory_profile_gate_and_runtime_ledger` rejected the old direct-scroll source, then rejected pushed candidate `02560b0e2b27799e0c28cb804ec73413075782b1` because its final text lookup used `useUnmergedTree = true`. After restoring merged semantics, the Meals/Profile method passed three consecutive standard API 35 executions with `for attempt in 1 2 3; do ./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.xjie.ui.profile=standard_api35 -Pandroid.testInstrumentationRunnerArguments.class=com.xjie.app.feature.xage.XAgeShellSwipeUiTest#mealsAndProfileUseAllowlistedEmptyStatesAndReturnToDataPage || exit 1; done`.
- Same-class red/green: expanding the same named Python contract to Weight and Medication rejected the source before `waitForAndScrollToTag` and both readiness tags existed. The first two-method device attempt then failed exactly on `bodyWeight` because waiting for an off-screen lazy target cannot precede its composition; this rejected the naive target-wait helper. Review then rejected zero-height readiness items because they changed XAGE's index-zero product invariant and could be evicted, rejected Medication readiness that survived on child editors, and rejected a contract that did not prove use of the supplied root. Placing `xage.data.metrics.loaded` and dashboard-scoped `xage.medication.loaded` on persistent non-lazy owners plus awaiting the exact root preserved list indices/tags and prevented premature return synchronization. The exact two-method command for `WeightDashboardUiTest#bodyWeightDataCardOpensTheSameDedicatedWeightFlow,MedicationDashboardUiTest#secondaryRowsOpenRealDestinationsAndBackReturnsToDashboard` passed `2/2` on the final implementation.
- Adjacent/full verification: exact Python `44/44`, JVM `232/232`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:lintDebug`, and standard/compact/large-text UI `42/42` passed. Local `run-status.txt` is `exit_code=0`, `profile=large_text_api35`; the three local JUnit digests are recorded above.

### Hosted exact-head hardening success — qualification evidence

- Exact identity: [run 31188223697](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31188223697), event `pull_request`, completed `success`, head `8263cefbaabfbc5cc7b3b1b0bfb38bf9934591fa`. PR #1 independently reported that same head, `CLEAN`/`MERGEABLE`, with all three checks successful.
- Hosted inventories: backend production-image Python `392/392`; Android quality Python `44/44`; JVM declared/executed `232/232`; assemble and lint passed. The backend JUnit reports `392` tests, `0` failure/error/skip, time `27.065 s`, file SHA-256 `43f956ac8d4ffc2e92b80c38e095b490c7725ea5962e237687a1ba641192d13e`.
- Hosted UI: KVM character-device/read/write admission passed and Linux hardware acceleration remained enabled. Standard, compact, and large-text API 35 each report `14` tests with `0` failure/error/skip; times are `66.595 s`, `50.613 s`, and `67.397 s`. Their downloaded JUnit SHA-256 values are respectively `01ff0b4fa4177b4f299c66358520da7f37c112fb27eb30a52e6c6d44ed03607b`, `5144eb7e286064965101b3a2be7e752344225d044ec197917cf93e8896cc082a`, and `09b097a7d30584c640f6d7e2025ae1660b79ff64ef4eb8219b7717315a52b36b`. The tracked validator returned `ANDROID UI INVENTORY: PASS: source and required device results exact (14 tests)` across all three sets; `run-status.txt` is `exit_code=0`, `profile=large_text_api35`.
- Hosted artifacts: `android-ui-profile-results` ID `8998461179`, size `758320`, digest `sha256:433ba7dca434a238ec992bf701487d2068f903a7076fd285ebd746d74f78ced5`; `backend-unit-results` ID `8997691633`, size `11529`, digest `sha256:ee61e5e83981bedf349cdd6b3c4eaee77604b0027abc863ba77373c9674b6baa`.
- Zero-escape evidence: every connected test ends in `DebugUiAutomationTransport.assertNoRequestEscapedStub()`. All `42/42` passed, and a scan of the downloaded text logs found no `10.0.2.2`, HTTP `418`, unknown deterministic request, empty interceptor ledger, fail-closed bridge error, or escaped-request marker. This proves only the deterministic client states and request allowlist asserted by the tests; it does not prove live service or AI quality.
- Evidence-head delivery: later evidence head `5dd78092de5d295b7031d58782c8585a35efc068` passed all three required checks in exact-head [run 31190901068](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31190901068): backend `392/392`, Python `44/44`, JVM `232/232`, assemble/lint and API 35 UI `14/14` × 3. Downloaded UI artifact `8999525030` is `938250` bytes with digest `sha256:6983446bca5d982f76bcc6eed3a818fa3d49c1bbfabd988a4fe683f62d09ee4f`; its standard, compact and large-text JUnit files report `14`, `0` failure/error/skip with SHA-256 `aa401ac08e1ba69fed4b0352a454277c47855925ca798bd3de2c601abbe6e79e`, `4cc03a55651afe341f0291c5908124cc24045a5076b8e76a46c1131670fc5154`, and `54ed0a5f5f47ffe85a6afd64ba4b850c4e85d76313d7b6aa0c6dc06445a56ad4`; `run-status.txt` is `exit_code=0`, `profile=large_text_api35`. Backend artifact `8998790180` is `11590` bytes with digest `sha256:fdb293d57b4cfff9ea8bfe477c1d359afdd8bb6ea3a275a789f28de597f09a48`, and its downloaded JUnit reports exact `392/392` with file SHA-256 `9619ad7ae5f7364f6365a64a00ef1614f861ad36f130841828d0bea44b7d8c20`.
- Protected merge identity: PR #1 merged at `2026-08-07T15:31:53Z` as `1c09cc31d5fd725dc172762bf68067ed73553cb8`; its parents are base `3d3b7bcd3a2a9cee2a8376be6d936a53eda9299a` and evidence head `5dd78092de5d295b7031d58782c8585a35efc068`, and its tree is exactly evidence-head tree `d1804bd58b90424470ba47add144481491b2dfa3`.

### First post-merge loaded-empty readiness incident — minimum reproduction and repair contract

- Minimum reproduction: run `XAgeShellSwipeUiTest.zeroEvidenceShowsNeutralDailyScoreAndIndependentWarning` as part of the standard API 35 profile from exact `main` push [run 31193076541](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31193076541), job `92917265963`, on merge SHA `1c09cc31d5fd725dc172762bf68067ed73553cb8`.
- Observed failure: backend `392/392`, Android Python `44/44`, JVM `232/232`, assemble and lint passed. KVM admission and accelerated emulator launch passed; the standard profile executed all `14`, with `13` passing and one failure. The score test expected `压力评分 50 分，数据完整度 0%，置信度较低` but observed `压力评分待更新`; compact and large-text correctly did not run after the red result. The standard JUnit is `tests=14`, `failures=1`, `errors=0`, `skipped=0`, time `60.129 s`, SHA-256 `5bb45120936b0a6a98959ee3da07ca01a6c20f9492e0f6d8765c8447872fb89a`. UI artifact `9000198527` is `300447` bytes with digest `sha256:9f2987d556261d20414ac638f298b1bf5648c5ad23a5fd8a6f9e7b7385de3d36`; its preserved status is `exit_code=1`, `profile=standard_api35`. Backend artifact `8999670087` is `11526` bytes with digest `sha256:e66ac34db54d2af96f22f29ce782a9d00c98c9c5bff61ad59eec383caf661159`, and its JUnit SHA-256 is `fa3e2ce11d96ecdf4cd4c9bcf1a99d65c7600e33246b529afb01d0115ca6afb9`.
- Confirmed root cause: the product correctly composes pressure/recovery/inflammation score nodes immediately with an accessible `待更新` placeholder, then changes them to neutral `50/0%` only after the deterministic XAGE snapshot commits its loaded-empty state. The test waited for the always-present `xage.data.score.pressure` node, so it could proceed during the valid loading interval. This is a test-observation synchronization defect, not incorrect score computation, failed traffic, or a product request to hide loading.
- Permanent invariant and owner: loaded score assertions call `DeterministicXjieUiTest.waitForLoadedXAgeData`, which waits for `xage.data.metrics.loaded`, before reading any score semantics. The product retains the honest loading placeholder. The named static regression `test_zero_evidence_score_waits_for_loaded_empty_state_before_assertion` strips comments, scans all Android connected-test methods that access any daily-score tag, requires one shared loaded-state wait before the first access, forbids direct score-tag waiting and pins the current same-class inventory.
- Same-class scan: only `zeroEvidenceShowsNeutralDailyScoreAndIndependentWarning` asserted score semantics. The capsule state test already waits for the exact authoritative header state; Weight, Medication, Meals and Profile use their own persistent loaded-state owners. Score detail/info/confidence nodes, header/manage/scroll nodes and default metric cards remain present during loading and are explicitly not readiness signals. Error-state tests must wait for their exact header error semantics rather than weakening the loaded tag.
- Local red/green and adjacent verification: the old direct score-tag wait is rejected by the new named Python test. On the final repair, the exact score method passed three consecutive standard API 35 executions. `python3 tools/verify_python_test_inventory.py --run` passed exact `45/45`; `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` and the exact JVM validator passed `232/232`; `bash tools/run_connected_ui_profiles.sh` passed standard, compact and large-text `14/14` each (`42/42`). The final local JUnit files report zero failure/error/skip with standard `48.785 s`, compact `42.223 s`, large-text `49.63 s` and SHA-256 `ea8bef7e2b50d73350f8ee74fce2207df0e5c399c48eefc78d41c5e04354c80f`, `5136f9b7a4695ea255b33f624dbbdb3dc91ebc794e0032b2add2891faaf18daa`, and `271a822b35244c522bbeef4f1b3d134b01c4da8087ed42841cd818be661fa8a4f`.
- Hosted completion: repair head `f49b038fb9d344d0742f409e25cb81bbf429b2e4` passed all three required checks in [run 31195706886](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31195706886), then [PR #2](https://github.com/doyoulikelin-wq/Xjie_andro/pull/2) merged under protection as `bebf94afdd702f7dee5d930ee8083a457f89a9ea`. Independent push/main [run 31196747858](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31196747858) passed backend `392/392`, Python `45/45`, JVM `232/232`, assemble/lint and standard/compact/large-text UI `14/14` each. Run `31193076541` remains historical red evidence and was not rerun into a different conclusion.
- Final main artifact audit: UI artifact `9001434700` is `1076511` bytes with digest `sha256:2d387310c392277ceb368a36d123d9b8ce8f2777c9be64bd2f4eafeb513a96cc`; backend artifact `9001147046` is `11536` bytes with digest `sha256:d93cbe98240e72fde26037f953f3654a3203bf8cf536ba1ef4fb4959db7435b5`. Downloaded archives matched those GitHub digests. Standard/compact/large-text JUnit files report `14/14` with zero failure/error/skip, times `76.691 s`, `44.769 s`, `57.859 s`, and SHA-256 `933017bbf455f442617f3aad157496d3e535256575ed3a989e2d40d5732f22b3`, `735c6d9be500dcb268bf018b7907054a573b74bdf36232b90b335d56aac80caa`, `24811dfcbd6c414fc755f1c24cd638e30799fac82083af1a4ef1f163adcb7a87`. Backend JUnit is exact `392/392`, time `29.992 s`, SHA-256 `d9afb9e2d4698ec47ad802f5ffc2d266a3b643963b8692990b4ca67f21390e4b`.
- Final runtime audit: every UI profile recorded `171` requests and `171` responses, all status `200`; no unknown request, empty ledger, escaped request or fail-closed bridge marker appeared. `run-status.txt` is exactly `exit_code=0` and `profile=large_text_api35`. KVM admission passed, Linux hardware acceleration remained enabled, and no `-accel off` launch occurred. These deterministic results prove only the client contracts they assert.

## Visual parity review

The final API 35 captures are `1080x2400`. The composites are `2412x2622` and place the current iOS reference beside Android for direct review.

| Artifact | SHA-256 |
|---|---|
| `Android/build/quality/visual-audit/android-login-final.png` | `fb1ad673bae030367e44f018739cf65bc1c9f1f29d6c1d79ec77414583ae168f` |
| `Android/build/quality/visual-audit/android-xage-final.png` | `95941ccc228d4242b9b4faaa2011a0e295d99e930c97bef59894a1872d7db79f` |
| `Android/build/quality/visual-audit/android-weight-rich-final.png` | `807da6f066bba41593782a72a119330ba7f6d5c9754e9cd141867a3a00db3b83` |
| `Android/build/quality/visual-audit/ios-android-login-final.png` | `01e7a78bdb4f4a8ef9ae7ef9ed58873da4994c712b0293cb8ee1096cac01d372` |
| `Android/build/quality/visual-audit/ios-android-xage-final.png` | `2987e0d84372644250fd0ab18c65e0f8200796022cd575ddff9758cce3f38b78` |

Review result:

- Login aligns on brand mark, title/subtitle, phone/password hierarchy, show-password affordance, primary gradient action and registration/forgot-password order.
- XAGE aligns on the three-page capsule, management action, title, three-score card, independent confidence affordances, quick-action order, platform-health card and metric-card hierarchy.
- The Weight capture verifies the dedicated detail hierarchy, trusted current value/trend presentation and height/weight input entry points used by both the quick action and canonical server card.
- Health Connect is the explicit Android replacement for Apple Health; Android system status/navigation insets remain native.
- The XAGE comparison contains different deterministic loading/evidence moments (`--` on the captured iOS reference versus `50/0%` on Android). It proves hierarchy and the required Android zero-evidence presentation, not pixel equality between identical runtime data or real health-source behavior.

## Development artifact and Release boundary

- Debug APK: `Android/app/build/outputs/apk/debug/app-debug.apk`.
- Debug APK SHA-256: `27df6b229f5b1fec4242f2e29976aedb9bf92c57a2af293a18fa89848136806f`.
- This is a Debug artifact only. Its successful assembly and digest are not formal signing, distribution or release evidence.
- `./gradlew verifyReleaseConfiguration` intentionally exits `1` in the current environment because an explicit non-placeholder HTTPS `API_BASE_URL_RELEASE` and all external release-signing inputs were not supplied.
- The seven release-artifact verifier fixtures cover APK/AAB identity, version, production origin, explicit cleartext prohibition, modern signature/certificate, exact digest, explicit AAB tools and full-entry Debug marker scanning. They do not attest a real signed candidate because no such package was authorized or provided.
- No APK website replacement, production deployment, database migration, Play upload or external distribution occurred in this local verification.

## GitHub delivery fields

Only facts already produced by GitHub are populated:

| Field | Status |
|---|---|
| Main-based feature branch | `codex/android-ios-parity-20260807` (pushed to `andro`) |
| Initial feature / qualified behavior head | `f9f581f2cec1cf44a2fae0a5524d58cd13e81e8d` / `8263cefbaabfbc5cc7b3b1b0bfb38bf9934591fa` |
| Pull request URL/number | [PR #1](https://github.com/doyoulikelin-wq/Xjie_andro/pull/1) |
| First hosted exact-head CI run URL/ID and conclusion | [run 31175685594](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31175685594) — **failure before UI instrumentation; backend and JVM succeeded, UI executed 0 tests after the unaccelerated emulator became unresponsive** |
| KVM repair commit / replacement exact-head run | `43157811daf5db8c9ad4b59ae916234a04e21b76`; [run 31179377876](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31179377876) — **KVM/boot/evidence repair succeeded, but clean-checkout Debug origin was not explicit; standard executed `14` with `13` fail-closed `418` results, compact/large did not run** |
| Deterministic-origin repair / third exact-head run | `5d12df5cfd90e89036cee3eeb683563860e82e98`; [run 31181786778](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31181786778), attempt 1 — **backend/build passed; KVM and exact origin passed; standard executed `14` with one asynchronous semantics race, compact/large correctly did not run; failure artifact `8995625515`** |
| Initial semantics-readiness repair / superseded run | `02560b0e2b27799e0c28cb804ec73413075782b1`; [run 31184274631](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31184274631) — **cancelled after backend/build succeeded because review found the unmerged-semantics weakening and uncovered lazy-list siblings; the partial UI artifact `8996489587` (182 bytes, `sha256:bd27a0e17698136b7c9bd20a3305eeacec93f3c301576033bdbd958216e921ec`) is not qualification evidence** |
| Merged-semantics/persistent-readiness hardening / replacement exact-head run | `8263cefbaabfbc5cc7b3b1b0bfb38bf9934591fa`; [run 31188223697](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31188223697) — **success**: backend `392/392`, Python `44/44`, JVM `232/232`, assemble/lint, KVM and accelerated API 35 UI `14/14` × 3 passed; downloaded success artifacts and runtime ledger were independently inspected |
| Evidence-only follow-up exact-head run | `5dd78092de5d295b7031d58782c8585a35efc068`; [run 31190901068](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31190901068) — **success**: backend `392/392`, Python `44/44`, JVM `232/232`, assemble/lint, KVM and API 35 UI `14/14` × 3 passed; downloaded artifacts were independently inspected |
| Parity merge commit SHA | `1c09cc31d5fd725dc172762bf68067ed73553cb8`; merge tree exactly equals evidence-head tree `d1804bd58b90424470ba47add144481491b2dfa3` |
| First post-merge `andro/main` run | [run 31193076541](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31193076541) — **failure preserved**: backend/build passed; standard UI `13/14`, one loaded-empty observation race; compact/large stopped fail-closed |
| Loaded-empty readiness repair branch / PR | `codex/android-postmerge-loaded-empty-readiness-20260807`; `f49b038fb9d344d0742f409e25cb81bbf429b2e4`; [PR #2](https://github.com/doyoulikelin-wq/Xjie_andro/pull/2) merged |
| Repair exact-head run | [run 31195706886](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31195706886) — **success**: backend `392/392`, Python `45/45`, JVM `232/232`, assemble/lint and UI `14/14` × 3 |
| Final behavior `andro/main` SHA | `bebf94afdd702f7dee5d930ee8083a457f89a9ea`; tree `1452d42ad20fe0959c5e830a6aad2282bb0df360` exactly equals repair-head tree |
| Replacement post-merge main CI run URL/ID and conclusion | [run 31196747858](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31196747858) — **success** on exact `bebf94a`: backend `392/392`, Python `45/45`, JVM `232/232`, assemble/lint and UI `14/14` × 3 |

Successful runs `31188223697`, `31190901068`, `31195706886`, and `31196747858` do not rewrite the historical conclusions of failed runs `31175685594`, `31179377876`, `31181786778`, or `31193076541`, and cancelled run `31184274631` is not green evidence. Current delivery status is: “the Android parity source and protected GitHub `main` delivery are exact and auditable; the first post-merge loaded-state observation race is permanently constrained by a shared readiness owner and named regression; the repair head and independent final main run are fully green. Signed production Release, Play distribution, backend deployment/migration and real-device sign-off remain outside this completion boundary.”

## Remaining risks and non-claims

- No real Android release signing identity, explicit production Release URL or signed candidate package was supplied.
- Emulator tests do not prove Health Connect installation/authorization or real sample behavior, notification/exact-alarm delivery, reboot/time-zone recovery, OEM keyboard/autofill, system camera/picker behavior, lock-screen privacy or real-device TalkBack.
- Deterministic fixtures prove the client states they assert and zero network escape; they do not prove production AI answer quality, safety, evidence relevance, OCR accuracy, latency or network recovery.
- The backend is byte-aligned in this repository and passes its exact unit inventory, but migrations 0020–0026, workers, object storage, Redis and providers were not deployed or validated in production by this task.
- Visual comparison is deterministic layout/semantic evidence. It does not establish literal pixel identity across iOS/Android system chrome, fonts, safe areas or platform controls.
- Bluetooth/NFC device binding remains honestly unsupported until vendor protocols, ownership and revocation contracts exist.
- Exact inventories prevent accidental missing, extra, duplicate, failed or skipped tests; they cannot mathematically prove that future assertion semantics have not been weakened.

## Completion boundary

Local completion for the Android software tree requires the root cause, same-class scan, permanent constraints, named regressions, exact full gates and this evidence. Those local conditions are satisfied by the parity tree and by the post-merge readiness repair described above.

End-to-end source/GitHub completion additionally requires exact-head success for the repair PR, a protected repair merge, and a successful replacement post-merge main CI run on that exact merge SHA. A production Android release remains a separate blocked boundary and must not be claimed without the explicit URL, external signing and exact verified candidate package.
