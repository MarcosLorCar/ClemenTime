# Changelog

All notable changes to ClemenTime will be documented in this file.

## 1.0.0-rc4 (2026-07-24)

### Added
- Search bar to the schedule library screen to filter schedules easily.
- Categorized schedule lists (Remote, Bundled, and Custom) for clearer organization.
- Auto-update detection for schedules using SHA-256 metadata verification.
- "Update Available" and "Saved" status badges for schedule items.
- Empty state UI to the Home screen widget when there are no classes scheduled.
- Localized strings and Spanish translations for search placeholders, repository configuration, dialog titles, and network connection warnings.

### Changed
- Refactored Home screen widget to reactively collect schedule data and settings updates.
- Refactored scheduling scripts to use regex-based accent and code normalization for Spanish university timetables.
- Externalized UI strings in Import and Settings screens to XML.

### Fixed
- Fixed Home screen widget crash issues by wrapping Hilt entry point lookups in safety try-catch blocks.
- Improved locale detection in widget provider to support older Android versions.

## 1.0.0-rc3 (2026-07-20)

### Added
- Caching state to the local schedule import model to prevent unnecessary re-fetches.
- Automated pipeline scripts to automatically fetch and convert raw Spanish university schedules.

## 1.0.0-rc2 (2026-07-15)

### Added
- First-use onboarding flow to guide new users.
- Feature discovery tooltips on key interactive elements.

## 1.0.0-rc1 (2026-07-10)

### Added
- Reactive Home screen widget showing current and upcoming events.
- Horizontal swipe paging to navigate days on the schedule timeline.
- "Now" indicator line showing the current time position on the schedule.
- Conflict detection and resolution options when importing overlapping schedule slots.
- Adaptive navigation layout using Compose Material 3 Adaptive library (supporting foldables, tablets, and phones).
- Dynamic Theme options (Material 3 Dynamic Color, Dark/Light Mode).
