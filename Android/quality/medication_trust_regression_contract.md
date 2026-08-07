# Android trusted medication regression contract

Updated: 2026-07-15

## Minimum reproduction and root cause

1. Open **Medication** with a legacy CRUD item or with no item.
2. The old screen treats a reminder configuration as the medication truth, offers edit/delete as
   its main actions, and claims a photo can be recognized although the client only has a raw-text
   endpoint.
3. There is no consumer for the server-authoritative today task, occurrence version, prefill review,
   confirmed-plan version, inventory estimate, or adverse-reaction evidence.

The root cause is not a missing button. The legacy page merged three different trust levels
(unconfirmed extraction, a reminder configuration, and a user-confirmed dose event) into one CRUD
model. Time elapsed could therefore be presented without the server's explicit
`possibly_missed_is_not_confirmation` boundary, and retries had no stable event identity.

## Permanent invariants

- `MEDICATION-TRUST-001`: the trusted screen renders current tasks, plans, pending prefills and
  reactions only from the typed trusted API. Legacy CRUD remains compatibility-only and is never
  promoted to a confirmed plan.
- `MEDICATION-DOSE-001`: elapsed time may produce `possibly_missed`, but the client must label it as
  pending confirmation and must never claim a confirmed missed dose. Taken, snooze and skip send
  the server plan/occurrence versions plus a stable `client_event_id`; a failed retry reuses it.
- `MEDICATION-PREFILL-001`: recognize accepts raw OCR text only. It creates an
  `unconfirmed_prefill`; a plan exists only after explicit, versioned confirmation. Low-confidence
  fields remain visibly marked. The UI must not claim that camera capture is wired.
- `MEDICATION-INVENTORY-001`: inventory is shown only with the server label `预计剩余` and the
  `user_confirmed_taken_events_only` basis.
- `MEDICATION-REACTION-001`: a reaction is a temporal association only. Severe symptoms show the
  server safety guidance and never establish causality.
- `UX-FORM-EXIT-001`: page/back/scroll clears focus and the keyboard. Dirty manual, OCR, prefill or
  reaction editors require an explicit discard decision.

## 2026-07-15 specialist-document completion audit

### Minimum reproduction and actual root cause

1. Open a confirmed plan and try to turn its reminders on. The card says reminders are locally
   managed, but there is no reminder-settings page, no permission recovery action, and no persisted
   trusted-plan schedule for reboot recovery.
2. Open records after a mistaken taken/skip action. The page can show only today's rows and cannot
   send the backend's explicit `correct` event, while weekly/course views and their evidence boundary
   are absent.
3. Try the four documented add paths. Manual entry and raw OCR text are real, but confirmed
   prescription import and history restart have no current listing/import API. A generic add action
   hides that capability gap instead of naming it.

The root cause is a second incomplete layer boundary: the first trusted-client pass correctly
separated schedule, OCR and confirmation facts, but it stopped at read/confirm actions. Local
notification preferences were still legacy-medication snapshots, and presentation code had no
typed capability model for records, course evidence, prescription import, history restart or
refill eligibility. Adding isolated buttons would recreate the same drift.

### Additional permanent invariants and sibling states

- `MEDICATION-REMINDER-002`: a confirmed server plan may create local alarms only after the user
  explicitly enables a validated, plan/version-bound reminder configuration. Daily/alternate-day,
  advance, snooze, meal wording, course end, sound and lock-screen privacy share one persisted model;
  reboot/update/time-zone recovery consumes that same model. One plan/time and one occurrence snooze
  have stable identities, so retries replace instead of duplicate alarms.
- `MEDICATION-PERMISSION-001`: app launch never requests notification permission for medication.
  Enabling a reminder requests it in context; denial shows a real system-settings recovery action.
- `MEDICATION-RECORD-002`: daily and seven-day records come only from trusted today snapshots.
  The confirmed-rate numerator is user-confirmed taken plus skipped occurrences, never elapsed,
  snoozed or pending schedules. Same-day correction must pin plan/occurrence versions and the latest
  event ID. The current API has no bounded course aggregate, so the course view must label its
  confirmed rate unavailable instead of inventing one.
- `MEDICATION-COURSE-002`: elapsed days and end proximity may be derived only from confirmed course
  dates; refill eligibility remains unavailable unless a prescription/server fact explicitly
  supplies it. Inventory remains the server's confirmed-event-based estimate.
