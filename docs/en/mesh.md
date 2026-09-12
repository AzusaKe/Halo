# OBJ meshes and alpha masks

Halo 2.0.0 development adds the `mesh` primitive with definition schema **1.1.0**. Existing definitions remain supported.
Put assets in a resource pack and reference them by namespace:

```text
assets/mypack/halo_definitions/example.json
assets/mypack/models/halo/example.obj
assets/mypack/textures/halo/example.png
assets/mypack/textures/halo/example_mask.png
```

A complete definition follows. Omit `material` for an ordinary textured mesh.

```json
{
  "version": "1.1.0",
  "id": "mypack:example",
  "positioning": {"offset": [0, 0.4, 0], "scale": 1},
  "layers": [{
    "position": [0, 0, 0],
    "rotation": [0, 0, 0],
    "primitive": {
      "type": "mesh",
      "model": "mypack:models/halo/example.obj",
      "texture": "mypack:textures/halo/example.png",
      "size": [0.5, 0.5, 0.5],
      "material": {
        "double_sided": true,
        "effects": [{
          "type": "alpha_mask",
          "texture": "mypack:textures/halo/example_mask.png",
          "mode": "linear",
          "threshold": 0.5,
          "uv_offset": {
            "u": [{"function": "linear", "start": 0, "speed": 0.1}],
            "v": [{"function": "sin", "A": 0.05, "omega": 1, "phi": 0}]
          }
        }]
      }
    }
  }]
}
```

## Coordinates and size

`model` and `texture` are required. By default, three-component `size` is also required. Size specifies the target local X/Y/Z bounding-box
dimensions in blocks, with independent scaling per axis before group/ancestor scaling. It is a dimension,
not a multiplier: `[1,1,1]` fits the bounds into a one-block cube.

The exported origin is retained. Halo does not center, swap axes, or rotate imported geometry. OBJ `(0,0,0)`
maps to the enclosing group's origin, with aligned axes at zero group position/rotation. Author coordinates
around your intended pivot. Position, rotation and animation belong on the group, using the existing
`[yaw,pitch,roll]` degrees and Y-X-Z order.

Size must be finite and nonnegative. A zero-extent source axis requires size=0 and retains its authored
coordinate on that axis; an XY plane uses `[0.5,0.5,0]`. An axis with nonzero extent may be collapsed to zero.

For ready-to-use exported geometry, set **`preserve_proportions: true`** inside the primitive:

```json
{
  "type": "mesh",
  "model": "mypack:models/halo/example.obj",
  "texture": "mypack:textures/halo/example.png",
  "preserve_proportions": true,
  "scale": 1
}
```

| Primitive field | Default | Behavior |
| --- | --- | --- |
| `preserve_proportions` | `false` | `true` keeps authored OBJ coordinates and ignores `size`; this does not fit geometry inside `size` |
| `scale` | `1` | One finite nonnegative number; uniformly multiplies authored XYZ only when `preserve_proportions` is true |
| `size` | Required when the flag is false | May be omitted when true; if supplied it must still be a valid three-component nonnegative size |

In this mode, one OBJ unit is one block at `scale: 1`, and the transform is
`group/ancestor matrix × uniform primitive scale × OBJ vertex`. Scaling is about the authored origin,
including any offset of the geometry from that origin. Zero-extent axes need no special settings.
Group and positioning scales still apply afterwards, independently. With the flag false, primitive `scale`
has no effect and existing definitions retain independent per-axis size fitting.

## Exporting OBJ

1. Export a static mesh with the intended object transforms applied and Y-up axes matching Halo local coordinates.
2. Unwrap every face and use a single PNG. Named objects/groups are combined under the JSON material.
3. Triangulate on export. Planar convex quads are also supported; concave faces, nonplanar quads and larger polygons require triangulation before loading.
4. Every corner needs a UV: `v/vt` or `v/vt/vn`. Positive/negative independent indices and UV seams are supported.
5. Normals are accepted, but v1 uses Halo's existing uniform brightness. MTL, object/group names and smoothing groups do not alter the material. Bones, model animations and free-form geometry are unsupported.

### Blender and external OBJ files

Use the editor's existing basis change: Blender `(x,y,z)` becomes Halo `(x,z,-y)`;
the inverse is `(x,-z,y)`. Its determinant is **+1**, so it rotates without mirroring or reversing winding.
Blender's native OBJ exporter provides this with **Forward = -Z, Up = Y**
(`forward_axis='NEGATIVE_Z', up_axis='Y'`). If the add-on already converts vertices explicitly,
do not apply the exporter axis conversion a second time.

External OBJ files have no standard axis metadata. Import them into Blender with their source axis convention,
verify orientation, then export using the Halo convention above. Halo reads OBJ XYZ unchanged.
Vertices must be relative to the enclosing group's origin; do not bake a group transform into geometry and
also encode it in JSON. To retain exact exported dimensions, use `preserve_proportions: true, scale: 1`.
Older definitions can still set `size` to the exported XYZ bounding-box extents.

A Blender 5.2 export of an asymmetric off-origin tetrahedron verifies that `(0.25,0.5,1)` becomes
`(0.25,1,-0.5)`, retains its winding and UV seams, and is neither centered nor mirrored by core.

