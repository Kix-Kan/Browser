# Build and packaging

The build is intentionally isolated from KixKan OS. KixKan OS downloads a separately released, approved `.deb`; it never compiles the engine during ISO assembly.

## Builder requirements

- Ubuntu 26.04 x86-64 or a pinned equivalent container
- 16 GB RAM minimum, 32 GB recommended
- 80 GB free disk
- Mozilla build bootstrap dependencies
- `python3`, `curl`, `gpgv`, `xz-utils`, `zip`, `dpkg-dev`, `debhelper`, `debsigs`

## Build sequence

```bash
bash scripts/fetch-source.sh
bash scripts/build-extension.sh
bash scripts/build-engine.sh
bash scripts/build-deb.sh
```

`fetch-source.sh` verifies the pinned SHA-512 checksum and Mozilla signature. `build-engine.sh` copies only the KixKan branding overlay and distribution defaults into the verified source tree, then invokes Mozilla's build system. The build has not been completed in the current Windows workspace; only the overlay, scripts, policies, and tests are validated here.

## Reproducibility boundary

`SOURCE_DATE_EPOCH`, the ESR source digest, overlay, and package metadata are pinned. Toolchains, Rust crates, Node packages used by Mozilla, and Ubuntu build dependencies must also be snapshot-pinned before claiming bit-for-bit reproducibility.

