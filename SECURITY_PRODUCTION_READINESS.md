# Security, Stability, and Scale Review

## Critical findings in current app

1. **No server-side auth/subscription enforcement exists**
   - All gating is local preference-based and can be bypassed on rooted devices or by local storage edits.
2. **No production-grade CI/CD guardrails**
   - Before this change, no automated lint/unit test/build pipeline existed in GitHub Actions.
3. **Accessibility automation reliability in background is constrained by Android policy**
   - Accessibility services can only act on active/visible windows and events provided by the system.
   - If the rider app is fully backgrounded and no actionable window exists, automatic tapping cannot be guaranteed.
4. **Data exposure risk through backups**
   - Manifest allowed Android backup, which can leak preferences in some restore flows.
5. **Input validation gaps (crash/logic-risk)**
   - Negative/infinite/invalid numeric values could be saved, causing unsafe filtering behavior.

## Immediate hardening completed in this branch

- Disabled app backup in manifest (`allowBackup=false`, `fullBackupContent=false`).
- Added strict numeric validation and bounds checks in settings save flow.
- Added API-based account creation/login/subscription refresh UI and local session persistence.
- Added UPI payment launch + transaction verification flow hooked to API-based subscription activation.
- Added periodic accessibility scan loop to improve detection when ride app remains visible but event flow is sparse.
- Enforced subscription + auth-token checks in service runtime before auto-accept can execute.
- Added GitHub Actions CI workflow for lint + unit tests + APK build + artifact upload.
- Added GitHub Actions release workflow to publish APK artifacts on tags.
- Added baseline unit tests for `FilterEngine` decision and parsing paths.

## What must always be done for production

1. **Secure SaaS architecture**
   - Move entitlement checks to backend with signed tokens (short TTL + refresh + device binding).
   - Never trust only client-side flags for paid subscription or account state.

2. **Payment + subscription lifecycle**
   - Use payment gateway webhooks and server verification before enabling service.
   - Persist subscription state server-side with expiry and grace period policy.
   - App should poll/refresh entitlement and disable automation when expired.

3. **Observability and rollback**
   - Add crash reporting, structured logs, and release health dashboards.
   - Use staged rollouts and kill-switch/remote config for bad releases.

4. **Release hygiene**
   - Enforce CI required checks for every PR.
   - Add static analysis (Detekt/Ktlint), dependency scanning, and secrets scanning.
   - Sign release APK/AAB with managed secrets in CI.

5. **Android background behavior reality**
   - Document that fully backgrounded third-party app screens cannot always be interacted with automatically.
   - Optimize for when target app window is present/foreground and use clear UX prompts for unsupported states.

## Recommended next implementation phases

- Phase 1: Backend auth + JWT, user/device registration, entitlement endpoint.
- Phase 2: Payment webhook integration (UPI via provider), subscription DB model, auto-disable on expiry.
- Phase 3: Remote config + kill switch + release channels.
- Phase 4: UI redesign (glass style) with accessibility-compliant contrast/touch targets.
