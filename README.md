# Halo Mod

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![MC Version](https://img.shields.io/badge/Minecraft-1.20.1-green.svg)
![Mod Loader](https://img.shields.io/badge/Mod%20Loader-Fabric-orange.svg)

English | [中文](README_ZH.md)

## Table of Contents

- [Introduction](#introduction)
- [Features](#features)
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

**Halo** is a Fabric mod for Minecraft 1.20.1 that renders decorative ring-shaped billboards (called "halos") above entities' heads. Halos are smooth, configurable, persistent, and fully command-driven — no GUI required.

> The halo uses frame-rate-independent exponential damping physics, so it smoothly trails the entity's head movement instead of being stiffly glued to it. It survives world reloads, server restarts, and even teleports instantly snap into position.

<a id="features"></a>

## Features

- [x] **Command-Driven**: Full `/halo` command tree with tab completion — attach, detach, configure, inspect, and list halos, all from chat.
- [x] **Smooth Damping Physics**: Frame-rate-independent exponential damping for both position and rotation. Configurable linear/angular follow speed, max distance, and hard-clamping limits.
- [x] **Persistent**: Halos survive world reloads and server restarts via entity NBT and world persistent state. Automatic restoration on entity load.
- [x] **Teleport-Aware**: When the entity teleports (or crosses dimensions), the halo instantly snaps to the new position — no sliding across the map.
- [x] **Glow-Capable**: Each halo can have an optional additive-blended glow layer with pulsing alpha animation.
- [x] **Animated**: Halo definitions support position animation curves (oscillate, linear, constant) and rotation animation curves (continuous spin, etc.).
- [x] **Runtime Configurable**: Damping factor, max distance, scale, position offset, and rotation offset can all be changed live with `/halo config`.
- [x] **Resource-Pack Friendly**: Halo definitions are JSON files loaded from `assets/<namespace>/halo_definitions/`. Add new halos via resource packs or data packs and run `/reload`.
- [x] **Multi-Layer Shapes**: Beyond single billboards, halos can use `MultiBillboardShape` for layered quad effects.
- [x] **Distance Culling**: Halos beyond 1000 blocks from the camera are automatically skipped for performance.

<a id="installation"></a>

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.20.1.
2. Download the [Fabric API](https://modrinth.com/mod/fabric-api) (0.92.0+ for 1.20.1).
3. Download the latest **Halo** mod JAR from the [Releases](https://github.com/AzusaKe/Halo/releases) page.
4. Place both JARs into the `mods` folder of your Minecraft installation directory.
5. Launch Minecraft with the Fabric profile.

> **Multiplayer**: Install the mod on both the server and all clients that need to see halos. The halo state (which entity has which halo) is server-authoritative; rendering is client-side.

<a id="usage"></a>

## Usage

<a id="commands"></a>

### Commands

All commands require permission level 2 (operator). Use `/halo` with tab completion to explore available subcommands.

| Command | Description |
|---|---|
| `/halo list` | List all loaded halo definitions |
| `/halo dump` | Detailed dump of definitions with shape, animation, and damping info |
| `/halo show <entity> <definition>` | Attach a halo to an entity |
| `/halo hide <entity>` | Remove a halo from an entity |
| `/halo active` | List all entities currently wearing a halo |
| `/halo inspect <entity>` | Detailed per-entity halo runtime status |
| `/halo config linear-damping <0-1>` | Set linear follow speed (0 = frozen, 1 = instant) |
| `/halo config angular-damping <0-1>` | Set angular follow speed |
| `/halo config max-linear-distance <n>` | Set max distance before hard-clamping (blocks) |
| `/halo config max-angular-degrees <n>` | Set max angular deviation (degrees) |
| `/halo config scale <0.1-5.0>` | Set uniform scale multiplier |
| `/halo save` | Sync halo data to world persistence and trigger save-all |
| `/halo debug <true/false>` | Toggle teleport/snap debug logging to chat |
| `/halo reload` | Hint to use `/reload` to reload halo definitions |
| `/halo config reload` | Reload halo definitions from resource/data packs |

**Examples:**

```mcfunction
# Attach the default ring halo to yourself
/halo show @s ring_default

# Attach a halo to another player
/halo show Azusa_Ke ring_default

# Remove your halo
/halo hide @s

# Make halos respond faster
/halo config linear-damping 0.3
/halo config angular-damping 0.2

# Make the halo render larger
/halo config scale 1.5
```

<a id="custom-halo-definitions"></a>

### Custom Halo Definitions

Halo definitions are JSON files placed under `assets/<namespace>/halo_definitions/` (resource pack) or `data/<namespace>/halo_definitions/` (data pack).

**Example definition** (`ring_default.json`):

```json
{
  "id": "halo:ring_default",
  "shape": {
    "type": "billboard",
    "texture": "halo:textures/halo/ring.png",
    "size": [0.5, 0.5]
  },
  "animation": {
    "positionCurves": [
      {"type": "oscillate", "axis": "Y", "amplitude": 0.05, "frequency": 1.0}
    ],
    "rotationCurves": [
      {"type": "spin", "axis": "Z", "speed": 30.0}
    ]
  },
  "positioning": {
    "offset": [0.0, 0.2, 0.5],
    "scale": 1.0
  },
  "damping": {
    "linearFactor": 0.15,
    "angularFactor": 0.1,
    "maxLinearDistance": 1.0,
    "maxAngularDegrees": 180.0
  }
}
```

| Field | Description |
|---|---|
| `id` | Unique identifier in `namespace:name` format |
| `shape.type` | `billboard` (single quad) or `multi_billboard` (layered quads) |
| `shape.texture` | Texture path relative to `assets/` |
| `shape.size` | `[width, height]` in blocks |
| `animation.positionCurves` | Array of position animations (`oscillate`, `linear`, `constant`) |
| `animation.rotationCurves` | Array of rotation animations (`spin`, etc.) |
| `positioning.offset` | `[X, Y, Z]` offset from entity head in blocks |
| `positioning.scale` | Default scale multiplier |
| `damping.linearFactor` | Linear interpolation speed per tick at 20 TPS (0 = frozen, 1 = instant) |
| `damping.angularFactor` | Angular interpolation speed (same range) |
| `damping.maxLinearDistance` | Maximum distance in blocks before hard-clamping |
| `damping.maxAngularDegrees` | Maximum angular deviation in degrees |

> After adding or modifying halo definitions, run `/reload` (or `/halo config reload`) to reload them.

<a id="building-from-source"></a>

## Building from Source

<a id="prerequisites"></a>

### Prerequisites

- **JDK 17** — required for Minecraft 1.20.1
- An internet connection (Gradle downloads dependencies from Maven repositories)

<a id="build"></a>

### Build

```bash
git clone https://github.com/AzusaKe/Halo.git
cd Halo
./gradlew build
```

The compiled JAR will be at `build/libs/halo-0.1.0.jar`.

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

# Launch a dedicated server with the mod
./gradlew runServer
```

<a id="project-structure"></a>

## Project Structure

```
src/main/
  java/com/example/halo/
    HaloMod.java              — Mod initializer (server-side entry point)
    HaloModClient.java         — Client initializer (client-side entry point)
    animation/                 — Animation curves (Linear, Oscillate, Constant)
    command/                   — /halo Brigadier command tree
    config/                    — Runtime HaloConfig (damping, scale, offsets)
    data/                      — HaloDefinition, HaloInstance, HaloEntityData, etc.
    json/                      — JSON deserializer and resource loader
    lifecycle/                 — EntityHaloTracker, HaloWorldSaveData, event handlers
    manager/                   — HaloManager (singleton, owns all active halos)
    mixin/                     — EntityTeleportMixin, LivingEntityDataMixin
    physics/                   — DampingPhysics, HaloDampingState, HaloTickHandler
    render/                    — HaloRenderer, HaloClientManager, HaloRenderListener
    server/                    — HaloServerEvents, ServerTickHandler
    shape/                     — BillboardShape, MultiBillboardShape, GlowLayer
  resources/
    fabric.mod.json            — Mod metadata (entrypoints, mixins, dependencies)
    halo.mixins.json            — Mixin configuration
    assets/halo/
      halo_definitions/        — JSON halo definition files
      textures/halo/           — Halo and glow textures
```

<a id="contributing"></a>

## Contributing

Contributions to Halo are welcome! If you have ideas, suggestions, or want to report a bug, please open an issue on the [GitHub repository](https://github.com/AzusaKe/Halo). If you want to contribute code, please fork the repository and submit a pull request.

- **Environment**: Minecraft 1.20.1 + Fabric Loader 0.15+
- **IDE**: IntelliJ IDEA (recommended, with Minecraft Development plugin) or VS Code

<a id="license"></a>

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
