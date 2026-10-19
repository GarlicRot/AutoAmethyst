# Changelog

Notable changes to AutoAmethyst are documented here. Releases follow [Semantic Versioning](https://semver.org/).

## [2.0.0] - 2026-10-18

### Fixed
- Replaced the unreliable one-tick START/STOP sequence with vanilla mining-progress-aware breaking.
- Improved crystal targeting using real block hit shapes and line-of-sight checks.
- Protected Budding Amethyst and normal Amethyst Blocks by strictly restricting valid mining targets.
- Added world-state confirmation and bounded retries for server-rejected or unconfirmed breaks.

### Improved
- Immediately move to the next available target after a successful block-state update.
- Rescan nearby crystals every two ticks.
- Honor the player's effective block interaction reach, capped at vanilla **4.5-block Survival** and **5.0-block Creative** limits.
- Minimize settings to **Harvest** and **Show Targets**.
- Make **Mature Clusters** the default harvest mode.
- Avoid interfering with manual mining and item use.

### Maintenance
- Restrict declared compatibility to **Minecraft 1.21.4**, the tested target.
- Add automated unit tests, CI, tag-based release builds, metadata validation and SHA-256 checksums.
- Rewrite setup, testing and release documentation.

**Validation:** v2.0.0-beta.2 compiled with Java 21 and passed the reported in-game checks in the Minecraft 1.21.4 Prism Launcher instance. The stable version promotes that working code without changing the harvesting implementation.

## [2.0.0-beta.2] - 2026-10-18
- Faster confirmed-break handoff, two visible settings, and automatic vanilla-max reach.
- Added reach/verification regression checks.

## [2.0.0-beta.1] - 2026-10-18
- Rebuilt the harvesting engine with progress-aware mining, target validation and retries.

[2.0.0]: https://github.com/GarlicRot/AutoAmethyst/releases/tag/v2.0.0
[2.0.0-beta.2]: https://github.com/GarlicRot/AutoAmethyst/releases/tag/v2.0.0-beta.2
[2.0.0-beta.1]: https://github.com/GarlicRot/AutoAmethyst/releases/tag/v2.0.0-beta.1
