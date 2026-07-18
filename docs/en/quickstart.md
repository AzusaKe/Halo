# Quickstart: Create Your First Halo Resource Pack

Welcome! This tutorial will walk you through creating a custom halo step by step — no coding required, just an image and a JSON file.

When you're done, your halo will follow an entity's head movements in-game with smooth damping physics.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Step 1: Create the Resource Pack Folder](#step-1-create-the-resource-pack-folder)
- [Step 2: Write the Halo Definition File](#step-2-write-the-halo-definition-file)
- [Step 3: Prepare the Texture Image](#step-3-prepare-the-texture-image)
- [Step 4: Install and Use](#step-4-install-and-use)
- [Step 5: Debug and Iterate](#step-5-debug-and-iterate)
- [Next Steps](#next-steps)

---

## Prerequisites

You will need:

- A **text editor** (e.g., VS Code, Notepad++)
- An **image editor** (e.g., Photoshop, GIMP, Aseprite) for creating PNG textures. For original halo designs, a **vector drawing application** (e.g., Adobe Illustrator, Inkscape) is also recommended — vector graphics scale cleanly and make it easier to draw precise geometric shapes.
- Minecraft 1.20.1 (Fabric) with the Halo mod installed

---

## Step 1: Create the Resource Pack Folder

First, create a folder as the root of your resource pack. We'll use `yourhalo_pack` as the example name:

```
yourhalo_pack/
```

Create a `pack.mcmeta` file in the root directory with the following content:

```json
{
  "pack": {
    "pack_format": 15,
    "description": "My First Halo Resource Pack"
  }
}
```

> **Note**: `pack_format: 15` corresponds to Minecraft 1.20.x. The `description` can be anything you like — it will appear in the in-game resource pack list.

---

## Step 2: Write the Halo Definition File

The halo definition file tells the mod what the halo looks like, where it sits, and how it moves.

### Create the Directory and File

Create the following path inside your resource pack:

```
yourhalo_pack/
├── pack.mcmeta
└── assets/
    └── halo/
        └── halo_definitions/
            └── yourhalo.json
```

### Write the JSON

Put the following content into `yourhalo.json`:

```json
{
  "id": "halo:yourhalo",
  "orientation_mode": "sync",
  "layers": [
    {
      "position": [0.0, 0.0, 0.0],
      "rotation": [0.0, 0.0, 0.0],
      "scale": 1.0,
      "primitive": {
        "type": "billboard",
        "texture": "halo:textures/halo/yourhalo.png",
        "size": [0.5, 0.5]
      }
    }
  ],
  "animation": {
    "offset": {
      "y": [{ "function": "sin", "A": 0.01, "omega": 0.5 }]
    }
  },
  "hide_on_sleep": false,
  "positioning": {
    "offset": [0.0, 0.4, 0.35],
    "scale": 1.5
  },
  "damping": {
    "linearFactor": 0.5,
    "angularFactor": 0.1,
    "maxLinearDistance": 0.5,
    "maxAngularDegrees": 180.0
  }
}
```

### Field Reference

Here is what each field in this JSON means:

| Field | Description |
|------|------|
| `id` | The unique identifier for the halo. Format: `namespace:name`. The namespace maps to a folder under `assets/` — e.g. `halo:yourhalo` means the definition file lives under `assets/halo/`. You can use your own namespace (e.g. `mypack:myhalo`), in which case the definition file should be placed under `assets/mypack/`. You'll use this ID with the `/halo show` command. |
| `orientation_mode` | The halo's orientation mode. `"sync"` means the halo follows the entity's head rotation — when the player looks up, the halo tilts up; when the player turns, the halo turns. This tutorial uses `sync` as the example. (Inspired by the halo-following style in Blue Archive.) |
| `layers` | Array of layers. Each halo can be composed of multiple layers stacked together. Layers determine their front-to-back spatial relationship through their `position` values, not their array order. Here we only use one layer. |
| `layers[0].position` | This layer's position in the halo's local space `[X, Y, Z]` (in blocks). `[0, 0, 0]` represents the halo's **origin** — this origin will be aligned with the position computed by damping physics. The layer's actual position in space = damped follow position + the offset defined here. |
| `layers[0].rotation` | This layer's initial rotation `[pitch, yaw, roll]` (in degrees). `[0, 0, 0]` means no rotation. |
| `layers[0].scale` | This layer's scale multiplier. `1.0` is the original size. |
| `layers[0].primitive` | The rendering primitive for this layer. `"billboard"` is a flat quad with no thickness (named for its lack of depth) — this is currently the only available primitive type. |
| `layers[0].primitive.texture` | The texture path. Format: `namespace:textures/halo/filename.png`. This path is relative to `assets/`, where the **namespace** corresponds to a folder name under `assets/`. For example, in `halo:textures/halo/yourhalo.png`, `halo` is the namespace and maps to the `assets/halo/` folder. If you use your own namespace (e.g. `mypack`), place files under `assets/mypack/` and write the path as `mypack:textures/halo/yourhalo.png`. |
| `layers[0].primitive.size` | The quad's dimensions `[width, height]` (in blocks). `[0.5, 0.5]` is a 0.5×0.5-block square. |
| `animation` | The halo's overall animation. Here we've added a Y-axis sinusoidal bobbing effect — the halo gently floats up and down, giving it a lively feel. |
| `animation.offset.y` | Y-axis offset animation. `A: 0.01` is the amplitude (0.01 blocks), `omega: 0.5` is the frequency. Larger values produce more pronounced bobbing. |
| `hide_on_sleep` | Whether to hide the halo while the entity is sleeping. `true` = stop rendering during sleep, auto-resume upon waking; `false` (default) = always render. |
| `positioning.offset` | The halo's overall position offset relative to the entity's head `[X, Y, Z]`. `[0, 0.4, 0.35]` means 0.4 blocks above the head and 0.35 blocks behind. |
| `positioning.scale` | The halo's overall scale multiplier. `1.5` scales it up by 50%. |
| `damping` | Physics follow parameters that control how smoothly the halo tracks the entity. |
| `damping.linearFactor` | Linear follow speed (0 = no follow, 1 = instant follow). `0.5` provides a moderate smooth-tracking feel. |
| `damping.angularFactor` | Angular follow speed. `0.1` is relatively slow — suitable for a decorative item like a halo that doesn't need to snap to rotation. |
| `damping.maxLinearDistance` | Maximum follow distance (in blocks). When the halo reaches this distance from the entity, it is **clamped** at this value — it won't drift further and will gradually rebound as the follow speed slows. |
| `damping.maxAngularDegrees` | Maximum angular deviation (in degrees). When the halo's angle relative to the entity reaches this value, it is **clamped** at this angle — it won't rotate further and will gradually rebound as the follow speed slows. |

---

## Step 3: Prepare the Texture Image

### Create the Texture

Create the texture file inside your resource pack:

```
yourhalo_pack/
├── pack.mcmeta
└── assets/
    └── halo/
        ├── halo_definitions/
        │   └── yourhalo.json
        └── textures/
            └── halo/
                └── yourhalo.png
```

### Texture Requirements

- **Format**: PNG with transparency (RGBA)
- **Size**: Square recommended, e.g. 512×512 or 714×714 pixels. Power-of-two dimensions are not required.
- **Content**: Draw your halo design on a transparent background. Areas outside the halo should remain transparent — only what you draw will be visible.

> **Tip**: The halo texture is drawn as the **front** appearance of the halo. Since halo layers are rendered on flat quads (billboards), their facing direction is determined by `orientation_mode` — in `sync` mode they follow the entity's head rotation. You can refer to the mod's built-in textures (at `assets/halo/textures/halo/`) for style inspiration.

### ⚠️ Texture Orientation (Important)

Each halo layer is rendered on a flat quad (billboard) with no thickness. When a layer's `position` and `rotation` are both set to `[0, 0, 0]`, and the halo is mounted at some `offset` from the entity's head:

**Basic direction mapping**:
- **Up** in an image viewer = **up** for the player in-game (world Y+, toward the sky) — **never flips**
- **Down** in an image viewer = **down** for the player in-game (world Y-, toward the ground) — **never flips**

**Viewing direction effects**:

- **Looking from the player's head center outward** (like spectator-mode free camera, positioned at the player's head looking out): the texture appears **exactly correct** — identical to what you see in your image editor
- **Looking from outside toward the player** (normal gameplay perspective, looking at the player from the front): the texture appears **flipped left-to-right** (horizontally mirrored), but the up-down orientation stays the same

**Summary**: The halo texture is **correct when viewed from the inside** (looking outward from the player's head, it matches your image editor exactly), and **flipped left-to-right when viewed from the outside** (looking at the player from the front). The up-down orientation never changes.

> **Tip**: If the text description isn't clear enough, check out the built-in example halo definitions and their corresponding textures in the open-source repository [AzusaKe/Halo](https://github.com/AzusaKe/Halo) — comparing them side by side helps build intuition. You can also look at the ready-made Blue Archive character halo resource packs in [Ba-Halo-Definition](https://github.com/AzusaKe/Ba-Halo-Definition) for real-world examples.

---

## Step 4: Install and Use

### Install the Resource Pack

1. Place the `yourhalo_pack` folder (or the compressed `.zip` file) into Minecraft's `resourcepacks` directory
   - If you're not sure where the `resourcepacks` directory is: launch the game, then go to **Options → Resource Packs → Open Pack Folder** to open it directly
2. Launch the game (if already running, restart so Minecraft can detect the new pack), go to **Options → Resource Packs**, find "My First Halo Resource Pack", and click the arrow to move it to the "Enabled" list on the right
3. Click "Done" to apply

> **Tip**: Minecraft supports both folder-form and `.zip` resource packs — you can drop the entire folder directly into `resourcepacks`, or use a compressed `.zip` file. `.zip` is better for sharing and transferring. **Important**: When zipping, make sure `pack.mcmeta` is at the top level of the archive — opening the zip should directly show `pack.mcmeta` and the `assets/` folder, not another nested folder inside.
>
> **Convenience Tool**: If you don't want to manually create the zip, use [Halo-Packing-Tool](https://github.com/AzusaKe/Halo-Packing-Tool) to bundle JSONs and PNGs into resource packs in one step.

### Load the Halo

After enabling the resource pack, press `T` in-game to open chat and enter:

```mcfunction
/reload
```

This command tells the mod to reload all halo definitions. You should see a confirmation that the new definition was loaded.

Then, equip the halo on yourself:

```mcfunction
/halo show @s halo:yourhalo
```

> `@s` is a Minecraft target selector meaning "self". `yourhalo` is the name part of the ID you defined in the JSON. **Note**: typing `yourhalo` directly will always be resolved to `minecraft:yourhalo` (which is incorrect). Use **tab completion** after typing `/halo show @s ` to select the correct `halo:yourhalo` entry.

If everything went well, you should see the halo floating above your head!

---

## Step 5: Debug and Iterate

### Useful Debug Commands

| Command | Purpose |
|------|------|
| `/halo list` | List all loaded halo definitions. Confirm your `halo:yourhalo` appears in the list. |
| `/halo dump` | Output detailed information about all halo definitions (including layers, animations, and damping parameters). |
| `/halo active` | List all entities currently wearing a halo. |
| `/halo inspect @s` | View the runtime status of the halo on yourself. *(Note: currently broken for unknown reasons — may not produce output.)* |
| `/halo hide @s` | Remove the halo from yourself. |

### Editing and Hot-Reload

Halo definitions support hot-reloading in-game. No need to restart after editing JSON:

1. Edit `yourhalo.json` and save
2. Run `/reload` in-game
3. The halo immediately renders with the new configuration

This means you can tweak parameters and see the results in real time — very convenient.

### Troubleshooting

**Halo not showing up?**
- Check if `/halo list` includes your halo ID. If not, verify the JSON file path is correct (`assets/halo/halo_definitions/yourhalo.json`)
- Check that the texture path matches the `texture` field in your JSON
- Check that the texture is a valid PNG file

**Halo position is off?**
- Adjust the Y value in `positioning.offset` to change height, and the Z value to change front-to-back distance
- Adjust `positioning.scale` to change the overall size

**Halo follows too slowly / too snappily?**
- Increase `damping.linearFactor` (closer to 1.0) for faster following
- Decrease `damping.linearFactor` (closer to 0) for a floatier feel

---

## Next Steps

Congratulations! You've successfully created your first custom halo. From here, you can try:

- **Adding multiple layers**: Append more layers to the `layers` array, each with different textures and Y offsets, to create a layered halo
- **Adding rotation animations**: Use `animation.rotation` on a layer to make it spin continuously
- **Tweaking the bobbing animation**: Adjust `A` (amplitude) and `omega` (frequency) in `animation.offset` to change the floating effect
- **Trying other orientation modes**: Change `orientation_mode` to `"locked"` or `"free"`:
  - `locked`: The halo always points toward the player's head center, and uses the head's spherical poles to keep it pointing stably like a compass — it won't rotate freely around its normal axis
  - `free`: The halo also points toward the player's head center, but its rotation around its normal axis is uncontrolled
  - `sync` (the mode used in this tutorial): The initial pose is determined by the first frame of `locked` mode, then synchronizes rotation with the player's head

> A detailed field reference manual is in the works — it will cover every available field, animation function, and common patterns. Stay tuned!
>
> **Reference Examples**: You can also check out the ready-made Blue Archive character halo resource packs at [Ba-Halo-Definition](https://github.com/AzusaKe/Ba-Halo-Definition) for real project examples.

---

*Halo Mod v1.0.6 · [GitHub](https://github.com/AzusaKe/Halo)*
