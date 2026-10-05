"""Turn the actual saved node/link audit into a reviewable active-output report."""
import json
import pathlib

ROOT=pathlib.Path(__file__).resolve().parents[2]
audit=json.loads((ROOT/'.local/ba/node-audit.json').read_text(encoding='utf8'))
def active(tree, output_type):
    nodes={n['name']:n for n in tree['nodes']}
    roots=[n['name'] for n in tree['nodes'] if n['type']==output_type and n['active_output']]
    visited=set(); pending=list(roots)
    while pending:
        name=pending.pop()
        if name in visited: continue
        visited.add(name)
        node=nodes[name]
        links=[l for l in tree['links'] if l[2]==name]
        # Muted math passes the first input directly. Do not import its disconnected second input.
        if node['mute'] and node['type']=='ShaderNodeMath': links=[l for l in links if l[3]=='Value']
        if node['type']=='ShaderNodeMixShader':
            factor=next(s for s in node['inputs'] if s['id']=='Fac')
            if not any(l[3]=='Fac' for l in links) and factor['default'] in (0,1):
                selected='Shader' if factor['default']==0 else 'Shader_001'
                links=[l for l in links if l[3]==selected]
        pending.extend(l[0] for l in links)
    return visited

lines=['# 指定 Arisu 文件的活动节点审计','',
    f"来源：`{audit['source']}`；SHA256 `{audit['sha256']}`。",
    f"Blender {audit['blender']}，参考帧 {audit['frame']}。读取时 `--disable-autoexec` / `use_scripts=False`；没有运行内嵌脚本，没有保存源文件。",'',
    '参考图以独立进程临时改用 `BLENDER_EEVEE_NEXT`。原场景保存的 `BLENDER_EEVEE` 在 4.5.6 中不可用；参考图不是原旧版引擎的保证等价截图。嘴部非法驱动保持跳过。','',
    '| 材质 | 活动 Surface 输入 | 活动节点数/总数 |','| --- | --- | --- |']
materials=['CH0334_Body','CH0334_Body_Arms','CH0334_Face','CH0334_Hair','BA_Halo_Emission','CH0334_Weapon01','CH0334_EyeMouth','CH0334_Eyebrow']
for name in materials:
    tree=audit['materials'][name]; used=active(tree,'ShaderNodeOutputMaterial')
    groups=[n['group'] for n in tree['nodes'] if n['name'] in used and n['group']]
    lines.append(f"| {name} | {', '.join(groups)} | {len(used)}/{len(tree['nodes'])} |")
lines += ['', '## 数学及实际输入','',
    '- Body/Arms/Face 分别保存实际实例参数。ShadowClamp 使用严格 `>` 的 A 分段；G 控制明暗偏置。Face 没有 Mask 链接，原输入 RGB=0、A=0.5。Fresnel IOR=1.5，按实际阈值和 Alpha 条件选择边缘色。',
    '- Hair 活动路径含 BA_MASK、BA_SPEC。Spec RGB 解码 `2*RGB-1` 后归一化；A 控制形状扣减和 `<0.07` 排除；硬高光为严格 `>`，不把 Spec 当粗糙度/金属度。Mask G 使用不截断 Map Range，阴影 TRUNC 向零截断。',
    '- `hair_spec_normal` 世界空间驱动为 `(-rotX,-rotZ,-rotY)`；Incoming 旋转 Y=pi/2。导出 helper 参考四元数，运行时根据部件姿态重算驱动。',
    '- Halo 颜色距离平方选择、替换和混色后乘有效发光强度约 0.5（原始浮点 -0.500000119 + 1）。',
    '- Weapon Diffuse Roughness=1，Mask R 为 Bump 高度，Distance=0.001，invert=true。G 经 `2G-1` 从阴影输入减去；B 经 `2B-1` 作为 AO 混色。`Math.009` 静音，直接透传第一输入，不能额外扣除 `1-B`。metallic=0，跳过金属 BSDF 支路。MC 的漫反射输入目前不是 Blender Oren–Nayar 的严格复现。',
    '- EyeMouth/Eyebrow 活动 ba_no_shadow，发光强度 1；阴影射线透明。eyebrow_in_front 只接到非活动 Material Output，默认不启用。',
    '- 描边：实际活动几何节点读取 outline 属性，法线偏移、合并/删除、翻面及材质替换后实例输出。导出阶段仅在内存中追加 Realize Instances。三个描边颜色从实际 Emission 读取，生成 sRGB 常量色图；不以纹理打包代替壳体。','',
    '## 活动组的节点列表','', '以下以活动 Group Output 反向跟踪，排除未连接节点、静音第二输入及 metallic=0 支路。组输入会在材质实例中覆盖默认值；原始完整链接见交付目录 node-audit.json。','']
for name in ['ba_body_shader','ShadowClamp','ba_hair_shader','BA_MASK','BA_SPEC','ba_halo','ba_weapon_shader','ba_no_shadow']:
    if name not in audit['groups']: continue
    tree=audit['groups'][name]; used=active(tree,'NodeGroupOutput')
    lines += [f'### {name}', '',f"活动祖先节点 {len(used)}/{len(tree['nodes'])}：", '',
        ', '.join('`'+n['name']+'`'+('（静音透传）' if n['mute'] else '') for n in tree['nodes'] if n['name'] in used), '']
lines += ['BA_SPEC 的 Kajiya-Kay、MatCap 等不在实际活动输出路径中，因此未移植。纹理和参数仅以此指定文件为依据；附近旧导出不作为节点真值。','']
(ROOT/'docs/ba-node-audit.md').write_text('\n'.join(lines),encoding='utf8')
print('Saved docs/ba-node-audit.md')
