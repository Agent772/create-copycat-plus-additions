## Unreleased

### Changed
- Corner slope layers and inner corner slope layers now stack with the same two-phase profile as the straight (advanced) slope layer (#43): layers 1–4 raise the apex 4 voxels per layer, reaching 45° at layer 4, then layers 5–8 raise the eave/notch to fill out to a full block at layer 8. Previously they rose 2 voxels per layer and only reached 45° at layer 8, so the shallow sub-45° pitches were only ever available bottom-anchored (as a small wedge on the floor) and could not match a partial-pitch straight-slope roof. Corner layers now match straight slope layers 1-for-1, including the top-anchored shallow pitches.

  **Note:** this intentionally remaps existing blocks — every already-placed corner/inner-corner slope layer is now twice as tall as before (a layer-4 corner goes from 8 to 16 voxels). Contraption re-mesh and Sable mass/volume were updated to match.

## Version 1.2.1

### Fixed
- Fixed corner slopes, inner corner slopes, their layer variants, and the advanced slope layer becoming invisible after being moved by a Create contraption and disassembled, when no mimic material was applied. Base Copycats+ blocks are affected by the same upstream bug and are not fixed by this change — reported upstream.
- Fixed tinted materials (e.g. Create: Armored Constructs armor, grass, leaves) rendering white/uncolored on corner slopes, inner corner slopes, their layer variants, and the advanced slope layer.
