# Login legal-consent parity contract

## Minimum reproduction and root cause

1. Open Android login, switch to registration, enter otherwise valid credentials, and press `注册` without accepting any legal document.
2. The old Android flow immediately called signup. It had no user-agreement/privacy state, no document views, and no failure-closed ViewModel guard. The research subject switch also remained visible although current iOS keeps that entry unavailable to ordinary users.
3. Root cause: Android registration remained on the earlier onboarding form while current iOS added explicit dual-document consent and hid the research-only subject selector. Legal content was not shared with the support/privacy policy, so parity drift had no regression anchor.

## Permanent invariants

- Ordinary users see the phone login/register surface branded `小捷`; the research subject login implementation may remain for controlled use but has no production UI launcher.
- Login never requires registration agreements. Signup requires both the user agreement and privacy policy to be explicitly accepted before the network request.
- Pressing signup without both agreements presents a second explicit choice; only `确认同意并注册` sets both values and retries. Cancelling sends no request.
- Both legal documents are readable before acceptance. The privacy document is the same versioned local content used by `关于与支持`, not a copy.
- Switching password visibility, keyboard/back handling, validation failure, and document close never silently changes either consent value.

## Same-class states and verification

- Login vs signup, partial/complete consent, confirmation cancel/confirm, document open/close, process recomposition, loading lock, invalid phone/password/username, and compact/large-text layouts.
- Named regression: `LoginLegalConsentPolicyTest`; static wiring regression; deterministic UI coverage before GitHub delivery.
- Real OEM keyboard, password-manager/autofill, TalkBack, and controlled non-production signup remain device/integration evidence and are not proven by the deterministic transport.
