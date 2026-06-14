---
name: halo-renderer-debug
description: Debug approach for HaloRenderer visibility issues - Vec3d.ZERO initial position, texture fallback, log diagnostics
metadata:
  type: project
---

HaloRenderer initially failed to show any halos (not even missing-texture quads).

**Root Causes Found & Fixed:**
1. **Initial Vec3d.ZERO position**: HaloInstance.relativePosition starts as Vec3d.ZERO. Before the first physics tick, getInterpolatedPosition() returns (0,0,0), placing the halo at world origin — often underground or thousands of blocks away. Fix: detect position < 0.001 length and fall back to entity head anchor.
2. **No debug visibility**: The renderer silently failed. Added comprehensive debug logging with periodic (3s) summaries and one-time first-render confirmation.
3. **Missing texture fallback**: If texture PNGs aren't loaded, bindTextureSafe falls back to texture 0, which produces black pixels with the POSITION_TEX shader. Fix: draw a magenta quad with POSITION_COLOR shader as fallback.
4. **No try-finally on matrix stack**: Exception during rendering would corrupt the stack. Fixed with try-finally blocks.
5. **RenderSystem.enableTexture() doesn't exist** in 1.20.1 Yarn mappings. Removed.

**Debug Log Messages to Check (in game log):**
- `[HaloRenderListener] registered` → event hook is wired
- `[HaloRenderer] FIRST RENDER FRAME` → confirms render event fires, shows definition count & active halo count
- `[HaloRenderer] frame=N totalActive=M visible=V rendered=R` → periodic summary every 3s
- `[HaloRenderer] entity not found` → entity lookup failure
- `[HaloRenderer] definition not found` → HaloJsonLoader missing definition
- `[HaloRenderer] physics not yet initialised` → fallback position used
- `[HaloRenderer] halo too far from camera` → anomaly in position calculation
- `[HaloRenderer] texture not found` → texture PNG missing/unregistered
- `[HaloRenderer] rendered halo for` → successful render (DEBUG level)

**How to apply:** When debugging rendering issues, first check the FIRST RENDER FRAME log to confirm event fires. Then check periodic summary for active/visible/rendered counts. If rendered=0 but visible>0, check individual halo failure logs.

Related: [[halo-task-10-renderer]]
