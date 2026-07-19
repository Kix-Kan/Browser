# KixKan Browser

**Developed by Mac and Windows**

KixKan Browser is a separately versioned, privacy-focused Linux browser distribution based on Mozilla's open-source Firefox ESR code. It does not use Mozilla names, logos, or product branding and is not affiliated with or endorsed by Mozilla.

This repository contains KixKan's browser overlay, policies, start/new-tab extension, source builder, Debian and experimental Flatpak packaging, updater integration, automated tests, and independent security-release workflows. It intentionally does not live inside KixKan OS.

## Versions

- KixKan Browser: `0.1.0`
- Foundation: Firefox ESR `140.12.0esr`
- Supported architecture for the MVP: Linux `amd64`

The full browser engine is downloaded from Mozilla's signed source archive and verified against the pinned SHA-512 digest. Upstream engine source is not vendored into Git.

## Repository map

| Path | Responsibility |
| --- | --- |
| `browser/branding/kixkan/` | KixKan build-time branding overlay |
| `extension/` | Local start and new-tab experience |
| `distribution/` | Enterprise policies, defaults, search, and bookmarks |
| `scripts/` | Verified source retrieval, engine/package build, signing, and validation |
| `debian/` | Debian binary package metadata |
| `flatpak/` | Experimental Flatpak manifest |
| `.github/workflows/` | Validation, dependency/security monitoring, and releases |
| `docs/` | Foundation decision, security model, build, and release process |

## Validate the overlay

```bash
python3 -m unittest discover -s tests -v
bash scripts/validate.sh
```

## Build overview

Full engine builds require a dedicated Linux builder with at least 16 GB RAM, 80 GB free disk, and Mozilla's documented Firefox build dependencies:

```bash
bash scripts/fetch-source.sh
bash scripts/build-extension.sh
bash scripts/build-engine.sh
bash scripts/build-deb.sh
```

The Debian package is signed only in the protected release workflow. A local unsigned package must never be presented as an official release.

## Privacy position

There is no hidden KixKan telemetry. Studies, sponsored content, Pocket, form autofill telemetry, and silent crash submission are disabled. Crash reporting requires explicit user action. Search queries go only to the provider selected by the user; DuckDuckGo is the initial default and can be changed.

KixKan Browser does not claim to be categorically more secure than other browsers. Security depends on how quickly each pinned ESR update is built, tested, signed, and delivered.

