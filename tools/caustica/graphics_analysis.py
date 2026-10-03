"""Read captured PNG/EXR signals without editing or tone mapping the images."""
import json,sys
from pathlib import Path
import numpy as np
from PIL import Image
from raw_exr import read

out=Path(sys.argv[1])
def rgb(path):
    d=read(path);return np.stack([d[c] for c in 'RGB'],axis=-1)
def silhouette(label):
    shot=np.asarray(Image.open(out/(label+'-off.png')).convert('RGB'))
    normal=rgb(out/(label+'-normal-roughness.exr'))
    metadata=json.loads((out/(label+'-raw.json')).read_text())['images'][0]['metadata']
    h,w=normal.shape[:2];yy,xx=np.mgrid[:h,:w]
    # Direction from surface to eye; depth cancels for this perspective camera.
    matrix=np.array(metadata['projection']['inverseProjectionView']).reshape(4,4,order='F')
    clip=np.stack([2*(xx+.5)/w-1,1-2*(yy+.5)/h,np.zeros((h,w)),np.ones((h,w))],axis=-1)
    q=clip@matrix.T;outgoing=-q[:,:,:3];outgoing/=np.linalg.norm(outgoing,axis=-1,keepdims=True)
    nv=np.sum(normal*outgoing,axis=-1)
    # Fixed camera object-only bounding rectangle, excludes the stand/platform.
    roi=shot[205:535,745:1175];dark=np.max(roi,axis=-1)<55
    sy,sx=np.where(dark);sample=nv[np.minimum(((sy+205+.5)*h/shot.shape[0]).astype(int),h-1),
                                 np.minimum(((sx+745+.5)*w/shot.shape[1]).astype(int),w-1)]
    return dict(darkPixels=int(dark.sum()),backFacingFraction=float(np.mean(sample<=0)) if len(sample) else 0,
                normalDotViewMedian=float(np.median(sample)) if len(sample) else None,
                finiteNormals=bool(np.isfinite(normal).all()),preExposure=metadata['preExposure'])

result={}
for label in ['seia','seia-guard']:
    if (out/(label+'-off.png')).exists():result[label]=silhouette(label)
for label in ['day-auto','night-auto','night-manual-minus7']:
    v=rgb(out/('yuuka-'+label+'-nrd-stable-radiance.exr'))[290:393,445:670]
    mask=(v[:,:,2]>1)&(v[:,:,0]>0)&(v[:,:,2]/np.maximum(v[:,:,0],.0001)>2)
    metadata=json.loads((out/('yuuka-'+label+'-raw.json')).read_text())['images'][0]['metadata']
    result['yuuka-'+label]=dict(bluePixels=int(mask.sum()),unexposedAcesCgMedian=np.median(v[mask],axis=0).tolist(),
                               preExposure=metadata['preExposure'],finiteRadiance=bool(np.isfinite(v).all()))
(out/'graphics-analysis.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
