"""Read Seia before/after and nearby-view captures; do not alter image data."""
import json,sys
from pathlib import Path
import numpy as np
from PIL import Image
from raw_exr import read

out=Path(sys.argv[1])
def rgb(path):
    data=read(path);return np.stack([data[c] for c in 'RGB'],axis=-1)
def signals(label,metadata):
    n=rgb(out/(label+'-normal-roughness.exr'));e=rgb(out/(label+'-nrd-stable-radiance.exr'))
    h,w=n.shape[:2];yy,xx=np.mgrid[:h,:w]
    # Seia's original gold emission, bounded to the isolated halo. This is a
    # capture-specific object mask, not a generally valid segmentation rule.
    mask=(e[:,:,0]>70)&(e[:,:,0]<100)&(e[:,:,1]>40)&(e[:,:,1]<90)&(e[:,:,2]>20)&(e[:,:,2]<60)
    mask&=(xx>350)&(xx<700)&(yy>100)&(yy<480)
    matrix=np.array(metadata['projection']['inverseProjectionView']).reshape(4,4,order='F')
    clip=np.stack([2*(xx+.5)/w-1,1-2*(yy+.5)/h,np.zeros((h,w)),np.ones((h,w))],axis=-1)
    v=-(clip@matrix.T)[:,:,:3];v/=np.linalg.norm(v,axis=-1,keepdims=True)
    selected=n[mask];nv=np.sum(n*v,axis=-1)[mask]
    jumps=(np.linalg.norm(n[:,1:]-n[:,:-1],axis=-1)>.4)&mask[:,1:]&mask[:,:-1]
    return dict(objectPixels=int(mask.sum()),finiteNormals=bool(np.isfinite(n).all()),
        finiteEmission=bool(np.isfinite(e).all()),normalLengthMaxError=float(np.max(abs(np.linalg.norm(selected,axis=-1)-1))),
        minimumNormalDotView=float(nv.min()),backFacingPixels=int(np.sum(nv<-.001)),
        oldPlateauPixels=int(np.sum(np.linalg.norm(selected-[0,.765625,-.642578125],axis=-1)<.002)),
        horizontalNormalJumps=int(jumps.sum()),preExposure=metadata['preExposure'])
result={}
for label in ['hard-back','smooth-back','hard-front','smooth-front']:
    meta=json.loads((out/(label+'-raw.json')).read_text(encoding='utf-8'))['images'][0]['metadata']
    result[label]=signals(label,meta)
    shot=np.asarray(Image.open(out/(label+'-off.png')).convert('RGB'))
    if label.endswith('front'):
        result[label]['darkPixels']=int(np.sum(shot[205:535,745:1175].max(axis=-1)<55))
for k,item in enumerate(json.loads((out/'smooth-sweep.json').read_text(encoding='utf-8'))):
    result[f'smooth-sweep-{k}']=signals(f'smooth-sweep-{k}',item['raw']['images'][0]['metadata'])
(out/'normal-analysis.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
