package network.azusake.halo.compat.caustica;
import dev.comfyfluffy.caustica.api.geometry.*;
import dev.comfyfluffy.caustica.api.program.*;
import dev.comfyfluffy.caustica.api.resource.*;
import dev.comfyfluffy.caustica.api.scene.*;
import dev.comfyfluffy.caustica.api.light.LightId;
import network.azusake.halo.core.*;
import network.azusake.halo.core.render.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CausticaPlacementsTest {
 static final class Claim implements ResourceOwner {
  final AtomicInteger refs;boolean closed;
  Claim(){refs=new AtomicInteger(1);}Claim(AtomicInteger refs){this.refs=refs;refs.incrementAndGet();}
  public Claim retain(){assertFalse(closed);return new Claim(refs);}public void close(){if(!closed){closed=true;refs.decrementAndGet();}}
 }
 static final class Mesh implements ReadyMesh<CausticaPrograms.Instance>{
  final Claim claim;Mesh(){claim=new Claim();}Mesh(Claim claim){this.claim=claim;}
  public ShaderDataType<CausticaPrograms.Instance> instanceDataType(){return CausticaPrograms.INSTANCE;}
  public Mesh retain(){return new Mesh(claim.retain());}public void close(){claim.close();}
 }
 static final class Channel implements SceneChannel {
  List<? extends SceneEdit> last=List.of();int calls;boolean fail;
  final Map<InstanceId,ShaderData<?>> data=new HashMap<>();final Map<InstanceId,ReadyMesh<?>> meshes=new HashMap<>();
  public InstanceId newInstance(){return new InstanceId(){};}public LightId newLight(){throw new UnsupportedOperationException();}
  public void edit(List<? extends SceneEdit> edits){
   if(fail)throw new IllegalStateException("rejected atomically");calls++;last=List.copyOf(edits);
   for(var e:edits){if(e instanceof SceneEdit.SetInstance<?> set){var old=data.put(set.instance(),set.instanceData().retain());if(old!=null)old.close();var m=meshes.put(set.instance(),set.mesh().retain());if(m!=null)m.close();}
    else if(e instanceof SceneEdit.DropInstance drop){data.remove(drop.instance()).close();meshes.remove(drop.instance()).close();}}
  }
 }
 static SceneDraw.Key key(int n){return new SceneDraw.Key(new UUID(0,n),n,new Identifier("halo:test"),1,"0",0,0);}
 @Test void reorderMotionAndMaterialsKeepStableIdsAndIndependentReferences(){
  var channel=new Channel();var scene=new CausticaPlacements(channel,new SceneId(){});var mesh=new Mesh();var root=new Claim();
  try(var tx=scene.begin()){for(int n:new int[]{1,2})tx.put(key(n),mesh,GeometryTransform.translation(n,0,0),new byte[]{7},()->CausticaPrograms.INSTANCE.data(123,root));assertEquals(2,tx.commit());}
  assertEquals(3,mesh.claim.refs.get());assertEquals(3,root.refs.get());var first=((SceneEdit.SetInstance<?>)channel.last.get(0)).instance();
  try(var tx=scene.begin()){for(int n:new int[]{2,1})tx.put(key(n),mesh,GeometryTransform.translation(n,0,0),new byte[]{7},()->{throw new AssertionError("Steady frame allocates no parameter data");});assertEquals(0,tx.commit());}
  assertEquals(1,channel.calls);
  try(var tx=scene.begin()){tx.put(key(1),mesh,GeometryTransform.translation(9,0,0),new byte[]{7},()->{throw new AssertionError();});tx.put(key(2),mesh,GeometryTransform.translation(2,0,0),new byte[]{8},()->CausticaPrograms.INSTANCE.data(456,root));assertEquals(2,tx.commit());}
  assertEquals(first,((SceneEdit.SetTransform)channel.last.get(0)).instance());assertInstanceOf(SceneEdit.SetInstance.class,channel.last.get(1));
  root.close();mesh.close();assertEquals(2,root.refs.get());assertEquals(2,mesh.claim.refs.get());
  scene.hide();assertEquals(0,root.refs.get());assertEquals(0,mesh.claim.refs.get());assertEquals(0,scene.size());
 }
 @Test void rejectedEditPreservesCpuRevisionAndReleasesUnacceptedClaims(){
  var channel=new Channel();var scene=new CausticaPlacements(channel,new SceneId(){});var mesh=new Mesh();var root=new Claim();
  try(var tx=scene.begin()){tx.put(key(1),mesh,GeometryTransform.translation(1,0,0),new byte[]{1},()->CausticaPrograms.INSTANCE.data(1,root));tx.commit();}
  channel.fail=true;
  try(var tx=scene.begin()){tx.put(key(2),mesh,GeometryTransform.translation(2,0,0),new byte[]{2},()->CausticaPrograms.INSTANCE.data(2,root));assertThrows(IllegalStateException.class,tx::commit);}
  assertEquals(1,scene.size());assertEquals(2,root.refs.get());channel.fail=false;scene.hide();root.close();mesh.close();assertEquals(0,root.refs.get());
 }
}
