## Version 1.1.0

### Added
- Added [Sable](https://github.com/ryanhcode/sable) physics compatibility: each block now ships per-state mass and buoyancy volume data scaled to the fraction of the block its geometry fills — layer blocks scale per layer, so a 1-layer sliver weighs far less than a full 8-layer block. This data is inert when Sable is not installed.
- Added [Sable Beyond](https://github.com/YassiGame/SableBeyond) soft-dependency support: when installed with dynamic mass enabled, a copycat's Sable mass reflects the material it mimics — obsidian-skinned blocks are heavy, wool-skinned ones are light — scaled by the block's volume fraction. Falls back to the static Sable values when no material is applied, and is completely inert when Sable Beyond is absent.

### Fixed
- Fixed copycats being invisible (outline only, no geometry) when joining a world with them on a Sable sub-level, until any block on the sub-level was updated. On client load each copycat now triggers a re-mesh of its sub-level section so it renders correctly from the moment of world join. Applies to both skinned and unskinned copycats, and is inert without Sable. Note: this fix applies only to this mod's blocks; base Copycats+ and Create blocks on the same sub-level remain affected until Sable addresses the underlying issue upstream.
