<h1>
  <img src="./src/main/resources/assets/halo/textures/halo/ring.png" alt="cover" height="40" style="vertical-align:middle;margin-right:12px">
  Halo Mod
</h1>

> This branch is the **26.1 Fabric release**, developed on 26.1.2, using HaloCore 2.4.1 and adapter.2. YSM and Connector are excluded. User network and reported rendering checks passed; the full three-version runtime matrix remains pending. See the [current migration record](docs/26.1-fabric-migration.md) for tested scope; the feature documentation below includes inherited baseline behavior and is not an acceptance claim.

![License](https://img.shields.io/badge/license-MIT-blue.svg)
![MC Version](https://img.shields.io/badge/Minecraft-26.1-green.svg)
![Mod Loader](https://img.shields.io/badge/Mod%20Loader-Fabric-orange.svg)

English | [中文](README_ZH.md)

This branch targets **Minecraft 26.1 Fabric**, developed on **26.1.2**. The source version is **2.4.1+adapter.2**, using HaloCore **2.4.1**. Players install one Halo JAR. Flash branches remain frozen. See the [migration record](docs/26.1-fabric-migration.md) and [development guide](DEVELOPMENT.md).

2.4.0 adds the loader-neutral server ownership source API. Accessory and integration mods can submit halo candidates by entity UUID; Halo selects one winner by source priority, while the built-in commands and Halo Scepter continue to edit only the persisted `halo:world_data` source. Priorities are stored in `halo_source_priorities.json` and can be changed without restarting through `/halo priority list|set|reload`. Integrations must also provide the referenced definitions and visual assets to rendering clients. See the [API guide](docs/en/API.md#6-server-ownership-source-api).

Billboard/ring rendering defaults to `compatibility`, with adjacent batching and shared geometry.
Use `/halo renderer` to query, or `/halo renderer compatibility|cached` to select and save a client-only
backend. Switching applies to the world and inventory preview at the next frame, without resetting
animation or physics. OBJ meshes are unaffected. See the [verification record](docs/render-optimization-verification.md).

2.3.0 extends core's loader-neutral anchor API v2 with preview submission; EMF uses that same public
interface. Providers only capture heads; Halo owns preview physics and drawing. See the
[provider API](docs/en/API.md#preview-anchor-provider-api).

Vanilla player previews now display the equipped halo, including survival and creative inventories.
The preview uses the actual head as its physics anchor and shares all three primitives and visual animation
with the world halo. `playerPreviewHaloEnabled` defaults to `true` in the client configuration (restart
after editing). EMF capture is gated by the target ABI and requires a resource pack that replaces the player model; custom-model runtime verification is pending. See the [preview API](docs/en/API.md#preview-host-integration) and [implementation/verification record](docs/player-preview.md).
`playerPreviewHaloPhysicsEnabled` defaults to `true`, using world-style physics with independent state
per preview. Set it to `false` for rigid head following; restart after editing.

For contributors and coding agents, the [development guide](DEVELOPMENT.md) covers feature development, debugging, porting to other game branches, and the two-repository commit and release workflow.

## Table of Contents

- [Table of Contents](#table-of-contents)
- [Introduction](#introduction)
- [Features](#features)
- [Feature Status](#feature-status)
- [Installation](#installation)
  - [NeoForge / Forge](#neoforge--forge)
- [Usage](#usage)
  - [Commands](#commands)
  - [YSM Compatibility](#ysm-compatibility)
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
This source branch builds for Minecraft 26.1.2 with Fabric. Other game/loader versions use their own adapter branches.


> **The project is in early development. Functionality and performance may be unstable. We welcome issues and pull requests to help improve it!**

> Halos use frame-rate-independent discrete position tracking with configurable follow speed and maximum distance clamping. Position resets after entity teleportation.

<a id="features"></a>

## Features

- [x] **Command-Driven**: Full `/halo` command tree with tab completion — attach, remove, configure, inspect, and list halos all from chat. See details below.
- [x] **Smooth Damping Physics**: Frame-rate-independent exponential damping for position and rotation. Configurable linear/angular follow speed and maximum distance clamping.
- [x] **Persistent**: Halos survive world reloads and server restarts through entity NBT and world persistent state. Automatically restored on entity load.
- [x] **Teleport-Aware**: When an entity teleports (or crosses dimensions), the halo instantly jumps to the new position — no sliding across the map.
- [x] **Glow Effects**: The `animation.glow` channel drives each primitive's own self-illumination brightness (fullbright); set `glowing: false` on a group to make its primitives follow ambient light instead.
- [ ] **LabPBR compatibility**: The 26.1 entity material path and IterationRP smooth-normal patch have run successfully. Per-channel normal/specular/emission and POM acceptance remains pending; see the [migration record](docs/26.1-fabric-migration.md).
- [x] **Animation Support**: Halo definitions support position, rotation, scale, alpha, and glow animation channels, plus independent startup and shutdown transition timelines.
- [x] **Runtime Debug Configuration**: `/halo config` provides session-scoped overrides for damping, distance limits, angular momentum, and uniform scale. It is intended for personal tuning and debugging: local mode applies it to the current client, integrated singleplayer bridges it to the paired client, and dedicated servers do not persist or distribute these values. Permanent placement changes, including position and rotation offsets, belong in each halo definition.
- [x] **Resource Pack Friendly**: Rendering clients load definitions and visual assets from `assets/<namespace>/halo_definitions/` in resource packs. A server data pack may register definition JSON under `data/<namespace>/halo_definitions/` for server-side listing and selection, but Halo does not distribute that JSON, textures, or models; every rendering client still needs the matching resource pack. Run `/reload` after changing either source.
- [x] **Hierarchical Multi-Layer Shapes**: A definition can contain multiple primitives per group and nested child groups with inherited transforms and animation. Legacy multi-billboard definitions remain loadable through compatibility parsing.
- [x] **Distance Culling**: World halos whose entities are more than 256 blocks from the camera are skipped before physics and geometry submission. A separate ±1000-block camera-relative guard remains as a numeric safety check.
- [x] **Multiplayer Support**: Authoritative halo assignments use a full snapshot on join and incremental attach/remove updates, so connected clients see ownership changes in real time. Clients render those assignments from their own locally installed definitions and assets; personal `/halo config` debug overrides are not part of this synchronization.
- [x] **Halo Scepter**: The craftable Halo Scepter opens a searchable selector for an entity, applies or removes the persisted world-data halo with server permission and range checks, and supports self-targeting while sneaking.
- [x] **Multiple Ownership Sources**: External server mods can register candidates through API v2. Halo arbitrates one visible winner by configurable source priority without commands or the Scepter overwriting higher-priority external state.

## Feature Status

- [x] **Visible in Inventory**: Equipped halos render in survival and creative inventory player previews, with optional independent preview physics and vanilla/EMF head capture.
- [x] **More Animations**: Animation-driven `alpha` (opacity) and `glow` intensity channels, scale animations, and "intro animations"
- [x] **OBJ Mesh Primitives**: Three-axis size, authored origins, grayscale alpha masks and U/V animation; see the [mesh authoring guide](docs/en/mesh.md).
- [ ] **More Layer Fields**: Will add `thickness`, using sprite extrusion to give billboards depth — may affect performance with larger textures
- [ ] **Improved Self-Illumination**: Better compatibility with more shaders and stronger visual quality
- [ ] **Better Entity & Pose Adaptation**: Halo display positions and animations currently have issues on some entities — pending fixes
- [x] **Singleplayer `/halo hide` Shutdown Animation**: Local and integrated-singleplayer removal enters the normal shutdown transition; an explicit shutdown timeline is used when present, otherwise the startup timeline is reversed.
- [ ] Other bug fixes — issues are welcome

<a id="installation"></a>

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.1.2.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for your Minecraft version.
3. Download the latest **Halo** mod JAR file from the [Releases](https://github.com/AzusaKe/Halo/releases) page.
4. Place both JAR files into the `mods` folder in your Minecraft installation directory.
5. Launch Minecraft with the Fabric profile.

### NeoForge / Forge

This mod is natively built for Fabric and does not ship a native Forge or NeoForge adapter. The historical 1.20.1 build was exercised through Sinytra Connector, but that result does not carry over to this 26.1.2 adapter. Forge/NeoForge 26.1.2 is currently unverified and is not part of this branch's compatibility claim.

Use the native Fabric build for this target; Connector is excluded from this migration.

<a id="usage"></a>

## Usage

<a id="commands"></a>

### Commands

Server commands require permission level 2 (operator) by default. `/halo renderer` is client-only and requires no server permission. The required level can be changed in `config/halo-azusake/halo_mod_config.json` (0–4; restart the server/game for changes to take effect). This mod-level config file is separate from the session-scoped `/halo config` debug overrides. Those overrides affect the current local runtime or integrated-singleplayer client and are neither persisted nor synchronized by a dedicated server. External ownership priorities live in `halo_source_priorities.json` and can be inspected or changed at runtime with `/halo priority list|set|reload`. Use `/halo` with tab completion to explore available subcommands.

| Command                                            | Description                                                                      |
| -------------------------------------------------- | -------------------------------------------------------------------------------- |
| `/halo list`                                       | List all loaded halo definitions                                                 |
| `/halo dump`                                       | Detailed output of halo definitions including shape, animation, and damping info |
| `/halo show <entity> <definition>`                 | Attach a halo to an entity                                                       |
| `/halo hide <entity>`                              | Remove a halo from an entity                                                     |
| `/halo active`                                     | List all entities currently wearing a halo                                       |
| `/halo inspect <entity>`                           | View detailed runtime status of an entity's halo                                 |
| `/halo config linear-damping <0-1>`                | Debug override for linear follow speed (0 = no follow, 1 = instant follow)       |
| `/halo config angular-damping <0-1>`               | Debug override for angular follow speed                                          |
| `/halo config max-linear-distance <n>`             | Debug override for maximum distance before hard clamping (blocks)                |
| `/halo config max-angular-degrees <n>`             | Debug override for maximum angular deviation (degrees)                           |
| `/halo config allow-angular-momentum <true/false>` | Debug override for the angular-momentum inertia toggle                           |
| `/halo config angular-momentum-factor <0-1>`       | Debug override for angular-momentum damping (0 = frozen, 1 = no inertia)          |
| `/halo config max-angular-momentum-degrees <n>`    | Debug override for maximum angular-momentum deviation (degrees)                  |
| `/halo config scale <0.1+>`                        | Debug override for the uniform scale multiplier                                  |
| `/halo priority list`                              | List registered ownership sources and effective priorities                       |
| `/halo priority set <source> <priority>`           | Persist and immediately apply a signed 32-bit source priority                    |
| `/halo priority reload`                            | Reload source priorities while retaining the last valid values on failure        |
| `/halo renderer [compatibility\|cached]`           | Query or persist the client-only billboard/ring rendering backend                |
| `/halo save`                                       | Sync halo data to world persistence and trigger save-all                         |
| `/halo debug <true/false>`                         | Toggle teleport/snap debug logging to chat                                       |
| `/halo reload`                                     | Hint to use `/reload` to reload halo definitions                                 |

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

<a id="ysm-compatibility"></a>

### YSM Compatibility

YSM is excluded from this platform migration. The public world and preview anchor API remains available.

<a id="custom-halo-definitions"></a>

### Custom Halo Definitions

Rendering definitions are JSON files stored in `assets/<namespace>/halo_definitions/` in resource packs. Servers can additionally read definition JSON from `data/<namespace>/halo_definitions/` in data packs so IDs are available to server-side listing, completion, and selection. The server synchronizes only ownership identifiers; it does not send definition JSON, textures, or OBJ files. Every rendering client therefore needs a resource pack containing each definition and its visual assets.

> **For a full step-by-step tutorial and the complete field reference, see the docs:**
> [Quickstart](docs/en/quickstart.md) · [Field Reference](docs/en/reference.md)

**Definition Example** (`ring_default.json`, simplified — see [full version](src/main/resources/assets/halo/halo_definitions/ring_default.json)):

```json
{
  "id": "halo:ring_default",
  "orientation_mode": "locked",
  "allow_angular_momentum": true,
  "layers": [
    {
      "position": [0.0, -0.001, 0.0],
      "rotation": [0.0, 0.0, 0.0],
      "scale": 1.5,
      "animation": {
        "offset": {
          "y": [{ "function": "sin", "A": 0.01, "omega": 0.5 }]
        }
      },
      "primitive": {
        "type": "billboard",
        "texture": "halo:textures/halo/ring_03.png",
        "size": [0.5, 0.5]
      }
    },
    {
      "position": [0.0, 0.0, 0.0],
      "rotation": [0.0, 0.0, 0.0],
      "scale": 1.5,
      "animation": {
        "offset": {
          "y": [{ "function": "sin", "A": 0.01, "omega": 0.5 }]
        },
        "rotation": {
          "yaw": [{ "function": "linear", "start": 0, "speed": -0.008333 }]
        }
      },
      "primitive": {
        "type": "billboard",
        "texture": "halo:textures/halo/ring_00.png",
        "size": [0.5, 0.5]
      }
    },
    {
      "position": [0.0, 0.001, 0.0],
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
        "texture": "halo:textures/halo/ring_01.png",
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
    "maxAngularDegrees": 180.0,
    "angularMomentumFactor": 0.3,
    "maxAngularMomentumDegrees": 45.0
  }
}
```

| Field                               | Description                                                                                                                   |
| ----------------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `id`                                | Unique identifier in format `namespace:name`                                                                                  |
| `orientation_mode`                  | `locked`, `free`, or `sync` — how the halo orients relative to the entity head                                                |
| `allow_angular_momentum`            | When `true`, adds angular momentum inertia to orientation (`locked`/`free` only)                                              |
| `hide_on_sleep`                     | When `true`, halo stops rendering while the entity is sleeping (default `false`)                                              |
| `display_in_invisible`              | When `true`, halo continues rendering while the entity is invisible (default `false`)                                         |
| `layers`                            | Array of groups; each group can contain multiple primitives and nested child groups, sharing a common transform and animation |
| `layers[].position`                 | `[X, Y, Z]` offset of this group relative to the parent group or anchor frame (blocks)                                        |
| `layers[].rotation`                 | `[yaw, pitch, roll]` Euler rotation of this group (degrees)                                                                   |
| `layers[].scale`                    | Per-group scale multiplier                                                                                                    |
| `layers[].animation`                | Per-group `offset` / `rotation` animation curves (see reference)                                                              |
| `layers[].primitives`               | Array of rendering primitives within this group (see below)                                                                   |
| `layers[].primitive`                | Backward-compatible single primitive object (equivalent to `primitives: [...]`)                                               |
| `layers[].children`                 | Optional array of nested child groups that inherit this group's transform                                                     |
| `primitive.type`                    | `billboard` (textured quad), `ring` (cylindrical ring), or `mesh` (OBJ model)                                                  |
| `primitive.texture`                 | Texture path, e.g. `halo:textures/halo/ring_00.png`                                                                           |
| `primitive.model`                   | Mesh only: OBJ model resource path                                                                                            |
| `primitive.inner_texture`           | Ring only: inner surface texture (optional; defaults to `texture` if omitted)                                                 |
| `layers[].primitive.size`           | `billboard`: `[width, depth]`; `ring`: `[radius, cylinder_width]`; `mesh`: `[x, y, z]` bounds in blocks                       |
| `layers[].primitive.segments`       | Ring only: polygon segment count (default 32)                                                                                 |
| `positioning.offset`                | `[X, Y, Z]` offset relative to entity head (blocks)                                                                           |
| `positioning.scale`                 | Default scale multiplier                                                                                                      |
| `damping.linearFactor`              | Linear interpolation speed per tick at 20 TPS (0 = no follow, 1 = instant follow)                                             |
| `damping.angularFactor`             | Angular interpolation speed (same range as above)                                                                             |
| `damping.maxLinearDistance`         | Hard clamp maximum distance (blocks)                                                                                          |
| `damping.maxAngularDegrees`         | Maximum angular deviation (degrees)                                                                                           |
| `damping.angularMomentumFactor`     | Angular momentum damping factor (0 = frozen, 1 = no inertia)                                                                  |
| `damping.maxAngularMomentumDegrees` | Maximum angular momentum deviation (degrees)                                                                                  |

> After adding or modifying halo definitions, run `/reload` to reload.

<a id="building-from-source"></a>

## Building from Source

<a id="prerequisites"></a>

### Prerequisites

- **JDK 25** — Required for Minecraft 26.1.2 (HaloCore remains Java 17 compatible)
- Internet connection (Gradle downloads dependencies from Maven repositories)

<a id="build"></a>

### Build

```bash
git clone --branch 26.1-fabric --recurse-submodules https://github.com/AzusaKe/Halo.git
cd Halo
./gradlew build
```

The compiled JAR is under `build/libs/`; this source version builds `halo-26.1.2-fabric-2.4.1+adapter.2.jar`. Builds from modified or unpinned worktrees carry a `.dev` suffix. See the [release checks](DEVELOPMENT.md).

<a id="run-tests"></a>

### Run Tests

```bash
./gradlew check
```

<a id="run-client--server"></a>

### Run Client / Server

```bash
# Launch Minecraft client with the mod
./gradlew runClient

# Launch dedicated server with the mod
./gradlew runServer --console=plain
```

The client uses `run/`, the second client uses `run2/`, and this branch's dedicated server uses
`runServer/26.1.2-fabric/` for its own logs, configuration and world. On first launch, follow the
instructions for `eula.txt` in that directory and configure the port in `server.properties`.
Development usernames require an offline-mode test server; bind `server-ip` to `127.0.0.1` for local
testing. Enter `stop` in the console to save and shut down normally.

Do not reuse worlds from another game version here; `--safeMode` does not downgrade saves. The server's `Done (...)!` log confirms startup;
`BUILD SUCCESSFUL` only means the Gradle launch task exited normally.

<a id="project-structure"></a>

## Project Structure

```text
core/                         # independent HaloCore repository (git submodule)
  src/main/java/              # definitions, ownership, anchors, physics, animation, geometry
  src/test/                   # standalone contracts, replay and numeric fixtures
src/main/java/network/azusake/halo/
  platform/                   # core value conversions and explicit integrated-server bridge
  client/, command/, item/    # UI, Brigadier and item inputs
  json/, config/, lifecycle/  # resource, file and world/NBT adapters
  network/                    # Fabric channels and existing byte codecs
  physics/, compat/, mixin/   # game/model capture, EMF/Iris integration
  render/                     # frame assembly and GPU batch submission
src/main/resources/           # built-in definitions, textures, recipes and translations
src/testFixtures/             # frozen anchor API v2 ABI consumer
```

<a id="contributing"></a>

## Contributing

Contributions to Halo are welcome! If you have ideas, suggestions, or want to report a bug, please submit an issue on the [GitHub repository](https://github.com/AzusaKe/Halo). If you want to contribute code, please fork the repository and submit a pull request.

- **Development Environment**: Minecraft 26.1.2 + Fabric Loader 0.19.3+ + JDK 25
- **IDE**: Recommended IntelliJ IDEA (with Minecraft Development plugin) or VS Code

<a id="license"></a>

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
