package network.azusake.halo.manager;

import network.azusake.halo.config.HaloConfig;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.runtime.ServerRuntime;
import network.azusake.halo.data.*;
import network.azusake.halo.lifecycle.HaloWorldSaveData;
import network.azusake.halo.network.HaloNetwork;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import java.util.*;

/** Legacy server facade. Authoritative core state never contains client animation instances. */
public final class HaloManager {
    private static final HaloManager INSTANCE=new HaloManager();
    private MinecraftServer server;
    private ServerRuntime runtime;
    private HaloConfig config=new HaloConfig();
    private HaloManager() {}
    public static HaloManager getInstance() { return INSTANCE; }
    public void bind(MinecraftServer value) {
        if(server==value && runtime!=null)return;
        server=value;config=new HaloConfig();network.azusake.halo.platform.IntegratedBridge.clearDiagnostics();
        runtime=new ServerRuntime(new ServerRuntime.OwnershipStore() {
            private HaloWorldSaveData data(){return HaloWorldSaveData.get(value.getOverworld());}
            public Identifier get(UUID id){return data().get(id);}
            public void set(UUID id,Identifier def){data().set(id,def);}
            public void remove(UUID id){data().remove(id);}
        },new ServerRuntime.Updates() {
            public void attach(UUID id,Identifier def){HaloNetwork.sendHaloAttach(value,id,def);}
            public void remove(UUID id,Identifier def){HaloNetwork.sendHaloRemove(value,id,def);}
        });
    }
    public void stop() { runtime=null;server=null;network.azusake.halo.platform.IntegratedBridge.clearDiagnostics(); }
    public void showHaloOn(LivingEntity entity,Identifier definition) {
        if(entity.getServer()==null)return;
        bind(entity.getServer());runtime.show(entity.getUuid(),definition);HaloEntityData.attachHalo(entity,definition);
    }
    public void showHaloOn(LivingEntity entity,net.minecraft.util.Identifier definition) {
        showHaloOn(entity,network.azusake.halo.platform.PlatformTypes.core(definition));
    }
    public void restore(LivingEntity entity) {
        if(entity.getServer()==null)return;bind(entity.getServer());
        Identifier id=runtime.restore(entity.getUuid());if(id!=null)HaloEntityData.attachHalo(entity,id);
    }
    public void hideHaloOn(LivingEntity entity) {
        if(entity.getServer()==null)return;bind(entity.getServer());
        if(runtime.hide(entity.getUuid()))HaloEntityData.removeHalo(entity);
    }
    public void died(LivingEntity entity,boolean player) {
        if(entity.getServer()==null)return;bind(entity.getServer());runtime.died(entity.getUuid(),player);HaloEntityData.removeHalo(entity);
    }
    public void removeHalo(UUID uuid,MinecraftServer server) { if(runtime!=null)runtime.unload(uuid); }
    public void forceRemoveHalo(UUID uuid) { if(runtime!=null)runtime.unload(uuid); }
    public void tickAll(MinecraftServer server) {
        bind(server);
        for(UUID uuid:runtime.snapshot().keySet()) {
            boolean found=false;
            for(var world:server.getWorlds()) {var e=world.getEntity(uuid);if(e instanceof LivingEntity && e.isAlive()){found=true;break;}}
            if(!found)runtime.unload(uuid);
        }
    }
    public HaloConfig getConfig() { return config; }
    public void publishConfig() {
        if(server!=null && !server.isDedicated())network.azusake.halo.platform.IntegratedBridge.config.accept(network.azusake.halo.core.runtime.RuntimeConfigSnapshot.of(config));
    }
    public HaloInstance getHaloInstance(UUID uuid) {
        Identifier id=runtime==null?null:runtime.get(uuid);if(id==null)return null;
        var status = server.isDedicated() ? null : network.azusake.halo.platform.IntegratedBridge.status(uuid,id);
        long created = status == null ? runtime.createdAt(uuid) : status.createdAt();
        HaloInstance view = new HaloInstance(uuid,id,() -> created);
        view.setNeedsSnap(status != null && status.needsSnap());
        if (status != null) { view.setTransitionState(status.transition()); if (!status.active()) view.deactivate(); }
        return view;
    }
    public HaloInstance getInstance(UUID uuid) { return getHaloInstance(uuid); }
    public Map<UUID,HaloInstance> getActiveHalos() {
        Map<UUID,HaloInstance> views=new LinkedHashMap<>();
        if(runtime!=null)runtime.snapshot().keySet().forEach(id->views.put(id,getHaloInstance(id)));
        return Map.copyOf(views);
    }
    public Collection<HaloInstance> getAllInstances() { return getActiveHalos().values(); }
    public Map<UUID,Identifier> ownershipSnapshot() { return runtime==null ? Map.of() : runtime.snapshot(); }
    public int getActiveCount() { return runtime==null?0:runtime.snapshot().size(); }
}
