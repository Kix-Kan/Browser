# Security and privacy model

## Release objective

Critical or actively exploited upstream browser fixes are targeted for a signed KixKan release within 24 hours of source availability; other high-severity fixes within 48 hours. This is an operational target, not a guarantee or a comparative security claim.

## Implemented controls

- Exact upstream ESR source version and SHA-512 pin.
- Mozilla source signature verification before a release build.
- Existing Firefox content, socket, GPU, and media process isolation remains enabled.
- No KixKan telemetry endpoint.
- Firefox studies, sponsored tiles, Pocket, and silent crash submission disabled.
- HTTPS-only mode enabled in private windows and recommended for normal browsing.
- Tracking protection set to strict by default but user-changeable.
- Extension installation remains user-controlled; OS policy can block unapproved external installation sources.
- KixKan start extension has no network, tab-history, or host permissions.
- Debian artifacts require a protected signing key and publish SHA-256/SHA-512 checksums.

## Trust boundaries

KixKan Browser inherits engine security from the exact Firefox ESR base plus KixKan patches. A stale KixKan package is unsafe even when its defaults are privacy-focused. Release monitoring must therefore be independent of KixKan OS releases.

Widevine, proprietary codecs, and third-party extensions have separate licenses and security lifecycles. They are not bundled by this MVP.

## Crash reporting

Automatic crash submission is disabled. The browser may retain a local crash record so the user can choose to inspect or submit it. Submission must clearly identify Mozilla's crash service (or a future explicitly documented KixKan endpoint) and require consent.

## Vulnerability reporting

Do not open a public issue for an undisclosed vulnerability. Use the private GitHub security advisory flow for `Kix-Kan/KixKan-Browser`. Include the affected KixKan and ESR versions, reproducibility details, and whether exploitation is known.

