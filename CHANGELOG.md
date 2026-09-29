## Version 1.5.0

### Added
- Added Copycats+ characteristics tooltips to corner slopes, inner corner slopes, and their layer variants — hold Shift for details, matching upstream: Copycat and CT Toggle on the corner slopes, plus Stackable on the layer variants.

### Fixed
- Fixed corner and inner-corner slopes (and their layer variants) not responding to the sneak + empty-hand connected-textures toggle — the roof now shows the connected tile instead of always the unconnected base tile. Fixed our full corners and upstream slopes blocking connected textures from flowing across each other, so hip roofs built from a mix of both now read as one connected surface.
- Fixed slopes not connecting textures to a neighbouring block of their own material on faces that sit flush with it, such as a slope's back wall or bottom next to a full block — the slope stayed oddly framed before. Sloped roofs and triangular sides still keep their border. Applies to our corners and their layers and to Copycats+' slope, slope layer, and vertical slope.
- Fixed corner and inner-corner slope roofs (and their layer variants) not matching Copycats+ enhanced slopes' texture scale — with enhanced models on (the default), each wing now textures at 1:1 density from its own edges instead of stretching one tile over the incline, so borders and grain line up with adjacent slopes.
