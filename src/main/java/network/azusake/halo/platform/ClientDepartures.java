package network.azusake.halo.platform;
import java.util.UUID;
import network.azusake.halo.core.runtime.ClientRuntime;

/** An untracked entity is removed from Minecraft but may still be alive and owned on the server. */
public final class ClientDepartures {
 private ClientDepartures(){}
 public static void depart(ClientRuntime runtime,UUID uuid,boolean player,boolean confirmedDeath){
  if(confirmedDeath)runtime.died(uuid,player);else runtime.unload(uuid);
 }
}
