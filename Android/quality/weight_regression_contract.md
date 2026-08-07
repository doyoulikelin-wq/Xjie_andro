# XAGE Weight parity regression contract

Date: 2026-08-07

## Minimum reproduction

1. Launch Android XAGE and tap the active `weight` quick action.
2. The pre-fix app opens the legacy Health Data dashboard rather than a dedicated weight page.
3. There is no equivalent latest-weight card, height entry, BMI calculation, current-relative
   three-month trend, one-decimal weight picker, or weight guidance surface.
4. Account changes, stale responses, unknown trend sources, and failed loads therefore have no
   weight-specific fail-closed state.

## Confirmed root cause

Android registered the Weight quick action but mapped it to `Route.HealthData`. Weight behavior was
not owned by a dedicated repository/state machine/presentation contract, so generic trend UI could
not enforce the iOS rules or the Android Health Connect provenance boundary.

## Permanent invariants

- `weight` resolves to the stable root destination `Route.Weight`; system Back returns to the XAGE
  Data page and never reconstructs the shell as a nested destination.
- Every load and manual save captures account scope, selected subject, and monotonic auth generation
  before suspension. A -> B -> A and late old-account responses fail closed.
- The page reads Health Connect measurements only after they have been uploaded and returned by the
  account-bound server trend endpoint. It does not query local Health Connect records or invent
  samples.
- Accepted weight/height samples are numeric, finite, non-future, correctly unit-bound, and from an
  explicit trusted family: user manual entry, stable-ID device/Health Connect/Apple Health entry, or
  the server's admitted-confirmed-report trend projection. Unknown source, missing device identity,
  category value, wrong metric identity, or wrong unit is excluded.
- Latest weight uses the latest admitted full-history sample. The chart uses only the current-date
  relative three-month window. An old latest record may remain visible while the recent chart is
  honestly empty.
- BMI is derived only from an admitted latest weight and a 60-210 cm server profile/admitted height.
  Missing or invalid height leaves BMI as `--` and exposes the explicit height action.
- Manual height accepts integer 60-210 cm. Manual weight mirrors iOS at 20.0-250.9 kg with one
  decimal. Failed saves remain open and show an explicit retryable error.
- Loading, empty, error, refreshing, and saving are visually distinct. The custom height keypad and
  native wheel pickers do not summon the system keyboard. Back/close, compact phone, large text,
  navigation bars, TalkBack semantics, and at least 48 dp actions remain usable.

## Sibling entry points and states

- XAGE Weight quick action and a future weight metric-card entry must share `WeightScreen`.
- Initial load, pull refresh, no records, only old records, missing height, malformed samples,
  account/subject change, duplicate save tap, save failure, sheet close, system Back, and process
  recreation are covered by the same state machine/policy.
- Health Connect stays a read-only sync entry on XAGE Data; this page shows only the server-confirmed
  round trip and clearly labels that boundary.

## Verification plan

- JVM: `WeightDashboardParityTest` and `WeightApiContractTest`.
- Static: `WeightParityContractTests`.
- Deterministic connected UI: `WeightDashboardUiTest` on `standard_api35`, `compact_api35`, and
  `large_text_api35`, using the shared Debug-only transport and its no-escape assertion.
- Adjacent: XAGE quick-action/shell tests, Health Connect sync contracts, main-source compilation,
  AndroidTest compilation, exact inventories, and the repository Android gate.
- Real-device limitations: Health Connect authorization/provider behavior, OEM `NumberPicker`,
  gesture navigation, and TalkBack traversal require controlled device validation.
