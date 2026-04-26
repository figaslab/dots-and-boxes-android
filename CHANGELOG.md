# Changelog

## [1.4.0] - 2026-04-27 (build 26042701)

### Added
- `-PuseLocalSubmodules` flag for sibling-source iteration on submodule changes.

### Fixed
- Per-turn timer now also active in tournament mode.

### Changed
- Removed in-tree submodule copies; submodules consumed from GitHub Packages (uikit 1.7.0, mockpvp 1.4.0, gridgame 1.0.0, p2pkit 1.1.0).

## [1.3.0] - 2026-04-24 (build 26042401)

### Added
- First-launch tutorial: deterministic 6-step flow that guides the player through drawing three sides of a box and closing it to earn the extra turn. The Tutor bot plays a scripted helpful move to set up the box.
- "Ad" disclosure overlay anchored to the left of the in-game banner.

### Changed
- Rewarded ad buttons show "Ad" prefix + reward amount (Google Play
  Families Ad Format Requirements) (mockpvp 1.3.0).
- Bumped submodule versions: mockpvp 1.2.1 → 1.3.0, uikit 1.0.0 → 1.2.0.
- Added "View Tutorial" entry to debug menu for quicker tutorial testing.

### Fixed
- Date-of-birth bottom sheet opens fully expanded in landscape
  (mockpvp 1.3.0).
- Tutorial softlock: AI script length now matches the player move count, so the flow reliably reaches the box-closing congratulations step.

## [1.2.1] - 2026-04-21 (build 26042102)

### Fixed
- Nickname field text on login was unreadable — it was painted the same color as the field background. Restored dark text color for proper contrast across device themes. (mockpvp 1.2.1)

## [1.2.0] - 2026-04-21 (build 26042101)

### Changed
- Local and CI builds now resolve submodule dependencies from the same source (GitHub Packages); lib-versions.properties is the single source of truth
- Bumped submodule versions: p2pkit 1.0.0 → 1.1.0, mockpvp 1.0.0 → 1.2.0

### Added
- Google Families Policy compliance: Firebase Analytics consent defaults (AAID/SSAID collection off, ad_* signals denied) and per-age-category consent applied at startup via AnalyticsManager.applyConsentForAge

## [1.1.0] - 2026-04-10

### Added
- Cross-game promotion cards in P2P lobby and tournaments
- Optional cryptography in P2P communication (disabled for game moves, reduces packet size ~85%)

### Fixed
- Duplicate variant selection dialog in WiFi/Bluetooth lobby
