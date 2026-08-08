# Copycats+ Additions

Copycats+ Additions is a NeoForge addon for [Copycats+](https://modrinth.com/mod/copycats-plus)
that adds a set of new copycat block shapes — corner slopes, layered corner slopes, and
wall-mountable slope layers. Like every copycat block, each one can be skinned with almost any
block in the game, so you can match them to whatever you are building.

![Showcase of all blocks](Screenshot1.png)

> **Note:** This mod was created with the help of AI.

## Blocks

| Block | Description |
|---|---|
| Copycat: Adv. Slope Layer | Slope layers that can also mount on vertical walls — place one against a wall for an in-wall variant. |
| Copycat Inner Corner Slope | Concave (inner) corner slope that fills the inside corner where two slopes meet. |
| Copycat Inner Corner Slope Layer | Layered version of the inner corner slope, stackable from 1 to 8 layers. |
| Copycat Corner Slope | Convex (outer) corner slope that caps the outside corner where two slopes meet. |
| Copycat Corner Slope Layer | Layered version of the outer corner slope, stackable from 1 to 8 layers. |

Every block is skinnable with any full block, just like the copycats you already know.

## Features

### Roof Texture Rotation

Corner slope blocks support **per-block roof texture rotation**. When you apply a uniform
material (such as planks) and right-click the block with the same material again, the sloped-face
UV projection rotates 90°, letting you choose whether the grain runs along the eave or up the
slope — independently on every placed block.

This rotation is saved in the blockstate, so it persists through world saves, multiplayer sync,
and Create contraption movement. The toggle can be disabled in the server config
(`enableExtraRotation`, default `true`).

### Sable Physics Compatibility

These blocks work with [Sable](https://modrinth.com/mod/sable), the block-physics mod. Because
the shapes only fill part of a block, each one ships Sable `physics_block_properties` data so its
mass and buoyancy volume match the fraction of the block its geometry actually occupies — layer
blocks scale per layer, so a 1-layer sliver is far lighter than a full 8-layer wedge. This data is
inert (does nothing) when Sable is not installed, so it is safe to keep regardless.

Optionally, install [Sable Beyond](https://modrinth.com/mod/sable-beyond) as well (with its dynamic
mass feature enabled) to make a block's physics mass reflect **the material it mimics**: an
obsidian-skinned slope is heavy, a wool-skinned one is light, each scaled by the same shape volume
fraction. The mass updates whenever you change the material or add/remove layers, and reverts to the
static Sable values above when a block has no material. Both mods are entirely optional — nothing
changes if you don't have them.

## Installation

1. Install [NeoForge](https://neoforged.net/) for Minecraft 1.21.1.
2. Install the dependencies: [Copycats+](https://modrinth.com/mod/copycats-plus) and
   [Create](https://modrinth.com/mod/create).
3. Drop this mod's `.jar` into your `mods` folder.
4. Optionally, install [Sable](https://modrinth.com/mod/sable) (and
   [Sable Beyond](https://modrinth.com/mod/sable-beyond)) for the block-physics compatibility
   described above.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- Copycats+ (latest for 1.21.1)
- Create 6.x

### Optional

- Sable — block-physics support (per-shape mass and buoyancy)
- Sable Beyond — material-aware physics mass (requires Sable)

## License

MIT
