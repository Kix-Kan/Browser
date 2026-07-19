# Browser foundation decision

Decision date: 2026-07-18

## Decision

KixKan Browser uses **Firefox ESR 140** as its initial engine foundation. KixKan ships its own name, artwork, start page, preferences, package, and updater. It does not use Firefox or Mozilla product branding and does not imply Mozilla endorsement.

## Evaluation

| Criterion | Firefox ESR | Chromium | Chromium Embedded Framework |
| --- | --- | --- | --- |
| Security updates | ESR point releases at least every four weeks and urgent releases when required | Fast upstream cadence, but a downstream branded build must continuously track and package it | Tracks Chromium branches, but KixKan would own both engine and a large native shell integration |
| Linux integration | Mature desktop integration, sandbox, profiles, policy engine, media stack | Mature sandbox and policies; Ubuntu's Chromium delivery differs from conventional `.deb` packaging | Strong embedding API but more custom browser UI, lifecycle, accessibility, and packaging code |
| Extensions | Broad WebExtensions support and enterprise controls | Largest extension ecosystem and strong policy controls | Chrome extensions are not a drop-in browser product feature without added integration |
| DRM/media | Open codecs build normally; Widevine remains optional and separately licensed | Similar proprietary-codec/Widevine constraints | Same Chromium constraints plus embedding integration |
| Branding | Custom source builds are permitted under MPL when Mozilla trademarks are removed | BSD-style Chromium source permits custom branding; Google service/codec components require care | Flexible host branding under BSD, with Chromium notices |
| Maintenance | Annual major ESR migration with security point releases | Frequent major-version rebases and large build infrastructure | Frequent Chromium rebases plus KixKan-owned shell code |
| Memory | Multi-process browser; workload dependent | Multi-process browser; workload dependent | Chromium engine plus a custom host; not inherently lighter |
| Privacy controls | Strong preferences and enterprise policies; KixKan can remove studies/sponsored integrations | Strong policies, but Google service removal/default auditing is broader | Maximum host control but largest opportunity for integration mistakes |

## Why not CEF

CEF is excellent for embedding web content, not for minimizing the maintenance burden of a full consumer browser. KixKan would need to implement and continuously secure tabs, downloads, permissions, certificates, profiles, extension UX, accessibility, updater behavior, and many other surfaces already maintained upstream.

## Why Firefox ESR wins the MVP

Firefox ESR provides the best combination of urgent security servicing, long-lived UI behavior, Linux support, WebExtensions, policy control, and manageable annual migration testing. Mozilla documents that ESR receives security point releases aligned with regular releases and can receive off-schedule fixes for actively exploited issues. KixKan's release monitor treats these updates as independent, urgent browser releases.

## Re-evaluation triggers

- ESR security fixes cannot be rebuilt and released within 48 hours.
- Required DRM/media functionality cannot be legally and reliably delivered.
- A critical extension or web-compatibility requirement is unavailable.
- Chromium packaging becomes materially easier without adding proprietary dependencies.
- Measured memory or performance results on KixKan target hardware favor another foundation.

No comparative security claim should be made without published test methodology and measurements.

