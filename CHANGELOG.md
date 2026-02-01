# Changelog

## [1.2.5] - 2026-01-31

### Added
- Pokemon-style UI design matching Cobblemon's battle interface aesthetics
- Visual catch rate progress bar with color-coded fill
- Cobblemon-style health bars with proper RGB color gradients
- Status effect icons (💤 Sleep, ❄ Frozen, ⚡ Paralysis, 🔥 Burn, ☠ Poison)
- Ball condition indicators (● met / ○ not met)
- Wild Pokemon indicator with red accent styling
- Rounded corners and gradient backgrounds for all panels
- Alternating row backgrounds in ball comparison panel for better readability

### Changed
- Complete UI overhaul with styled panels and borders
- Dynamic border colors based on catch chance (green → yellow → orange → red)
- Improved visual hierarchy with better text shadows and colors
- Ball comparison panel now uses gold borders (0xFFFFAA00) matching Cobblemon
- Shortened ball names in display (removed " Ball" suffix for cleaner look)
- Loading screen now has animated dots

### Fixed
- Out-of-combat display now properly shows catch rates for wild Pokemon
- Improved Pokemon detection with extended raycast fallback

## [1.2.4] - 2026-01-31

### Fixed
- Ancient ball catch rate multipliers (Jet, Wing, Heavy, Leaden, Gigaton, Feather)

## [1.2.3] - 2026-01-31

### Fixed
- Ball comparison panel footer text no longer covered by ball list

## [1.2.2] - 2026-01-30

Initial public release

- Real-time catch rate display during battles
- Ball comparison panel showing all available Poké Balls
- Server-side accuracy using actual HP and status data
- Configurable HUD position and colors
- Support for vanilla and Cobblemon custom balls
