# Android iOS parity evidence — 2026-08-07

## Evidence status

- Scope: Android-only replication of the current iOS XAGE product behavior and its compatible backend snapshot.
- Status: final local software verification and the hosted-emulator repair passed locally; the first hosted exact-head run failed before executing any UI test and its replacement run is pending; production Release is blocked by required external inputs.
- Android repository: `/Users/linlin/Desktop/X/XJie_And`.
- Current main-based feature branch: `codex/android-ios-parity-20260807`; first pushed feature SHA `f9f581f2cec1cf44a2fae0a5524d58cd13e81e8d`.
- Target remote and branch: `andro` (`doyoulikelin-wq/Xjie_andro`) → `main`. The `origin` remote points to the iOS repository and is forbidden for Android delivery.
- GitHub PR: [#1](https://github.com/doyoulikelin-wq/Xjie_andro/pull/1). First exact-head run: [31175685594](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31175685594), conclusion **failure**. Merge and post-merge main identities do not yet exist and remain pending.

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
- Support/device routes never prefetch account settings. Long-content UI tests reveal content before display assertions; dialog tests observe dismissal before using the owning close action.
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

## Final exact verification

All counts below describe the final local source that was committed as feature SHA `f9f581f2cec1cf44a2fae0a5524d58cd13e81e8d`. This later delivery-binding documentation update is local and is not retroactively claimed as part of that exact-head run.

| Gate | Exact command | Result |
|---|---|---|
| Android quality tools | `cd Android && python3 tools/verify_python_test_inventory.py --run` | `43/43`, no failure/error/skip/expected failure |
| JVM build/test/lint | `cd Android && ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` | passed |
| JVM inventory | `cd Android && python3 tools/verify_jvm_test_inventory.py --results app/build/test-results/testDebugUnitTest` | exact `232/232`, no missing/extra/duplicate/failure/skip |
| UI profiles | `cd Android && bash tools/run_connected_ui_profiles.sh` | exact `14/14` in each required profile, `42/42` total |
| Backend inventory | `python -I Android/tools/verify_backend_python_test_inventory.py --run --junit <result>` in the bound backend environment | exact `392/392`, no missing/extra/duplicate/failure/skip |
| Backend parity | enumerate `git -C /Users/linlin/Desktop/X/XJie_IOS ls-files backend`, then byte-compare each current iOS worktree file with its Android counterpart | `261/261` present, `0` missing, `0` mismatched |
| Formatting | `git diff --check` | passed |

The exact UI result files report:

| Profile | Configuration | Tests | Failures | Errors | Skipped | Time |
|---|---|---:|---:|---:|---:|---:|
| `standard_api35` | API 35 emulator default profile | 14 | 0 | 0 | 0 | 50.512 s |
| `compact_api35` | API 35, `700x1280`, density `320` | 14 | 0 | 0 | 0 | 42.920 s |
| `large_text_api35` | API 35, font scale `1.3` | 14 | 0 | 0 | 0 | 51.164 s |

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
- Constraint owners: `.github/workflows/ci.yml` owns the explicit job input; `Android/app/build.gradle.kts` owns local-properties/environment/default precedence; `DebugUiAutomationTransport.isProductionOrigin` and `assertNoRequestEscapedStub` own exact matching and zero escape; `DeterministicAndroidUiTransportTest.test_ci_pins_deterministic_origin_without_local_properties_dependency` will own the clean-checkout regression.
- Verification plan: first prove the new named regression rejects the old workflow, then add the explicit UI-job environment input and update the exact Python inventory. Run syntax/JSON checks, exact Python inventory, JVM tests plus exact executed inventory, assemble/lint, and the shared three-profile API 35 gate. Finally push a new exact feature SHA and require backend, JVM/build and all `14/14` standard + `14/14` compact + `14/14` large-text executions to pass with an uploaded evidence artifact, no `-accel off`, no `10.0.2.2` request and no `418`/unknown/escaped request.

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
- Debug APK SHA-256: `e52ddf643a3dcf359ee1d3007d657a7541b0f9537b41807ca39d95998fe9b8c7`.
- This is a Debug artifact only. Its successful assembly and digest are not formal signing, distribution or release evidence.
- `./gradlew verifyReleaseConfiguration` intentionally exits `1` in the current environment because an explicit non-placeholder HTTPS `API_BASE_URL_RELEASE` and all external release-signing inputs were not supplied.
- The seven release-artifact verifier fixtures cover APK/AAB identity, version, production origin, explicit cleartext prohibition, modern signature/certificate, exact digest, explicit AAB tools and full-entry Debug marker scanning. They do not attest a real signed candidate because no such package was authorized or provided.
- No APK website replacement, production deployment, database migration, Play upload or external distribution occurred in this local verification.

## GitHub delivery fields

Only facts already produced by GitHub are populated:

| Field | Status |
|---|---|
| Main-based feature branch | `codex/android-ios-parity-20260807` (pushed to `andro`) |
| Feature commit SHA | `f9f581f2cec1cf44a2fae0a5524d58cd13e81e8d` |
| Pull request URL/number | [PR #1](https://github.com/doyoulikelin-wq/Xjie_andro/pull/1) |
| First hosted exact-head CI run URL/ID and conclusion | [run 31175685594](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31175685594) — **failure before UI instrumentation; backend and JVM succeeded, UI executed 0 tests after the unaccelerated emulator became unresponsive** |
| KVM repair commit / replacement exact-head run | `43157811daf5db8c9ad4b59ae916234a04e21b76`; [run 31179377876](https://github.com/doyoulikelin-wq/Xjie_andro/actions/runs/31179377876) — **KVM/boot/evidence repair succeeded, but clean-checkout Debug origin was not explicit; standard executed `14` with `13` fail-closed `418` results, compact/large did not run** |
| Deterministic-origin repair / next exact-head run | pending commit and hosted result; old workflow rejected by `test_ci_pins_deterministic_origin_without_local_properties_dependency`; explicit job-level origin passes YAML semantics and local Python `44/44`, JVM `232/232`, build/lint and UI `42/42` |
| Merge commit SHA | pending |
| Final `andro/main` SHA | pending |
| Post-merge main CI run URL/ID and conclusion | pending |

Local green results cannot convert failed runs `31175685594` or `31179377876` to success. Until the deterministic-origin repair is committed, pushed, passes a new exact-head run and the PR is merged, this evidence must say “two hosted failures exposed and permanently constrained KVM and clean-checkout origin assumptions; replacement exact-head/merge/main delivery pending.”

## Remaining risks and non-claims

- No real Android release signing identity, explicit production Release URL or signed candidate package was supplied.
- Emulator tests do not prove Health Connect installation/authorization or real sample behavior, notification/exact-alarm delivery, reboot/time-zone recovery, OEM keyboard/autofill, system camera/picker behavior, lock-screen privacy or real-device TalkBack.
- Deterministic fixtures prove the client states they assert and zero network escape; they do not prove production AI answer quality, safety, evidence relevance, OCR accuracy, latency or network recovery.
- The backend is byte-aligned in this repository and passes its exact unit inventory, but migrations 0020–0026, workers, object storage, Redis and providers were not deployed or validated in production by this task.
- Visual comparison is deterministic layout/semantic evidence. It does not establish literal pixel identity across iOS/Android system chrome, fonts, safe areas or platform controls.
- Bluetooth/NFC device binding remains honestly unsupported until vendor protocols, ownership and revocation contracts exist.
- Exact inventories prevent accidental missing, extra, duplicate, failed or skipped tests; they cannot mathematically prove that future assertion semantics have not been weakened.

## Completion boundary

Local completion for the Android software tree requires the root cause, same-class scan, permanent constraints, named regressions, exact full gates and this evidence. Those local conditions are satisfied by the final results above.

End-to-end task completion additionally requires exact-head CI success, the pending merge SHA and post-merge main CI. A production Android release remains a separate blocked boundary and must not be claimed without the explicit URL, external signing and exact verified candidate package.
