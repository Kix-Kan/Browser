# KixKan Browser Android prototype

This is an engineering prototype, not a signed KixKan Browser release. It embeds
GeckoView directly so the team can validate the engine boundary before adopting
the broader Android Components stack.

## Current scope

- one GeckoView session with back, forward, reload, address, and search controls;
- an Android-owned KixKan start page generated into APK assets at build time;
- external HTTP/HTTPS link handling through Android's browser intents, with
  HTTPS upgrade and rejection of file/resource/content/script URIs;
- the approved KixKan symbol and matching native light/dark palettes;
- the same KixKan account credentials and bearer-session API as kixkan.com;
- Android Keystore-backed encryption for the account bearer token at rest;
- local-first bookmark storage with opt-in account push/pull sync, server revision
  cursors, offline pending changes, and deletion tombstones;
- HTTPS-only top-level navigation and Android cleartext traffic disabled;
- Global Privacy Control, query-parameter stripping, cookie purging, and strict
  social tracking protection enabled;
- content-blocking history storage, remote debugging, console forwarding, and
  automatic login filling disabled;
- no analytics, advertising, account, push, location, camera, or microphone SDKs
  or manifest permissions.

The prototype intentionally does not yet implement downloads, permission prompts,
certificate UI, multiple tabs, history sync, password sync, cookie sync, open-tab
sync, end-to-end encryption, or updates. Synced bookmark contents are readable by
the configured KixKan account service; the account screen discloses this before
sign-in.

## Toolchain

- Android Studio Quail 2 or compatible;
- JDK 17;
- Android compile platform37.2 (`platforms;android-37.2`) and Build Tools36.0.0;
  runtime target remains37 and minimum remains26;
- Android Gradle Plugin9.3.3 / Gradle9.5.0 with pinned distribution checksum;
- GeckoView stable `159.0.20261022150525`;
- Android app `0.3.0-prototype`, versionCode3, independently pinned in VERSION.env.

Open this `android/` directory in Android Studio, then run:

