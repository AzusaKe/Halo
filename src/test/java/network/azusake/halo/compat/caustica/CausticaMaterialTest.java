package network.azusake.halo.compat.caustica;
import network.azusake.halo.core.*;
import network.azusake.halo.core.render.*;
import org.junit.jupiter.api.Test;
import java.nio.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CausticaMaterialTest {
 private static final Identifier TEX=new Identifier("halo:a.png"),MASK=new Identifier("halo:mask.png");
 private SceneDraw draw(float alpha,float[] affine,MaterialState material,SceneDraw.Kind kind) {
  var mesh=ObjMeshLoader.parse(new Identifier("halo:a.obj"),"v 0 0 0\nv 1 0 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0 1\nf 1/1 2/2 3/3");
  return new SceneDraw(new SceneDraw.Key(UUID.randomUUID(),1,TEX,1,"0",0,0),kind,mesh,new Vec3d(29_999_999.123456,3,-12345.789),affine,
    new float[]{-1,0,0,0,2,0,0,0,.25f},TEX,true,true,true,1.5f,alpha,material);
 }
 private static float[] affine(){return new float[]{-2,0,0,0,0,.5f,0,0,0,0,4,0,.03125f,.5f,0,1};}
 @Test void preciseTransformRetainsMirrorAndNonUniformScaleAndRejectsDegeneracy(){
  var t=CausticaMaterial.transform(draw(1,affine(),MaterialState.LEGACY,SceneDraw.Kind.MESH));
  assertEquals(29_999_999.154706,t.translationX(),1e-8);assertEquals(-2,t.m00());assertEquals(.5f,t.m11());assertEquals(4,t.m22());
  assertEquals(.03125f,t.relativeTo(29_999_999.123456,3,-12345.789)[3]);
  float[] singular=affine();singular[0]=0;assertThrows(IllegalArgumentException.class,()->CausticaMaterial.transform(draw(1,singular,MaterialState.LEGACY,SceneDraw.Kind.MESH)));
 }
 @Test void dataUsagesAndMaterialFactorsStaySeparateAndNormalLayoutMatchesAbi(){
  var mask=new MaterialState.AlphaMask(MASK,MaterialState.MaskMode.STEP,.6f,-1.25f,2.125f);
  var d=draw(.5f,affine(),new MaterialState.Mesh(mask),SceneDraw.Kind.MESH);
  var lookups=new ArrayList<String>();
  var result=CausticaMaterial.encode(d,null,(id,srgb)->{lookups.add(id+":"+srgb);return new CausticaGpu.Texture(srgb?7:11,true,null);});
  assertFalse(result.opaque());var b=ByteBuffer.wrap(result.bytes()).order(ByteOrder.LITTLE_ENDIAN);
  assertEquals(128,result.bytes().length);assertEquals(7,b.getInt(0));assertEquals(11,b.getInt(16));
  assertEquals(1,b.getInt(28));assertEquals(.5f,b.getFloat(32));assertEquals(1.5f,b.getFloat(36));assertEquals(100,b.getFloat(52));
  assertEquals(2,b.getFloat(64));assertEquals(1,b.getFloat(84));assertEquals(1,b.getFloat(104));
  assertTrue(lookups.contains("halo:a.png:true"));assertTrue(lookups.contains("halo:a_n.png:false"));assertTrue(lookups.contains("halo:mask.png:false"));
  assertEquals(.6f,b.getFloat(40));assertEquals(-1.25f,b.getFloat(44));assertEquals(2.125f,b.getFloat(48));
 }
 @Test void coveragePolicyAccountsForBothRingSidesAndFades(){
  var d=draw(1,affine(),MaterialState.LEGACY,SceneDraw.Kind.RING);
  assertTrue(CausticaMaterial.encode(d,d,(id,srgb)->new CausticaGpu.Texture(3,true,null)).opaque());
  assertFalse(CausticaMaterial.encode(d,d,(id,srgb)->new CausticaGpu.Texture(3,false,null)).opaque());
  assertFalse(CausticaMaterial.encode(draw(.99f,affine(),MaterialState.LEGACY,SceneDraw.Kind.BILLBOARD),null,(id,srgb)->new CausticaGpu.Texture(3,true,null)).opaque());
 }
}
