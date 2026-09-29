## Unreleased

### Added
- Corner and inner-corner slopes (and their layer variants) now respond to the sneak + empty-hand connected-textures toggle: the roof keeps the CT tile instead of always drawing the unconnected base tile. Our full corners and upstream `copycat_slope`s no longer block CT on each other, so hip roofs built from a mix read as one connected surface.
- Slopes now connect textures to a neighbouring real block of their own material on faces that sit flush with it (both faces full and in one plane), such as a slope's back wall or bottom next to a full block. Before, only copycats whose touching sides matched the block connected, so a slope stayed framed next to the very block it mimics. Sloped roofs and triangular sides keep their border. Applies to our corners and their layers and to Copycats+' slope, slope layer and vertical slope.

### Changed
- Corner and inner-corner slope roofs (and their layer variants) now match Copycats+ enhanced slopes' texture scale: with `useEnhancedModels` on (the Copycats+ default), each wing is textured at 1:1 surface density from its own edges instead of stretching one tile over the incline, so borders and grain line up with adjacent slopes. Walls shorter than a block (the raised eave walls of outer corner layers, the ridge walls of inner corner layers) show both the top and bottom edge of the side texture, like upstream slope layers. With enhanced models off, the roof keeps the stretched look, like upstream plain slopes.

## Version 1.4.0

### Added
- Added automatic remapping of `copycats:copycat_slope_layer` to `copycatplusadditions:adv_slope_layer` when printing schematics, preserving facing, half, layer count, and waterlogged state. Old schematics saved before this mod was installed now print correctly without manual edits.

### Changed
- Extended compatible Copycats+ version range to include 3.0.7, 3.0.8, and 3.0.9.
