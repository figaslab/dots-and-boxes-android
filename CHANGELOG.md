# Changelog

## [1.0.2] - 2026-03-23

### Fixed
- Fixed runtime crash (NoClassDefFoundError) caused by ad mediation adapters whose SDK dependencies were unavailable
- Ad mediation exclusion is now dynamic: unavailable SDKs and their adapters are auto-detected and excluded at build time
- CI/CD exclude mechanism now also removes corresponding adapter modules, preventing runtime ClassNotFoundError

## [1.0.1] - 2026-03-20

### Changed
- Replaced age selection bottom sheet with neutral date-of-birth age gate for Google Play Family Policy compliance
- Age verification now uses date of birth input (DD/MM/YYYY) instead of direct age range selection
- Users verified with old method will be asked to re-verify

### Added
- Debug banner toggle in AdManager (debug builds only) - allows disabling banner ads for screenshots/testing
- Debug interstitial percentage now persists in SharedPreferences
- Portuguese translations for all missing strings

### Fixed
- Screenshot script updated to use Practice mode for game screen capture
- Screenshot script now polls for GameActivity instead of using fixed delays
- Ensured all strings have Portuguese translations
