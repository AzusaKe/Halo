# Halo 26.2 LabPBR controls

Enable this resource pack above other test packs. Use `/halo show @s halo_verify:control`,
then `normal`, `smoothness`, `metal`, `emission` in the same namespace.
Each definition places a billboard, ring and mesh side by side, all explicitly glowing:false.
The base color, UV and geometry are identical across variants. Normal adds checkerboard X slopes;
smoothness changes the right half; metal changes that half to iron; emission lights that half.
Compare every primitive, both renderer settings, day/night and resource/shader reloads.
Use a shader configured for entity LabPBR. Record exact shader/settings and screenshots.
No POM or GUI LabPBR claim follows from these tests.
Encoding reference: https://shaderlabs.org/wiki/LabPBR_Material_Standard
