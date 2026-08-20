# Inner Corner Slope - Architecture Reference

This document explains every layer of the inner corner slope implementation: geometry, physics,
rendering, outline, and registry. It is written for future agents (or contributors) who need to
modify or extend this block.

---

## What the block is

The **inner corner slope** is the geometric complement of Copycats+'s outer corner slope. Where the
outer corner fills one triangular quadrant of a block, the inner corner fills the remaining three
quadrants. Together they tile a full block.

Concretely: a full 16×16×16 block with one diagonal wedge removed. The cut goes from the
"notch corner" (zero height) across to the opposite corner (full height). The result has:
- Three solid walls (two axis-aligned faces + the block base)
- One diagonal slope face (the cut surface)
- A ridge where the two outer ramps meet

There is also a **stackable layer variant** (`inner_corner_slope_layer`, 1–8 layers) that
follows the same two-phase profile as the straight slope layer (issue #43): layers 1–4 raise
the three corners 4 voxels per layer (bottom-anchored, reaching the full inner corner at layer
4), then layers 5–8 raise the notch to fill out to a full block at layer 8.

---

## Block state properties

Both blocks share `FACING` (N/S/E/W) and `HALF` (TOP/BOTTOM). The layer block adds `LAYERS`
(1–8, integer).

| Property | Source                          | Effect                                               |
|----------|---------------------------------|------------------------------------------------------|
| `FACING` | `BlockStateProperties.HORIZONTAL_FACING` | Rotates the notch corner around Y          |
| `HALF`   | `BlockStateProperties.HALF`     | Flips the geometry vertically (floor vs ceiling)     |
| `LAYERS` | `BlockStateProperties.LAYERS`   | Two-phase profile: apex rises 4 voxels/layer (1–4), then the notch fills (5–8) (layer block only) |

**Notch corner mapping** (local block coords, verified against the render rotation in
`CopycatInnerCornerSlopeModelCore`):

| FACING | Notch corner (local XZ) | World description |
|--------|-------------------------|-------------------|
| SOUTH  | (1, 0) = NE             | Notch at the north-east |
| WEST   | (1, 1) = SE             | Notch at the south-east |
| NORTH  | (0, 1) = SW             | Notch at the south-west |
| EAST   | (0, 0) = NW             | Notch at the north-west |

---

## Files at a glance

```
src/main/java/…/blocks/
  CopycatInnerCornerSlopeBlock.java         block logic, placement, transform
  CopycatInnerCornerSlopeLayerBlock.java    extends above, adds LAYERS, stacking, wrenching

src/main/java/…/client/
  CopycatInnerCornerSlopeModelCore.java     runtime geometry (quads)
  CopycatInnerCornerSlopeLayerModelCore.java  same, driven by LAYERS

src/main/java/…/
  CCAdditionsShapes.java                    VoxelShape (physics + selection outline)
  CopycatPlusAdditions.java                 mod init, client side-load guard, creative tab

src/main/java/…/registry/
  ModBlocks.java          DeferredBlock registrations
  ModItems.java           DeferredItem registrations
  ModBlockEntities.java   CCCopycatBlockEntity types (one per block, see below)

src/main/resources/assets/copycatplusadditions/
  blockstates/inner_corner_slope{_layer}.json   always { "": air } — real model injected at runtime
  models/block/inner_corner_slope{_layer}.json  parent = copycats:block/copycat_base/slope (item fallback)
  models/item/inner_corner_slope{_layer}.json   parent = above block model
```

---

## Block entity requirement

Every Copycats+ copycat block needs its **own** `BlockEntityType<CCCopycatBlockEntity>`. Reusing
Copycats+'s internal type fails silently: `BlockEntityType.create()` returns `null` when the
placed block is not in the type's `validBlocks` set, and every subsequent read of the stored
material NPEs.

`ModBlockEntities` registers one type per block, each constructed with a lambda so the type
holds a reference to itself (avoids the forward-reference problem):

```java
BLOCK_ENTITIES.register("inner_corner_slope", () ->
    BlockEntityType.Builder
        .of((pos, state) -> new CCCopycatBlockEntity(
                ModBlockEntities.INNER_CORNER_SLOPE.get(), pos, state),
            ModBlocks.INNER_CORNER_SLOPE.get())
        .build(null));
```

---

## Physics / collision / face-occlusion — `CCAdditionsShapes`

`VoxelShape` in Minecraft is a union of axis-aligned boxes. True diagonal geometry is impossible.
`CCAdditionsShapes` builds an **8-step staircase** that approximates the inner-corner slope
surface and wraps it in a custom `OutlinedShape`.

### Staircase geometry

The slope surface in normalised block coords is `h(x,z) = max(dx, dz) * maxH` where `dx`, `dz`
are distances from the notch corner. At each step `i` (0–7), threshold `t = i/8` (the bottom of
the slice):

- The "solid L" at threshold `t` decomposes into two non-overlapping AABBs:
  - **Box A** — full-x, z-arm (away from the notch-z edge)
  - **Box B** — x-arm, complementary z-band (no overlap with A)

Using `t = i/8` (floor, not ceiling) ensures step 7 (`t = 7/8`) produces a thin L-shape that
reaches full height. The old ceiling formula caused the staircase to peak at 7/8 height.

The staircase is driven by two parameters, `apexTop` and `floor` (both in 0..1). For the solid
block they are `1.0` and `0.0`. For the layer variant they follow the two-phase profile: layers
1–4 use `apexTop = layers/4`, `floor = 0` (wedge grows up); layers 5–8 use `apexTop = 1.0`,
`floor = (layers-4)/4` (a full-footprint base slab fills below the wedge). At `layers=4` the
shape is the full inner corner; at `layers=8` it is a solid full block.

### `OutlinedShape` — smooth selection highlight

Minecraft calls `VoxelShape.forAllEdges()` to draw the selection highlight. The default
implementation emits box-derived edges, giving a stepped outline.

**Copycats+ technique**: `OutlinedVoxelShape` (in the Copycats+ source) overrides
`forAllEdges()` to emit explicit diagonal line segments stored in a `MutableShape.outlines`
list. This gives a true diagonal wireframe while keeping box-based physics.

**Our implementation** (`OutlinedShape`, private inner class of `CCAdditionsShapes`):
- Extends `ArrayVoxelShape` (public class, `protected` constructor accessible from subclass)
- Stores 12 wireframe edges as a flat `double[]` (6 doubles per edge: x1,y1,z1,x2,y2,z2)
- Overrides `forAllEdges()` to emit those 12 edges instead of box-derived edges
- Extracts the `DiscreteVoxelShape` from the staircase via reflection on `VoxelShape.shape`
  (Mojang-mapped field name; works at runtime in NeoForge 1.21.1)

This is **self-contained** — no Catnip dependency. Copycats+ 3.0.4 uses `net.createmod.catnip.data.Pair`
internally, but available Catnip 1.21.1 jars (0.8.14–0.8.54) only have `net.createmod.catnip.utility.Pair`
(package renamed between versions). Directly importing Copycats+'s `OutlinedVoxelShape` fails to
compile. Hence the independent reimplementation.

### The 12 wireframe edges

Four XZ corners are named:
- **A** — notch corner (zero height)
- **B** — same-z as notch, opposite-x
- **C** — same-x as notch, opposite-z
- **D** — far corner (opposite of notch, full height)

| Group                    | Edges                                       |
|--------------------------|---------------------------------------------|
| Flat face (4 edges)      | A–B, A–C, C–D, D–B                          |
| Vertical edges (3 edges) | B-bottom to B-top, C-bottom to C-top, D-bottom to D-top (none at A, zero height) |
| Horizontal top (2 edges) | B-top to D-top, D-top to C-top              |
| Diagonals to notch (3)   | B-top to A, A to C-top (adjacent faces), D-top to A (slope ridge) |

For `HALF=TOP` the flat face is at y=1.0 and `fullY = 1.0 - maxH`; for `HALF=BOTTOM` the flat
face is at y=0.0 and `fullY = maxH`.

---

## Client-side rendering — `CopycatInnerCornerSlopeModelCore`

### How Copycats+ copycat rendering works

Each Copycats+ block uses a **`CopycatModelCore`** to emit quads at render time. The core is
never used directly; instead `CopycatModelCore.createModel(originalBakedModel, core)` wraps the
baked model in a `CopycatModelNeoForge`, which:

1. Reads the copycat material (`BlockState`) stored in the block entity's `ModelData`
2. Calls `emitCopycatQuads(key, blockState, context, material)` on the core
3. Fetches quads from the *material block's* baked model and re-maps their UV/texture to match
   the slope geometry

The `assemblePiece(transform, origin, aabb, cullMask, ...QuadTransforms)` call in the context
takes one rectangular box from the material block's model, clips it to the given AABB, applies
optional transforms (slope, shear, scale, UV update), and adds the resulting quads to the
output.

### Model registration

`CopycatPlusAdditions` registers blocks via `DeferredRegister`, not Registrate. Copycats+ uses
Registrate's `blockModel()` hook to inject its model cores; we cannot do that. Instead,
`CopycatPlusAdditionsClient.onModifyBakingResult` (fired on `ModelEvent.ModifyBakingResult`)
iterates all block states, looks up each baked model by `ModelResourceLocation`, and replaces it
with `CopycatModelCore.createModel(original, new XxxModelCore())`.

The blockstate JSON always points to `minecraft:block/air` (a placeholder the baking system
accepts), so `original` is the air baked model. The swap happens after baking, ensuring the
correct model is used at runtime.

**Item models** use the static parent JSON (`copycats:block/copycat_base/slope` fallback) because
`CopycatModelNeoForge.getQuads(null, ...)` skips all entries when the `MATERIALS_PROPERTY` map
is empty (no block entity data), producing no quads. The item render falls back to the original
static model. This is the same behaviour as Copycats+'s own slope items.

### Two-piece construction

The inner corner slope is rendered as **two planar slope pieces** rather than one non-planar
surface. A single non-planar UP face would cause triangulation ambiguity in `QuadSlope`'s fixed
`v0–v2` triangulation.

```
Piece 1: Z-slope (low at z=0 / north, high at z=16 / south)
  slope: (a, b) -> map(0, 16, 0, h, b)    (b = z coordinate)
  cull:  NORTH (degenerate zero-height edge), WEST (replaced by Piece 2)

Piece 2: X-slope (low at x=16 / east, high at x=0 / west)
  slope: (a, b) -> map(0, 16, h, 0, a)    (a = x coordinate)
  cull:  EAST (degenerate zero-height edge), SOUTH (replaced by Piece 1)
```

Where the two pieces overlap, the **z-buffer** resolves to the higher surface: `max(h_z(z), h_x(x))`.
This is exactly the inner-corner profile. The notch lands at the **NE local corner** (x=1, z=0)
before `yRot` is applied.

**Rotation mapping**: `yRot = (int) facing.toYRot()`. The pre-rotation notch at NE steps around
as:

| FACING | yRot | Notch after rotation |
|--------|------|----------------------|
| SOUTH  |   0° | NE (x=1, z=0)        |
| WEST   |  90° | SE (x=1, z=1)        |
| NORTH  | 180° | SW (x=0, z=1)        |
| EAST   | 270° | NW (x=0, z=0)        |

This matches the `notchCorner()` mapping in `CCAdditionsShapes` — both must be consistent or the
VoxelShape and rendered geometry diverge.

**HALF=TOP**: `AssemblyTransform` calls `.flipY(topHalf)` after `rotateY`. The cull masks
transform with the flip, so NORTH/WEST/EAST/SOUTH still point to the correct local directions.

### Layer variant

`CopycatInnerCornerSlopeLayerModelCore` calls `assembleInnerCorner(context, facing, half, apexTop, floor)`
with the two-phase mapping: layers 1–4 use `apexTop = layers*4`, `floor = 0`; layers 5–8 use
`apexTop = 16`, `floor = (layers-4)*4`. The sloped face runs from `floor` at the notch to
`apexTop`; the solid body below is supplied by the full-height `aabb` prism (no separate slab
piece). At `layers=4` the result is the full inner corner; at `layers=8` it is a solid full block.

### Transparency artifact (known issue / future work)

The two full-16×16 pieces overlap in the entire XZ plane. When the copycat material has a
partly-transparent texture, Piece 1's slope face shows through the transparent regions of Piece
2's slope face (and vice versa), revealing geometry that should be hidden inside the block.

**Root cause**: both slope faces are rendered for the full AABB. At any (x,z) where one piece
is behind the other, that piece's face is still emitted and visible through transparency.

**Planned fix**: replace the two full `assemblePiece` calls with a staircase of 8 sub-pieces
each, approximating the diagonal split line `x + z = 16` with N=8 rectangular strips:

- Piece 1 strip `k` (k=1..8): `x = [2(k-1), 16], z = [16-2k, 16-2(k-1)]`
- Piece 2 strip `k` (k=1..8): `x = [16-2k, 16-2(k-1)], z = [0, 2k]`

Per-strip cull masks: only the WEST/NORTH (Piece 1) and EAST/SOUTH (Piece 2) on boundary strips
that actually touch the block perimeter. Interior strip edges must NOT be culled (they are exposed
slope surface faces, not internal seams). This reduces the overlap area from 256 to ~8 × 4 = 32
voxels², eliminating the visible artifact for all but a thin diagonal boundary band.

---

## Dependency notes

### Copycats+ (All Rights Reserved)

Copycats+ 3.0.4 is a required runtime dependency. Our `OutlinedShape` reimplements the same
technique used by Copycats+'s `OutlinedVoxelShape` (override `forAllEdges()` with explicit line
segments). **No Copycats+ source code was copied.** The implementations differ in class hierarchy,
data structure (flat `double[]` vs `List<Pair<Vec3,Vec3>>`), and the mechanism for extracting the
`DiscreteVoxelShape` (reflection vs Catnip's `MutableShape`). Technique attribution is noted in
`CCAdditionsShapes` Javadoc.

### Catnip — do NOT add as a dependency

Catnip is Copycats+'s utility library. The version available for NeoForge 1.21.1 (0.8.14–0.8.54)
exposes `net.createmod.catnip.utility.Pair`. Copycats+ 3.0.4 was compiled against a version that
had `net.createmod.catnip.data.Pair`. Adding any Catnip jar will not resolve the package mismatch.
Importing Copycats+'s own internal classes (`OutlinedVoxelShape`, `MutableShape`) that depend on
that type will fail at compile time.

### Create

Required on the compile classpath (`compileOnly`) because Copycats+ block supertypes extend
Create base types (`IBE`, `IWrenchable`, `ProperWaterloggedBlock`). Create is supplied transitively
at runtime by Copycats+ and does not need to be in the shipped jar.

---

## Adding a new inner-corner-shaped block

1. **Block class** — extend `CopycatInnerCornerSlopeBlock` (or `CCWaterloggedCopycatBlock`
   directly). Override `getShape()` to call `CCAdditionsShapes.innerCornerSlope(facing, half)`.
2. **Block entity type** — register a new `CCCopycatBlockEntity` type in `ModBlockEntities`
   with your block in `validBlocks`. Do not reuse an existing type.
3. **Model core** — extend `CopycatModelCore`, override `emitCopycatQuads()`, call
   `CopycatInnerCornerSlopeModelCore.assembleInnerCorner(context, facing, half, maxHeight)`.
4. **Model swap** — add a `swapModelCore(models, block, blockId, YourModelCore::new)` call in
   `CopycatPlusAdditionsClient.onModifyBakingResult`.
5. **Blockstate JSON** — `{ "variants": { "": { "model": "minecraft:block/air" } } }`.
6. **Block model JSON** — use the inner-corner stair or slope parent for the item fallback shape.
7. **Item model JSON** — `{ "parent": "copycatplusadditions:block/your_block" }`.
