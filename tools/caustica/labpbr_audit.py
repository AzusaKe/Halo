"""Audit original, enabled user textures; never write or modify image data."""
import io,json,sys,zipfile
from pathlib import Path
import numpy as np
from PIL import Image

game,out=Path(sys.argv[1]),Path(sys.argv[2])
options=(game/'options.txt').read_text(encoding='utf-8')
enabled=json.loads(next(line.split(':',1)[1] for line in options.splitlines() if line.startswith('resourcePacks:')))
result={}
for entry in enabled:
    if not entry.startswith('file/'):continue
    pack=game/'resourcepacks'/entry[5:]
    if pack.name not in ['Seia_mesh_import_glow.zip','Toki-LabPBR-Parallax.zip','Yuuka-LabPBR.zip','mika_obj_import-LabPBR.zip']:continue
    with zipfile.ZipFile(pack) as archive:
        textures={}
        for name in archive.namelist():
            if not name.endswith(('_n.png','_s.png')):continue
            with Image.open(io.BytesIO(archive.read(name))) as image:
                pixels=np.asarray(image.convert('RGBA'));flat=pixels.reshape(-1,4)
                textures[name]=dict(size=list(image.size),minimum=flat.min(axis=0).tolist(),maximum=flat.max(axis=0).tolist(),
                    greenHistogram={str(int(v)):int(n) for v,n in zip(*np.unique(flat[:,1],return_counts=True))})
                if name.endswith('_n.png'):
                    textures[name]['flatNormalRG']=bool(np.all(flat[:,:2]==flat[0,:2]))
        result[entry]=textures
out.write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
