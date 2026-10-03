package network.azusake.halo.platform;
import org.junit.jupiter.api.Test;
import network.azusake.halo.core.*;
import network.azusake.halo.core.runtime.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ClientDeparturesTest {
 @Test void healthyUntrackingDropsOnlyVisualAndPreservesAuthoritativeAssignment(){
  var runtime=new ClientRuntime(()->1L);var uuid=UUID.randomUUID();var definition=new Identifier("halo:test");
  runtime.attach(uuid,definition,false);var old=runtime.getInstance(uuid);assertNotNull(old);
  ClientDepartures.depart(runtime,uuid,false,false);assertNull(runtime.getInstance(uuid));assertEquals(definition,runtime.assignments().get(uuid));
  runtime.attach(uuid,definition,false);assertNotSame(old,runtime.getInstance(uuid));
  ClientDepartures.depart(runtime,uuid,false,true);assertFalse(runtime.assignments().containsKey(uuid));
 }
 @Test void playerDeathKeepsAssignmentForRespawn(){
  var runtime=new ClientRuntime(()->1L);var uuid=UUID.randomUUID();var definition=new Identifier("halo:test");runtime.attach(uuid,definition,false);
  ClientDepartures.depart(runtime,uuid,true,true);assertNull(runtime.getInstance(uuid));assertEquals(definition,runtime.assignments().get(uuid));
 }
}