OBJ V is flipped exactly once on import to existing texture coordinates: U right, V down. Do not additionally
flip the image for Halo. Load guards are 16 MiB OBJ text, 1,000,000 declared position/UV/normal elements combined,
and 250,000 triangles. These are not performance guarantees; use hundreds to a few thousand triangles for decorations.

## Material parameters

Only mesh supports `material` in this release. `effects` may be omitted/empty and supports at most one
`alpha_mask`. Unknown or repeated effects report a definition error.

| Field | Default | Behavior |
| --- | --- | --- |
| `double_sided` | `true` | Render both sides; false culls back faces using winding |
| `effects[].texture` | Required | Grayscale mask PNG; same dimensions or a uniform integer multiple/divisor of the base PNG |
| `mode` | `linear` | `linear` preserves gray values; `step` applies a binary threshold |
| `threshold` | `0.5` | In [0,1], STEP only; visible when gray is at least the threshold |
| `uv_offset.u/v` | Zero | Arrays of existing animation terms, summed independently per channel |

The mask reads normalized PNG R, ignores its own alpha, and applies no sRGB conversion. Black is zero,
white is one, gray 128 is `128/255`. Sampling is nearest and repeating without changing shared texture state.
LINEAR describes gray-to-alpha transfer, not bilinear filtering.

For a base texture of `m × n`, the mask may be `i*m × i*n` or `m/i × n/i`, with a positive integer `i`
and integer dimensions. For example, `32×16` accepts `16×8`, `32×16`, `64×32` and `96×48`, but not
`64×16` (unequal axis factors) or `48×24` (noninteger factor). Both textures use the same normalized UV
domain at their own resolutions. The smaller texture's pixels cover whole blocks on the larger grid;
no texture is downsampled, averaged, or uploaded again as an enlarged image. A high-resolution mask keeps
all of its detail. Base texture filtering remains controlled by Minecraft/resource metadata; keep PNG
`blur` disabled for pixel-exact nearest enlargement (the ordinary default).

```text
maskUV = fract(baseUV + offset(t))
maskAlpha = mode == step ? (gray >= threshold ? 1 : 0) : gray
finalAlpha = baseTextureAlpha × groupAndAnimationAlpha × maskAlpha
```

Base UVs never scroll. Positive U/V offsets increase sampling coordinates, so the pattern appears to move in
the opposite direction. One offset unit is a full texture period. `linear` is `start + speed*t`, `sin` is
`A*sin(omega*pi*t+phi)`, and `cos` is analogous; time is seconds and phi is radians. The existing idle clock
freezes mask motion during startup/shutdown transitions and resumes it afterwards. Resource reload preserves ownership and instance time.

`glowing`, `animation.glow` and existing inheritance still control brightness; v1 adds no PBR or directional
normal lighting. Blended meshes use ordinary depth-tested transparency sorting, with the usual limitations
for intersecting surfaces and intersections with world water/glass.

Meshes submit after entity buffers have been flushed, so later entity draws cannot overwrite blended meshes
that intentionally do not write depth. When Iris is active, Halo creates a private `gbuffers_textured` variant
during shader loading. Mask alpha modifies the base sample before pack shading; Iris owns its world render
targets and subsequent post-processing. Shared shaders used by old primitives remain unchanged, with no new
Iris runtime dependency. In this Iris pass, surviving mesh fragments also write translucent depth (`depthtex0`),
including linear masks. Packs need the mesh's own depth to reconstruct its position for compositing and fog;
using glass/sky background depth can fade out a nearby mesh. The opaque depth copy (`depthtex1`) is already
complete at this stage. Zero-alpha fragments are discarded, and alpha blending/sorting remain in effect.
Without shaders, blended meshes retain the usual no-depth-write behavior. Color, fog and bloom depend on
the shader pack; v1 adds neither PBR nor a separate
mesh shadow-casting pass. The 1.20.1 bridge lives in Halo, outside core. Unsupported geometry/tessellation
stages or base-sampling forms pause meshes with a diagnostic; changing packs rebuilds the material.

## Examples and reload

Built-ins `halo:mesh_demo`, `halo:mesh_mask_demo` and `halo:mesh_step_demo` show base textures, linear masks,
and step masks. Equip with `/halo show @s halo:mesh_mask_demo`; hide with `/halo hide @s`.
`halo:mesh_preserve_demo` uses authored geometry with primitive `scale: 0.4` and no `size`.
`halo:mesh_mask_resolution_demo` pairs a 32×32 base with 16×16 and 64×64 masks on two separate meshes;
the fine stripes in the 64×64 mask demonstrate that its extra detail is retained.

Models/base textures/masks come from client resource packs, following pack priority. Use **F3+T** after changes;
server `/reload` reloads data packs instead. Dedicated servers do not load OBJ/PNG files and the Halo protocol
does not transmit them. Distribute the same client resource pack when players should see the same appearance.

Missing/corrupt assets or mask dimensions outside the integer-ratio rule skip only the affected mesh, with resource diagnostics.
Repair and reload to recover without re-equipping; other valid primitives remain visible.
