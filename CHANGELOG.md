## Unreleased

### Fixed
- Fixed tinted materials (e.g. Create: Armored Constructs armor, grass, leaves) rendering white/uncolored on corner slopes, inner corner slopes, their layer variants, and the advanced slope layer. A block color handler is now registered so tint is resolved from the copycat material, matching Copycats+ and Create copycats (#36).

## Version 1.2.0

### Added
- Added stonecutting recipes for corner slope, corner slope layer, inner corner slope, and inner corner slope layer using zinc ingots.
- Added crafting recipes to convert between corner slope and inner corner slope (and their layer variants) in both directions.
- Added a crafting recipe to obtain corner slope layer from advanced slope layer.

### Fixed
- Fixed several conversion crafting recipes between corner and inner corner slope variants that had incorrect ingredients or results.
