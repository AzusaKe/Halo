<h1>
  <img src="./src/main/resources/assets/halo/textures/halo/ring.png" alt="cover" height="40" style="vertical-align:middle;margin-right:12px">
  Halo Mod
</h1>

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![MC Version](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)
![Mod Loader](https://img.shields.io/badge/Mod%20Loader-Fabric-orange.svg)

English | [中文](README_ZH.md)

## Table of Contents

- [Table of Contents](#table-of-contents)
- [Introduction](#introduction)
- [Features](#features)
- [Planned Features](#planned-features)
- [Installation](#installation)
- [Usage](#usage)
  - [Commands](#commands)
  - [Custom Halo Definitions](#custom-halo-definitions)
- [Building from Source](#building-from-source)
  - [Prerequisites](#prerequisites)
  - [Build](#build)
  - [Run Tests](#run-tests)
  - [Run Client / Server](#run-client--server)
- [Project Structure](#project-structure)
- [Contributing](#contributing)
- [License](#license)

<a id="introduction"></a>

## Introduction

**Halo** is a decorative mod that adds "halos" to vanilla Minecraft entities. Halos smoothly follow entity head movements and are fully configurable through commands — no GUI required.
Currently only available in Minecraft 1.20.1 with Fabric.

> **The project is in early development. Functionality and performance may be unstable. We welcome issues and pull requests to help improve it!**

> Halos use frame-rate-independent discrete position tracking with configurable follow speed and maximum distance clamping. Position resets after entity teleportation.

> ⚠️ **Multiplayer is not yet supported.** Halos currently render only in single-player. Cross-client synchronization (seeing other players' halos on a server) is **not implemented yet** — see [Planned Features](#planned-features). For now, treat Halo as a single-player / local-world mod.

<a id="features"></a>

## Features

- [x] **Command-Driven**: Full `/halo` command tree with tab completion — attach, remove, configure, inspect, and list halos all from chat. See details below.
- [x] **Smooth Damping Physics**: Frame-rate-independent exponential damping for position and rotation. Configurable linear/angular follow speed and maximum distance clamping.
- [x] **Persistent**: Halos survive world reloads and server restarts through entity NBT and world persistent state. Automatically restored on entity load.
- [x] **Teleport-Aware**: When an entity teleports (or crosses dimensions), the halo instantly jumps to the new position — no sliding across the map.
- [x] **Glow Effects**: Each halo can have an optional additive-blended glow layer with pulsing alpha animation.
- [x] **Animation Support**: Halo definitions support position animation curves (oscillate, linear, constant) and rotation animation curves (continuous spin, etc.). **This feature is still in planning, with model support and more animations to be added.**
- [x] **Runtime Configuration**: Damping factors, maximum distance, scale, position offset, and rotation offset can all be modified live via `/halo config`. **Note: Currently cannot configure these parameters for specific individuals and halos. Please manually adjust the individual halo definition file (JSON format) if needed.**
- [x] **Resource Pack Friendly**: Halo definitions are JSON files stored in `assets/<namespace>/halo_definitions/`. Add new halos via resource packs or data packs and run `/reload` to take effect. **The structure of data packs and resource packs is not yet finalized.**
- [x] **Multi-Layer Shapes**: Beyond single billboards, halos can use `MultiBillboardShape` for layered quad effects. **More customization features coming soon!**
- [x] **Distance Culling**: Halos beyond 1000 blocks from the camera are automatically skipped for performance optimization.

## Planned Features

- [ ] **Multiplayer Support (not implemented yet)**: Currently halos render only in single-player. The plan is to use vanilla mechanics (scoreboard tags) to transmit halo information so other players can see each other's halos — and ideally so only the client needs the mod installed. This is the single largest missing feature.
- [ ] **Visible in Inventory**: Currently halos do not render on the player model's head in the inventory screen — this will be added later
- [ ] **More Animations**: Add pulse glow, scale animations, and "intro animations"
- [ ] **More Halo Layer Types**: Planned additions include `mesh` (mesh loaded from `.obj` files), `ring` (similar to `billboard`, a ring with no thickness but with width and diameter displayed using a single texture)
- [ ] **More Layer Fields**: Will add `thickness`, using sprite extrusion to give billboards depth — may affect performance with larger textures
- [ ] **Improved Self-Illumination**: Better compatibility with more shaders and stronger visual quality
- [ ] **Better Entity & Pose Adaptation**: Halo display positions and animations currently have issues on some entities — pending fixes
- [ ] Other bug fixes — issues are welcome

<a id="installation"></a>

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.20.1.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) (version 0.92.0+ for 1.20.1).
3. Download the latest **Halo** mod JAR file from the [Releases](https://github.com/AzusaKe/Halo/releases) page.
4. Place both JAR files into the `mods` folder in your Minecraft installation directory.
5. Launch Minecraft with the Fabric profile.

> **Multiplayer**: Not supported yet. Halos currently render only in single-player worlds; on a dedicated server other clients will not see them. Cross-client synchronization is planned but not implemented — see [Planned Features](#planned-features).

<a id="usage"></a>

## Usage

<a id="commands"></a>

### Commands

All commands require permission level 2 (operator). Use `/halo` with tab completion to explore available subcommands.

| Command                                | Description                                                                      |
| -------------------------------------- | -------------------------------------------------------------------------------- |
| `/halo list`                           | List all loaded halo definitions                                                 |
| `/halo dump`                           | Detailed output of halo definitions including shape, animation, and damping info |
| `/halo show <entity> <definition>`     | Attach a halo to an entity                                                       |
| `/halo hide <entity>`                  | Remove a halo from an entity                                                     |
| `/halo active`                         | List all entities currently wearing a halo                                       |
| `/halo inspect <entity>`               | View detailed runtime status of an entity's halo                                 |
| `/halo config linear-damping <0-1>`    | Set linear follow speed (0 = no follow, 1 = instant follow)                      |
| `/halo config angular-damping <0-1>`   | Set angular follow speed                                                         |
| `/halo config max-linear-distance <n>` | Set maximum distance before hard clamping (blocks)                               |
| `/halo config max-angular-degrees <n>` | Set maximum angular deviation (degrees)                                          |
| `/halo config scale <0.1-5.0>`         | Set uniform scale multiplier                                                     |
| `/halo save`                           | Sync halo data to world persistence and trigger save-all                         |
| `/halo debug <true/false>`             | Toggle teleport/snap debug logging to chat                                       |
| `/halo reload`                         | Hint to use `/reload` to reload halo definitions                                 |
| `/halo config reload`                  | Reload halo definitions from resource/data packs                                 |

**Examples:**

```mcfunction
# Attach the default ring halo to yourself
/halo show @s ring_default

# Attach a halo to another player
/halo show Azusa_Ke ring_default

# Remove your halo
/halo hide @s

# Make the halo respond faster
/halo config linear-damping 0.3
/halo config angular-damping 0.2

# Render the halo larger
/halo config scale 1.5
```

<a id="custom-halo-definitions"></a>

### Custom Halo Definitions

Halo definitions are JSON files stored in `data/<namespace>/halo_definitions/` (data packs) or `assets/<namespace>/halo_definitions/` (resource packs).

> **For a full step-by-step tutorial and the complete field reference, see the docs:**
> [Quickstart](docs/en/quickstart.md) · [Field Reference](docs/en/reference.md)

**Definition Example** (`ring_default.json`):

```json
{
  "id": "halo:ring_default",
  "orientation_mode": "locked",
  "layers": [
    {
      "position": [0.0, 0.0, 0.0],
      "rotation": [0.0, 0.0, 0.0],
      "scale": 1.5,
      "animation": {
        "offset": {
          "y": [{ "function": "sin", "A": 0.01, "omega": 0.5 }]
        },
        "rotation": {
          "yaw": [{ "function": "linear", "start": 0, "speed": -0.1 }]
        }
      },
      "primitive": {
        "type": "billboard",
        "texture": "halo:textures/halo/ring_00.png",
        "size": [0.5, 0.5]
      }
    }
  ],
  "positioning": {
    "offset": [0.0, 0.4, 0.35],
    "scale": 1.0
  },
  "damping": {
    "linearFactor": 0.45,
    "angularFactor": 0.1,
    "maxLinearDistance": 0.5,
    "maxAngularDegrees": 180.0
  }
}
```

| Field                       | Description                                                                       |
| --------------------------- | --------------------------------------------------------------------------------- |
| `id`                        | Unique identifier in format `namespace:name`                                      |
| `orientation_mode`          | `locked`, `free`, or `sync` — how the halo orients relative to the entity head    |
| `layers`                    | Array of layers; each layer is a primitive with its own transform and animation   |
| `layers[].position`         | `[X, Y, Z]` offset of this layer relative to the anchor frame (blocks)            |
| `layers[].rotation`         | `[X, Y, Z]` Euler rotation of this layer (degrees)                                |
| `layers[].scale`            | Per-layer scale multiplier                                                        |
| `layers[].animation`        | Per-layer `offset` / `rotation` animation curves (see reference)                  |
| `layers[].primitive.type`   | `billboard` (single textured quad)                                                |
| `layers[].primitive.texture`| Texture path, e.g. `halo:textures/halo/ring_00.png`                               |
| `layers[].primitive.size`   | `[width, height]` in blocks                                                       |
| `positioning.offset`        | `[X, Y, Z]` offset relative to entity head (blocks)                               |
| `positioning.scale`         | Default scale multiplier                                                          |
| `damping.linearFactor`      | Linear interpolation speed per tick at 20 TPS (0 = no follow, 1 = instant follow) |
| `damping.angularFactor`     | Angular interpolation speed (same range as above)                                 |
| `damping.maxLinearDistance` | Hard clamp maximum distance (blocks)                                              |
| `damping.maxAngularDegrees` | Maximum angular deviation (degrees)                                               |

> After adding or modifying halo definitions, run `/reload` (or `/halo config reload`) to reload.

<a id="building-from-source"></a>

## Building from Source

<a id="prerequisites"></a>

### Prerequisites

- **JDK 17** — Required for Minecraft 1.20.1
- Internet connection (Gradle downloads dependencies from Maven repositories)

<a id="build"></a>

### Build

```bash
git clone https://github.com/AzusaKe/Halo.git
cd Halo
./gradlew build
```

The compiled JAR file will be at `build/libs/halo-1.0.3.jar`.

<a id="run-tests"></a>

### Run Tests

```bash
./gradlew test
```

<a id="run-client--server"></a>

### Run Client / Server

```bash
# Launch Minecraft client with the mod
./gradlew runClient

# Launch dedicated server with the mod
./gradlew runServer
```

<a id="project-structure"></a>

## Project Structure

```
src/main/
  java/network/azusake/halo/
    HaloMod.java              — Mod initializer (server entry point)
    HaloModClient.java         — Client initializer (client entry point)
    animation/                 — Animation curves (Linear, Oscillate, Constant)
    command/                   — /halo Brigadier command tree
    config/                    — Runtime HaloConfig (damping, scale, offset)
    data/                      — HaloDefinition, HaloInstance, HaloEntityData, etc.
    json/                      — JSON deserializers and resource loaders
    lifecycle/                 — EntityHaloTracker, HaloWorldSaveData, event handlers
    manager/                   — HaloManager (singleton managing all active halos)
    mixin/                     — EntityTeleportMixin, LivingEntityDataMixin
    physics/                   — AnchorFrameCalculator, DampingPhysics, HaloTickHandler
    render/                    — HaloRenderer, HaloClientManager, HaloRenderListener
    server/                    — HaloServerEvents, ServerTickHandler
    shape/                     — BillboardPrimitive, HaloModel, HaloLayer, GlowLayer
  resources/
    fabric.mod.json            — Mod metadata (entry points, mixins, dependencies)
    halo.mixins.json            — Mixin configuration
    data/halo/
      halo_definitions/        — JSON halo definition files (data pack)
    assets/halo/
      textures/halo/           — Halo and glow textures
```

<a id="contributing"></a>

## Contributing

Contributions to Halo are welcome! If you have ideas, suggestions, or want to report a bug, please submit an issue on the [GitHub repository](https://github.com/AzusaKe/Halo). If you want to contribute code, please fork the repository and submit a pull request.

- **Development Environment**: Minecraft 1.20.1 + Fabric Loader 0.15+
- **IDE**: Recommended IntelliJ IDEA (with Minecraft Development plugin) or VS Code

<a id="license"></a>

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
