# XAGE support and compliance regression contract

## Minimum reproduction and root cause

- Android XAGE `更多` exposed only `画像`, even though the app already had working account controls and `/api/feedback` in `SettingsScreen`.
- Settings had no distinct version, privacy-policy, or personal-information collection destinations.
- Metric/support rows with a chevron could become dead affordances when the XAGE shell did not map their destination.
- Root cause: XAGE information-architecture cleanup narrowed the More dialog but did not register account/support destinations in the shared navigation contract. The existing Settings implementation was therefore unreachable from the main XAGE shell.
- 2026-08-07 minimum reproduction: open Android `关于与支持`; it still shows the superseded `个人信息收集清单` destination and policy date `2026年4月9日`, while the current iOS authority exposes `权限申请与使用情况说明`, policy `2026.07` updated/effective `2026年7月26日`, eight full privacy topics, and per-capability timing/purpose/refusal impact.
- 2026-08-07 root cause: the first Android reachability repair copied a July 15 support snapshot into `SettingsScreen` instead of introducing one versioned shared compliance policy. Later iOS compliance changes therefore had no Android update surface or parity regression.

## Permanent invariants

1. `更多` keeps `画像` and also exposes real `账号与权限` and `关于与支持` destinations.
2. Every More destination is declared by `XAgeInformationArchitecture` and resolved by `MainScaffold`; unknown values fail closed and render no fake success.
3. Support presents distinct, working help, version, privacy, permission-use, and feedback pages. Destination IDs are exactly `help/version/privacy/permissions/feedback`.
4. Feedback accepts 2–2000 trimmed characters, keeps the dialog/draft on failure, and closes only after `/api/feedback` succeeds.
5. Privacy and permission content is available locally without public internet. The shared Android policy remains version `2026.07`, contains all eight current authority topics, and every Android capability states request timing, purpose, and refusal impact.
6. Account deletion uses the existing server `DELETE /api/users/me`; the ordinary list action is visually secondary while the irreversible confirmation remains explicit and destructive.
7. Android uses Health Connect/system-picker/notification/exact-alarm/package-installer/boot-recovery substitutions explicitly. It does not claim Apple APIs, broad gallery access, Bluetooth/NFC device support, or a scheduled reminder that the OS did not actually accept.

## Sibling entry points and states

- XAGE More, focused account/support routes, and the full legacy Settings route.
- Feedback empty, too-short, submitting, failure, and success states.
- Change-password, logout, consent, and account-delete confirmation paths.
- Back navigation from account/support must return to XAGE without creating a second shell.

## Verification plan

- Run the named `XAgeSupportComplianceParityTest`, strengthen `XAgeInformationArchitectureTest`, and add Android static/UI consumer checks.
- Run the focused JVM test, exact JVM inventory validator, Android tools tests, `assembleDebug`, `lintDebug`, and `git diff --check`.
- Real-device sign-off remains required for TalkBack, large text, OEM keyboard, notification/system settings, and actual feedback/account deletion against a controlled non-production test account.

## Local verification evidence (2026-07-15)

- Named JVM regression: `XAgeInformationArchitectureTest.moreMenuContainsOnlyTheHealthProfileEntry` was strengthened in place (no inventory change) to cover profile/account/support destinations, five support entries, feedback length limits, and draft detection.
- Command: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew --no-daemon :app:testDebugUnitTest --tests com.xjie.app.feature.xage.XAgeInformationArchitectureTest :app:lintDebug` — passed.
- Command: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew --no-daemon :app:assembleDebug :app:lintDebug` — passed.
- Command: `python3 -m unittest Android/tools/tests/test_health_trust_consumers.py` — 8 passed. The existing static consumer regression now proves that More account/support values resolve through `MainScaffold`, the focused route exists, feedback waits for success and protects drafts, and account deletion calls the real endpoint.
- Command: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew --no-daemon :app:testDebugUnitTest && python3 tools/verify_jvm_test_inventory.py --results app/build/test-results/testDebugUnitTest` (from `Android/`) — full JVM suite passed and the exact validator reported `80 tests` with no missing, extra, failed, or skipped results.
- Command: `python3 -m unittest discover -s Android/tools/tests -p 'test_*.py'` — 11 passed.
- Initial Gradle attempt without the tracked `JAVA_HOME` failed before project configuration because the shell default was Java 8 while AGP requires Java 11 or newer; no test ran in that attempt. The tracked Android Studio JBR command above is the valid evidence.
- Remaining limitation: local tests do not prove a production feedback response, real account deletion, TalkBack, OEM keyboard behavior, or small/large-font rendering. These remain controlled-device sign-offs.
