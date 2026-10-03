"""Read Caustica's uncompressed scanline EXR diagnostics without a display transform."""
from pathlib import Path
import io, struct
import numpy as np

def read(path):
    stream=io.BytesIO(Path(path).read_bytes())
    def cstr():
        value=bytearray()
        while (char:=stream.read(1)) != b'\0':
            if not char:raise ValueError('Truncated EXR header')
            value.extend(char)
        return value.decode('utf-8')
    assert struct.unpack('<II',stream.read(8)) == (20000630,2), 'Expected single-part scanline EXR'
    attrs={}
    while (name:=cstr()):
        kind=cstr(); size=struct.unpack('<I',stream.read(4))[0]
        attrs[name]=(kind,stream.read(size))
    assert attrs['compression'][1] == b'\0', 'Expected uncompressed Caustica export'
    x0,y0,x1,y1=struct.unpack('<4i',attrs['dataWindow'][1]); width=x1-x0+1; height=y1-y0+1
    channel_data=io.BytesIO(attrs['channels'][1]); channels=[]
    while True:
        name=bytearray()
        while (char:=channel_data.read(1)) != b'\0':name.extend(char)
        if not name:break
        pixel_type,linear,xs,ys=struct.unpack('<iB3xii',channel_data.read(16))
        assert xs==ys==1, 'Subsampled channels are unsupported'
        channels.append((name.decode(),['<u4','<f2','<f4'][pixel_type]))
    offsets=struct.unpack('<'+'Q'*height,stream.read(8*height))
    values={name:np.empty((height,width),dtype=np.float32) for name,_ in channels}
    for offset in offsets:
        stream.seek(offset); y,size=struct.unpack('<iI',stream.read(8)); start=stream.tell()
        for name,dtype in channels:
            values[name][y-y0]=np.frombuffer(stream.read(width*np.dtype(dtype).itemsize),dtype=dtype)
        assert stream.tell()-start==size
    return values

if __name__=='__main__':
    import sys
    for file in sys.argv[1:]:
        data=read(file)
        print(Path(file).name,{name:dict(shape=value.shape,finite=bool(np.isfinite(value).all()),
              minimum=float(np.nanmin(value)),maximum=float(np.nanmax(value))) for name,value in data.items()})
