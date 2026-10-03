package network.azusake.halo.compat.caustica;

import java.nio.*;
import dev.comfyfluffy.caustica.api.geometry.GeometryTransform;
import network.azusake.halo.core.render.*;

/** Versioned 128-byte shader record. Pure conversion, with no resource reads or triangle scans. */
final class CausticaMaterial {
    static final int SIZE=128, TEXTURED=1,GLOW=2,RING=4,LEGACY=8,NORMAL=16,SPEC=32,MASK=64,INNER_NORMAL=128,INNER_SPEC=256;
    private static final float NITS=nits();
    private static float nits(){try{float n=Float.parseFloat(System.getProperty("halo.caustica.emissionNits","100"));return Float.isFinite(n)&&n>=0?n:100;}catch(NumberFormatException e){return 100;}}
    record Value(byte[] bytes,boolean opaque) { }
    static GeometryTransform transform(SceneDraw draw) {
        return new GeometryTransform(draw.transform(0),draw.transform(4),draw.transform(8),
            draw.transform(1),draw.transform(5),draw.transform(9),draw.transform(2),draw.transform(6),draw.transform(10),
            draw.worldX(),draw.worldY(),draw.worldZ());
    }
    static Value encode(SceneDraw draw,SceneDraw inner,CausticaAssets assets) {
        return encode(draw,inner,assets::texture);
    }
    static Value encode(SceneDraw draw,SceneDraw inner,java.util.function.BiFunction<network.azusake.halo.core.Identifier,Boolean,CausticaGpu.Texture> texture) {
        var data=ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        var base=draw.textured()?texture.apply(draw.texture(),true):null;
        var inside=inner==null || !inner.textured()?null:texture.apply(inner.texture(),true);
        var normal=draw.textured()?texture.apply(CausticaAssets.companion(draw.texture(),"_n"),false):null;
        var spec=draw.textured()?texture.apply(CausticaAssets.companion(draw.texture(),"_s"),false):null;
        var inNormal=inner==null || !inner.textured()?null:texture.apply(CausticaAssets.companion(inner.texture(),"_n"),false);
        var inSpec=inner==null || !inner.textured()?null:texture.apply(CausticaAssets.companion(inner.texture(),"_s"),false);
        MaterialState.AlphaMask mask=draw.material() instanceof MaterialState.Mesh mesh?mesh.mask():null;
        var maskTexture=mask==null?null:texture.apply(mask.texture(),false);
        int flags=(base!=null?TEXTURED:0)|(draw.glowing()?GLOW:0)|(inner!=null?RING:0)
            |(draw.kind()!=SceneDraw.Kind.MESH?LEGACY:0)|(normal!=null?NORMAL:0)|(spec!=null?SPEC:0)
            |(maskTexture!=null?MASK:0)|(inNormal!=null?INNER_NORMAL:0)|(inSpec!=null?INNER_SPEC:0);
        data.putInt(index(base)).putInt(index(inside==null?base:inside)).putInt(index(normal)).putInt(index(spec))
            .putInt(index(maskTexture)).putInt(0).putInt(flags).putInt(mask!=null && mask.mode()==MaterialState.MaskMode.STEP?1:0);
        data.putFloat(draw.alpha()).putFloat(draw.brightness()).putFloat(mask==null?0:mask.threshold())
            .putFloat(mask==null?0:mask.offsetU()).putFloat(mask==null?0:mask.offsetV()).putFloat(NITS).putFloat(0).putFloat(0);
        // Store the authored correction in object space. Ordinary inverse-transpose normals
        // reduce to identity, so rotation/scale animation needs only SetTransform.
        var correction=new org.joml.Matrix3f(new org.joml.Matrix4f().set(draw.localToWorld())).transpose()
            .mul(new org.joml.Matrix3f().set(draw.normalToWorld()));
        float[] normals=correction.get(new float[9]);boolean ordinary=true;
        for(int i=0;i<9;i++)ordinary &= Math.abs(normals[i]-(i%4==0?1:0))<2e-5f;
        for(int row=0;row<3;row++){for(int col=0;col<3;col++)data.putFloat(ordinary?(row==col?1:0):normals[col*3+row]);data.putFloat(0);}
        data.putInt(index(inNormal)).putInt(index(inSpec)).putInt(0).putInt(0);
        return new Value(data.array(),draw.alpha()==1 && mask==null && (base==null||base.opaque()) && (inside==null||inside.opaque()));
    }
    private static int index(CausticaGpu.Texture value){return value==null?0:value.index();}
}
