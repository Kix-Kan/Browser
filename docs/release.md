# Independent release workflow

KixKan Browser uses semantic versions independent of KixKan OS. Engine-only security rebuilds increment the KixKan patch version even when policies and UI do not change.

1. Security monitor detects a newer supported ESR or advisory.
2. A maintainer updates `VERSION.env`, source digest, and release notes.
3. Validation and policy tests pass on hosted runners.
4. A clean self-hosted builder verifies the Mozilla source signature and builds the engine.
5. Package smoke tests run in a disposable VM/container.
6. Protected release job signs the `.deb`, creates checksums, and publishes artifacts.
7. The KixKan APT repository publishes the package; unattended upgrades can deliver it independently.
8. KixKan OS updates its approved-version pin only after boot, media, policy, and web-compatibility testing.

Signing secrets must never be present in pull-request workflows or developer configuration files.