- `MEDICATION-CAPABILITY-001`: manual confirmation and raw-text OCR are real capabilities. Pending
  `prescription_import` or `history` candidates may be reviewed; when none exist, those entries are
  visibly unavailable. Camera capture, prescription browsing, history browsing, course aggregates
  and refill eligibility must never be presented as working without a supporting contract.

Sibling coverage includes permission granted/denied/recovered, reminder on/off, daily/alternate-day,
advance crossing midnight, course end, sound/privacy, plan-version drift, boot/update/time-zone
restore, repeated snooze, current-day correction, weekly partial failure, empty add sources, active/
paused/completed plans, long content, keyboard/scroll/back and small/large accessibility layouts.

### Verification plan

- Add named pure-JVM tests for reminder defaults/validation/cadence/advance/end bounds/stable identity,
  confirmed-rate evidence, course/refill presentation, explicit add capabilities and versioned dose
  correction.
- Strengthen the static consumer contract to require the reminder permission/settings recovery,
  persisted trusted scheduler, real-or-unavailable add paths, records tabs and correction consumer,
  and to reject startup notification-permission requests and fake camera/refill/course claims.
- Run focused medication JVM tests, exact source/result inventory, all Android static contracts,
  complete JVM tests, `assembleDebug`, `lintDebug`, and `git diff --check`. Real delivery, reboot,
  OEM notification/channel behavior, lock-screen redaction, TalkBack, large text and small screens
  remain candidate-device sign-offs when no adb device is available.

## Sibling entry points and states

- Empty/loading/error/long-content medication page.
- Upcoming, awaiting-confirmation, snoozed, possibly-missed, taken and skipped occurrences.
- Manual plan creation and OCR prefill review, including low confidence and stale versions.
- Failed/retried dose, recognize, plan-confirm and reaction requests.
- Active/paused/completed/retracted plans and unavailable inventory estimates.
- Mild/moderate/severe adverse reactions.
- Toolbar back, system back, scroll keyboard dismissal and editor cancellation.

## Verification plan

- Named JVM API and policy tests must pin endpoint paths, request shapes, version use, stable event
  retry behavior, primary-action selection, non-confirmed missed copy, prefill confirmation and
  temporal-only reaction wording.
- Android static consumer tests must prove the trusted screen consumes all four authoritative
  resources and excludes legacy CRUD/photo-success language.
- Run focused JVM tests, exact source/result inventory, all static trust tests, assembleDebug, lint,
  and `git diff --check`.
- Emulator automation cannot prove notification delivery, OEM keyboard behavior or severe-event
  clinical handling. Those remain explicit real-device/review limitations.

## 2026-07-15 verification evidence

- Focused JVM: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew
  --no-daemon :app:testDebugUnitTest --tests 'com.xjie.app.feature.medication.*'` — passed,
  exact focused execution `34/34`, including the nine new reminder/record/correction cases.
- Exact JVM inventory: `python3 tools/verify_jvm_test_inventory.py --results
  app/build/test-results/testDebugUnitTest` — passed, exact `80/80`, no missing, extra,
  duplicate, skipped or failed tests.
- Static/adjacent contracts: `python3 -m unittest discover -s tools/tests -p 'test_*.py'` —
  passed `11/11`; the medication consumer contract now also pins permission context/recovery,
  persisted reminder scheduling, records/correction paths and unavailable capability copy.
- Build/lint: `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew
  --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` — passed in 59 seconds.
- Formatting: `git diff --check` — passed.
- An intermediate compile exposed `MedicationUiState` publicly carrying an internal reminder type.
  `TrustedMedicationReminderSettings` was made public because it is part of that public state
  contract; the production compile and full gate above then passed.
- The first focused run used the shell's Java 8 and failed before compilation because AGP 8.11.1
  requires Java 11+. The tracked workspace JDK 17 command above was then used; the failed attempt
  is not cited as test evidence.
- `adb devices` could not run because `adb` is not installed on this machine. Real notification
  delivery, reboot recovery for a persisted snooze,
  OEM keyboards, small-screen/large-font layout, accessibility services and real backend deployment
  remain release-level device/integration sign-offs and are not claimed by these JVM/build checks.
