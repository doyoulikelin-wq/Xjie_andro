# Android Health Connect regression contract

## Minimum reproduction

The current XAGE Android-health control changes UI state after a fixed delay and merges
`androidHealthSamples` that are compiled into the app. It does not check Health Connect SDK
availability, request Health Connect permissions, read device records, or call
`POST /api/health-data/indicators/device-sync`.

## Root cause

Android health was implemented as presentation-only sample state. There was no platform adapter,
permission state machine, account-bound upload client, or stable source-identity mapping.

## Permanent invariants

- Health Connect is pinned to stable `1.1.0`. Its AAR requires compile SDK `36` and AGP `8.9.1`
  or newer; the official API-36-compatible matrix is therefore pinned without metadata suppression:
  AGP `8.11.1`, Gradle `8.13`, compile SDK `36`. Target SDK remains `35`, minimum SDK remains `28`,
  and Java source/target compatibility remains `17`.
- The app requests only read permissions for steps, distance, sleep, HRV RMSSD, resting heart rate,
  and weight. No write permission is requested.
- Every SDK read and the upload boundary re-check the relevant permission. Missing permission,
  unavailable/outdated provider or metric, an unstable Health Connect record ID, an over-broad
  time window, or an account/session change fails closed; none of these states may be represented
  as success. A per-metric SDK exception names the unavailable metric and uploads no partial read.
- Reads are limited to at most 30 days and 5,000 records per metric.
- Every uploaded item has a deterministic `source_id`, stable `source_metric`, explicit local date,
  measured-time UTC offset, and `source=device`.
- The upload uses the bearer token captured before the read through a dedicated client with no
  authenticator. A concurrent account switch can therefore never redirect the payload to the new
  account.
- UI integration uses the Health Connect permission result contract and exposes real
  unavailable/permission-required/empty/error/success states. It contains no delayed or sample
  success path.

## Sibling paths and states

SDK unavailable, provider update required, logged out, permission denied/partially granted,
empty records, pagination/record cap, token or subject switch during read, network rejection,
partial server rejection, timezone/DST offset, and repeated idempotent sync.

## Verification

Named JVM tests cover the permission allowlist, bounded window and stable identity/payload mapping,
per-operation permission checks, explicit unavailable-metric handling, account-switch upload binding,
Retrofit endpoint/body, and UI state policy. Run the focused JVM suite, exact JVM inventory verifier,
debug assembly, and diff check.
