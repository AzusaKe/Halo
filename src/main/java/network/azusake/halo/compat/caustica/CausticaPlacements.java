package network.azusake.halo.compat.caustica;
import dev.comfyfluffy.caustica.api.geometry.*;
import dev.comfyfluffy.caustica.api.program.ShaderData;
import dev.comfyfluffy.caustica.api.scene.*;
import network.azusake.halo.core.render.SceneDraw;
import java.util.*;
import java.util.function.Supplier;

/** Transactional retained identities: CPU state changes only after the whole scene edit is accepted. */
final class CausticaPlacements {
 private record Placement(InstanceId id,ReadyMesh<CausticaPrograms.Instance> mesh,GeometryTransform transform,byte[] material){}
 private final SceneChannel channel;private final SceneId scene;
 private Map<SceneDraw.Key,Placement> current=new HashMap<>();
 CausticaPlacements(SceneChannel channel,SceneId scene){this.channel=channel;this.scene=scene;}
 final class Transaction implements AutoCloseable {
  private final Map<SceneDraw.Key,Placement> next=new HashMap<>();
  private final List<SceneEdit> edits=new ArrayList<>();
  private final List<ShaderData<?>> claims=new ArrayList<>();
  void put(SceneDraw.Key key,ReadyMesh<CausticaPrograms.Instance> mesh,GeometryTransform transform,byte[] material,Supplier<ShaderData<CausticaPrograms.Instance>> data){
   var old=current.get(key);var id=old==null?channel.newInstance():old.id();
   if(old==null || old.mesh()!=mesh || !Arrays.equals(old.material(),material)){
    var value=data.get();claims.add(value);edits.add(new SceneEdit.SetInstance<>(id,scene,mesh,transform,255,value));
   }else if(!old.transform().equals(transform))edits.add(new SceneEdit.SetTransform(id,transform,255));
   next.put(key,new Placement(id,mesh,transform,material));
  }
  int commit(){
   for(var entry:current.entrySet())if(!next.containsKey(entry.getKey()))edits.add(new SceneEdit.DropInstance(entry.getValue().id()));
   if(!edits.isEmpty())channel.edit(edits);current=next;return edits.size();
  }
  public void close(){claims.forEach(ShaderData::close);}
 }
 Transaction begin(){return new Transaction();}
 int size(){return current.size();}
 void hide(){try(var tx=begin()){tx.commit();}}
}
