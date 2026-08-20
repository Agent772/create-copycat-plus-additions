## Version 1.3.0

### Added
- Added wall orientation to corner slope, inner corner slope, and their layer variants. Clicking a vertical face now places the block flat against the wall in any of 16 orientations (4 walls × top/bottom edge × apex left/right), matching the wall-mounting behaviour of the straight slope layer.

### Changed
- Corner slope layers and inner corner slope layers now stack with the same two-phase profile as the straight (advanced) slope layer: layers 1–4 raise the apex 4 voxels per layer, reaching 45° at layer 4, then layers 5–8 raise the eave/notch to fill out to a full block at layer 8. Previously they rose 2 voxels per layer and only reached 45° at layer 8, so the shallow sub-45° pitches were only ever available bottom-anchored (as a small wedge on the floor) and could not match a partial-pitch straight-slope roof. Corner layers now match straight slope layers 1-for-1, including the top-anchored shallow pitches.

  **Note:** this intentionally remaps existing blocks — every already-placed corner/inner-corner slope layer is now twice as tall as before (a layer-4 corner goes from 8 to 16 voxels). Contraption re-mesh and Sable mass/volume were updated to match.

### Fixed
- Fixed corner, inner corner, and straight slope layer blocks inconsistently accepting layer-increment clicks depending on which face of the wedge was aimed at. Clicking any face of a slope layer while holding the matching item now reliably grows the stack; the target cell highlights as a full cube while the item is held to signal that the entire cell is clickable.
