"""Build an isolated, pinned Bliss derivative; never changes the source ZIP."""
import hashlib
import json
import pathlib
import re
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
BASE = pathlib.Path(r"E:\MineCraft_transfer\.minecraft\versions\1.20.1-Fabric 0.19.3\shaderpacks\Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip")
EXPECTED = "f41db92acc585fe9ffe0cc124d6ecaea27cef9ded91129ebc2a8e60226947b2c"
def build():
    if hashlib.sha256(BASE.read_bytes()).hexdigest() != EXPECTED:
        raise RuntimeError("Bliss source ZIP differs from the audited baseline")
    profiles = json.loads((ROOT / "core/src/main/resources/network/azusake/halo/ba/arisu-v1.json").read_text())["profiles"]
    params = {}
    for profile in profiles.values():
        params.update(profile)
    def uniform(name): return "HaloBA_" + re.sub(r"[^a-zA-Z0-9_]", "_", name)
    declarations = []
    for name, value in params.items():
        declarations.append(f"uniform {'vec'+str(len(value)) if isinstance(value,list) else 'float'} {uniform(name)};")
    library = (ROOT / "scripts/ba/ba_material.glsl").read_text().replace("/* HALO_BA_PARAMETERS */", "\n".join(declarations))
    out = ROOT / ".local/ba/delivery/Halo BA Experimental (Chocapic13' Shaders edit).zip"
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(BASE) as src, zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as dst:
        for entry in src.infolist():
            data = src.read(entry)
            if entry.filename == "shaders/dimensions/all_translucent.fsh":
                text = data.decode().replace("\r", "")
                text = '#include "/lib/halo_ba.glsl"\n' + text
                text = text.replace("vec3 Albedo = toLinear(gl_FragData[0].rgb);",
                    "vec3 Albedo = HaloBA_type != 0 ? baSRGB(gl_FragData[0].rgb) : toLinear(gl_FragData[0].rgb);")
                # Sample data without ever passing through normals/specular LabPBR decoders.
                text = text.replace("vec3 normal = normalMat.xyz;", "vec4 baMask = HaloBA_hasMask != 0 ? texture2D(HaloBA_mask,lmtexcoord.xy) : vec4(HaloBA_mask_default.rgb,HaloBA_mask_alpha);\n"
                    "vec4 baSpec = HaloBA_hasSpec != 0 ? texture2D(HaloBA_spec,lmtexcoord.xy) : vec4(0.5,0.5,1.0,0.0);\n"
                    "vec3 normal = baWeaponNormal(normalize(normalMat.xyz),viewPos,lmtexcoord.xy,baMask);")
                # The generated default avoids a uniform named mask conflicting with the sampler.
                text = text.replace("\t#ifdef MC_NORMAL_MAP", "\t#if defined MC_NORMAL_MAP")
                # Disable material normal perturbation and ordinary emission/reflectance on BA.
                text = text.replace("vec3 SpecularTex = texture2D(specular, lmtexcoord.xy, Texture_MipMap_Bias).rga;",
                    "vec3 SpecularTex = HaloBA_type != 0 ? vec3(0.0) : texture2D(specular, lmtexcoord.xy, Texture_MipMap_Bias).rga;")
                text = text.replace("vec3 NormalTex = vec3(texture2D(normals, lmtexcoord.xy, Texture_MipMap_Bias).xy,0.0);",
                    "vec3 NormalTex = HaloBA_type != 0 ? vec3(0.5,0.5,0.0) : vec3(texture2D(normals, lmtexcoord.xy, Texture_MipMap_Bias).xy,0.0);")
                text = text.replace("bool isReflective = abs(MATERIALS - 0.7) < 0.01 || isWater || isReflectiveEntity;",
                    "bool isReflective = HaloBA_type == 0 && (abs(MATERIALS - 0.7) < 0.01 || isWater || isReflectiveEntity);")
                text = text.replace("normal = applyBump(", "normal = HaloBA_type != 0 ? normal : applyBump(")
                old = "vec3 FinalColor = (Indirect_lighting + Direct_lighting) * Albedo;"
                if old not in text: raise RuntimeError("Missing audited forward lighting entry")
                # Native minimum-light floor becomes excessive on pale BA palettes at night.
                text = text.replace("doIndirectLighting(AmbientLightColor, MinimumLightColor, lightmap.y)",
                    "doIndirectLighting(AmbientLightColor, HaloBA_type!=0 ? vec3(0.0) : MinimumLightColor, lightmap.y)")
                # Shared emission brightness: sky + average direct exposure + block light.
                # Surface normal/shadow response belongs exclusively to the toon node input.
                ambient = "vec3 baAmbient=Indirect_lighting;\n"
                ambient += "vec3 baLpvPos=vec3(0.0);\n#ifdef IS_LPV_ENABLED\n baLpvPos=GetLpvPosition(feetPlayerPos);\n#endif\n"
                ambient += "#ifdef OVERWORLD_SHADER\n if(HaloBA_type!=0) baAmbient=doIndirectLighting(averageSkyCol_Clouds/30.0,vec3(0.0),lightmap.y)"
                ambient += "+0.5*(lightCol.rgb/80.0)*clamp((lightmap.y-0.8)*5.0,0.0,1.0)"
                ambient += "+doBlockLightLighting(vec3(TORCH_R,TORCH_G,TORCH_B),lightmap.x,exposure,feetPlayerPos,baLpvPos);\n#endif\n"
                text = text.replace(old, ambient + old + "\n if(HaloBA_type!=0) FinalColor=baEvaluate(Albedo,baMask,baSpec,Indirect_lighting+Direct_lighting,baAmbient,exposure,normalize(normal),-normalize(viewPos));")
                text = text.replace("Emission(gl_FragData[0].rgb, Albedo, SpecularTex.b, exposure);", "if(HaloBA_type==0) Emission(gl_FragData[0].rgb, Albedo, SpecularTex.b, exposure);")
                # Non-water/non-glass tag survives to composite without relighting the HDR result.
                text = text.replace("vec4(Albedo, MATERIALS)", "vec4(Albedo, HaloBA_type != 0 ? 64.0/255.0 : MATERIALS)")
                data = text.encode()
            elif entry.filename == "shaders/shaders.properties":
                # Classification is discrete even when the color target alpha blends.
                text=data.decode().replace("\r", "")
                text=text.replace("screen = \\\n", "screen = [HALO_BA] \\\n", 1)
                text=re.sub(r"(?m)^(sliders\s*=)", r"\1 HALO_BA_HALO_EMISSION", text, count=1)
                data=text.encode()
                data += b"\nscreen.HALO_BA = HALO_BA_HALO_EMISSION\n"
                data += b"\nblend.gbuffers_water.colortex7 = off\nblend.gbuffers_entities_translucent.colortex7 = off\n"
            elif entry.filename == "shaders/lang/en_us.lang":
                data += b"\nscreen.HALO_BA=Halo BA\noption.HALO_BA_HALO_EMISSION=Halo global emission\noption.HALO_BA_HALO_EMISSION.comment=Independent emission for BA halo suffix materials. Preserves node colors and strength; exposure compensated.\n"
            elif entry.filename == "shaders/lang/zh_cn.lang":
                data += "\nscreen.HALO_BA=Halo BA\noption.HALO_BA_HALO_EMISSION=光环全局自发光\noption.HALO_BA_HALO_EMISSION.comment=按 halo 后缀识别；保留节点颜色和强度，不随环境变暗，进行曝光补偿。\n".encode()
            elif entry.filename in ("shaders/dimensions/composite1.fsh", "shaders/dimensions/composite2.fsh", "shaders/dimensions/composite3.fsh"):
                text=data.decode().replace("\r","")
                # Material identity must not interpolate across transparent edges.
                text=re.sub(r"texture2D\(colortex7,\s*(texcoord|tc)\)\.a",
                    r"texelFetch2D(colortex7,ivec2(\1*vec2(textureSize(colortex7,0))),0).a",text)
                if entry.filename.endswith("composite3.fsh"):
                    text=text.replace("bool isWater = translucentMasks > 0.99;",
                        "bool isBA = abs(translucentMasks - 64.0/255.0) < 0.5/255.0;\n bool isWater = translucentMasks > 0.99;")
                    text=text.replace("ApplyDistortion(refractedCoord, tangentNormals, linearDistance, isEntity);",
                        "if(!isBA) ApplyDistortion(refractedCoord, tangentNormals, linearDistance, isEntity);")
                    text=text.replace("if(!isWater) color *=", "if(!isWater && !isBA) color *=")
                    text=text.replace("if(albedo.a > 0.01 && !isWater", "if(albedo.a > 0.01 && !isBA && !isWater")
                data=text.encode()
            dst.writestr(entry.filename, data)
        library = library.replace("uniform vec4 HaloBA_mask;", "uniform vec4 HaloBA_mask_default;")
        dst.writestr("shaders/lib/halo_ba.glsl",library)
        dst.writestr("HALO-BA.txt", "Halo BA Experimental v4 (material contract v1), based on Bliss v2.1.2 release11 by X0nk and Chocapic13.\n"
            "https://github.com/X0nk/Bliss-Shader/tree/release11\n"
            "Original LICENSE.md and CREDITS.txt are retained. Local experiment; no monetizing links.\n")
    # Adapter uniform metadata is generated from the same exact table, not manually duplicated.
    metadata = {uniform(k) if k != "mask" else "HaloBA_mask_default": v for k,v in params.items()}
    target = ROOT / "src/main/resources/assets/halo/ba/parameters-v1.json"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(metadata,indent=2))
    print(out)
if __name__ == "__main__": build()