```text
gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

On the current machine, `./scripts/build.ps1` can reuse the existing
Projects/.mobile-sim-tools SDK/JDK. It changes process environment only, restores
the previous values, and never starts ADB/emulators, installs tools, accepts
licenses or changes an Android default-browser role. It explicitly stops when
the required37.2 platform is missing; there is no AAR-metadata bypass or engine
downgrade. GeckoView159's AAR metadata requires that minor compile API. Review
platform/tool license requirements separately before installation.
Build/run acceptance must
be taken from the lead's dated report, not inferred from this source README.
Do not install the old153-series APK from an earlier prototype.

## Security pinning

The GeckoView version is an independent mobile security pin in `../VERSION.env`.
Do not downgrade it
to match the desktop ESR major version: the latest GeckoView 140 artifact is from
July 2025 and is unsuitable for a new mobile build in August 2026. Every prototype
update must check Mozilla's Maven metadata and record the new version in review.

## 2026-10-30 Mobile integration

The159 pin was verified against Mozilla's official
[artifact POM](https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/159.0.20261022150525/geckoview-159.0.20261022150525.pom).
Only Android version fields were changed; desktop Firefox ESR/package pins
remain untouched. This Java/GeckoView project builds separately from the
KixKan Mobile Compose project to avoid mixing their dependency graphs.

`MainActivity` accepts BROWSABLE/DEFAULT `ACTION_VIEW` for HTTP and HTTPS and
reuses its singleTask activity through `onNewIntent`. Every incoming URL,
including the existing bookmark URL extra, passes the same bounded gate:
ordinary parsed web URLs only, no credentials/control/backslash/local-resource
schemes, and HTTP upgraded to HTTPS before loading. Internal asset navigation is
available only through native Home/start-up actions, never incoming URL extras.
The existing address-bar search policy and engine HTTPS-only settings remain.

The menu's **Set default browser** action is opt-in. On Android29+ it checks
RoleManager availability/current role, then opens Android's own ROLE_BROWSER
consent screen with a for-result caller identity and a private request code.
On older Android it opens the system default-app settings.
Cancel/unavailable/error outcomes never assign the role in application code.
No role prompt opens on launch, and no script grants or silently changes it.

The native shell uses the Mobile daylight/midnight colors and Gecko's system
appearance preference. The live runtime preference is explicitly mirrored from
the Activity's actual UI_MODE_NIGHT configuration on creation and configuration
updates, rather than assuming the application-context SYSTEM setting refreshes.
This updates CSS prefers-color-scheme; it does not forcibly recolor websites.
GeckoView's first-paint cover matches the window background. Packaged palette
tests cover the mapping, while actual start-page color acceptance requires
native screenshot inspection after both light and dark selections.
The original512x512 approved symbol is copied unchanged (SHA256
`02582f057f04d5fd1427592f8fd71c5209fc456e989ae365514aa0a30a0ed05d`).
Its adaptive foreground is inset into the safe zone. The build generates its
start-page asset from that same canonical PNG, rather than maintaining an
unrelated logo copy. No Apple/Microsoft or generic placeholder logo is shown.

Android owns `start/`; its search is a real HTTPS DuckDuckGo form and its page
uses `prefers-color-scheme`, no remote fonts/scripts or analytics. It describes
unimplemented features instead of linking desktop-only privacy screens. The
dirty shared `extension/start` files and root website/account sources are not
modified by the Android asset-generation task.

Existing Android account, encrypted-session and local-first bookmark code is
preserved. This integration does not sign in, read credentials, change an API
endpoint, run account tests or claim new account/sync acceptance. It adds no
runtime data-access permission; INTERNET remains necessary for browsing, while
backup/cloud/device transfer are explicitly disabled. Production release signing
and an Android update delivery mechanism are not configured.

Ten new pure external-URL tests accompany the existing five address-input
tests. Five Android integration tests verify package/icon/intent registration,
incoming URL gates, packaged palettes and the owned start-page/logo bytes. They
do not launch Gecko, navigate externally or open account screens, so they avoid
ActivityScenario Intent-identity teardown problems and are not renderer/UI proof.
The lead must separately test startup, page rendering, external link dispatch,
light/dark appearance and default-browser consent on the disposable emulator.

Configuration changes transfer the existing live GeckoSession through a static
non-configuration holder. A static session-owned callback bridge holds only
String/primitive navigation state and a weak UI-owner reference. The old
GeckoView is released and the bridge's old UI owner is cleared before transfer;
the new Activity binds fresh views without opening or reloading the session.
Engine observers stay registered, avoiding a Gecko module disable/re-enable
cycle and its initial-state replay. Runtime creation uses application context.
The address editor's framework view-state restoration is disabled because the
session model owns the displayed location; an explicitly retained, still-current
editor draft/selection may be restored instead. No about:blank navigation is
filtered or rewritten to conceal an address mismatch.
Only a terminal close destroys the session; old executor/bookmark resources
are still closed on every Activity destruction. Retained documents do not receive
a new opaque first-paint cover that could obscure an already-painted page.

The separate `BrowserRecreationTest` launches Gecko and loads a fixed local HTML
fixture, then checks live session identity, page/title/address, back/forward
history after real Activity recreation, legitimate about:blank address reporting,
and terminal close. It never sends an
external Intent, uses an account or navigates to a network page. Unlike the five
APK-only integration cases, this test requires actual engine execution. Actual
results and day/night/rotation visual acceptance must be recorded by the lead.
This is configuration retention only, not process-death/session-disk persistence;
when no retained live session exists the existing fresh-start behavior remains.

Known prototype limitation: external host parsing currently accepts ordinary
ASCII/IPv6 URI hosts; internationalized host names should be supplied in their
ASCII punycode form. Unsupported external data is rejected rather than searched.

## Browsing-data milestone — 2026-10-31

Open **Menu → Browsing data**. No category is preselected and opening/cancelling
either screen does not dispatch a clear. Select categories, choose Review, read
the exact all-time scope and confirm Clear data. There is no unsupported
last-hour selector, background auto-clear or silent profile reset.

- **Current tab & history:** closes the sole live session and discards its
  Back/Forward navigation. There is no separate saved visit-history database.
- **Cached images and files:** requests only Gecko NETWORK_CACHE and IMAGE_CACHE.
- **Cookies and site data:** requests only COOKIES and DOM_STORAGES. Website
  sign-ins and offline storage may be lost; native KixKan account credentials
  and bookmarks are not selected.

Cache/site clearing requires the user to explicitly select Current tab &
history too. The UI explains this prerequisite instead of silently discarding an
unchecked category: active documents could recreate storage. The old session is
released/closed before engine clearing. Browsing stays paused until the native
result is terminal, then opens a fresh local KixKan start page. No full session
state or website document is restored. This prototype does not promise that
unselected history can be retained while clearing storage.

There is no filesystem/profile-directory deletion, ALL/SITE_DATA flag,
bookmark/account API, permission reset, password deletion or download removal.
The operation coordinator is runtime-scoped, rejects concurrent requests and
retains its promise through Activity recreation. UI observers are weak and
recreated views do not open a session while waiting. Exposed errors are shown
without URLs, cookies or engine exception text. Failures can be partial and
never trigger an automatic retry. Cancellation before confirmation is safe;
after dispatch, cancellation/rollback is not offered.

**Completion is not a deletion guarantee.** The pinned Gecko159 bridge drops
the service's individual cleaner-failure bitmask. Its result can acknowledge
completion without proving every stored item was removed. The UI therefore
says Clear request completed, not secure erasure. Native synthetic tests verify
specific observable cookies/localStorage/cache behavior, not global erasure.
Process-death recovery during a pending request and forensic erasure remain
unverified. No browsing-data collection or telemetry is added.

Pure model tests cover selection/masks, bounded categories and honest outcomes.
Native tests cover actual loopback cookie/localStorage/cache persistence,
bookmark/preferences preservation, close-boundary failures and busy rejection.
Separate visible-UI tests cover confirmation/cancellation and history reset.
An explicitly simulated promise-failure test covers recreation/error recovery;
it does not claim to force a real engine-cleaner failure. Use the lead's dated
report for executed counts, screenshots and limitations.

Primary interfaces:
[StorageController](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/StorageController.html),
[clear flags](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/StorageController.ClearFlags.html).
Public documentation advances with Gecko; signatures, constants and bridge
behavior were also inspected in the exact159.0.20261022150525 artifact.

Executed results and limitations:
[acceptance report](docs/reports/2026-10-31-privacy.json),
[native passing log](docs/reports/native-privacy-2026-10-31.log),
and [unedited emulator screenshots](docs/screenshots/privacy/).
These are prototype source-review evidence, not a production APK release.
