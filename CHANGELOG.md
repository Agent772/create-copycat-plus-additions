## Unreleased

### Added
- Added [Sable](https://github.com/ryanhcode/sable) physics compatibility: per-block-state `physics_block_properties` datapack definitions give every block a mass and buoyancy volume scaled to the fraction of the block its geometry actually fills (layer blocks scale per layer). The data is inert when Sable is not installed.

## Version 1.0.0

### Added
- Added Copycat Advanced Slope Layer (`adv_slope_layer`): a slope layer that can also mount on vertical wall faces, replacing and superseding the Copycats+ slope layer. Existing slope layer items can be converted via a shapeless recipe.
- Added Copycat Inner Corner Slope and Copycat Inner Corner Slope Layer: a full block with one corner wedge cut diagonally, available as both a solid block and a stackable 1–8 layer variant.
- Added Copycat Corner Slope and Copycat Corner Slope Layer: an outer triangular wedge that pairs with the inner corner slope to tile a complete full block. Also available as a stackable 1–8 layer variant.
- Added per-block rotatable roof textures for corner slope blocks, allowing each face to display the correct orientation of the applied material.
