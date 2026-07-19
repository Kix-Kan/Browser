# First-release feature coverage

KixKan Browser builds on maintained Firefox ESR features instead of recreating
them. Reader mode, picture-in-picture, screenshots, profile/bookmark/history
migration, password import where Firefox safely supports it, and tracker
protection remain upstream-maintained surfaces.

KixKan configures:

- a local KixKan start/new-tab page with no background network requests;
- the built-in protections report as the privacy dashboard;
- strict tracking protection as a recommended, user-changeable default;
- standard tabs by default with the maintained vertical-tabs interface enabled;
- low-memory tab unloading;
- explicit crash submission rather than automatic upload;
- profile import enabled and Firefox Accounts disabled for the initial release;
- package-manager-owned security updates independent from KixKan OS releases.

Website-to-app installation and deeper KixKan Hub actions are staged for a
later signed helper release. They are not represented as complete in 0.1.0.
Optional sync has no server or data collection in this release.
