package network.azusake.halo.manager;

import network.azusake.halo.HaloMod;
import network.azusake.halo.api.v2.HaloApi;
import network.azusake.halo.api.v2.HaloSource;
import network.azusake.halo.config.HaloConfig;
import network.azusake.halo.config.HaloModConfigStore;
import network.azusake.halo.config.HaloSourcePriorityStore;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.core.runtime.HaloSourceHost;
import network.azusake.halo.core.runtime.ServerRuntime;
import network.azusake.halo.data.*;
import network.azusake.halo.lifecycle.HaloWorldSaveData;
import network.azusake.halo.network.HaloNetwork;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;

/** Server facade joining the platform store/transport to core source arbitration. */
public final class HaloManager {
    public static final String WORLD_DATA_SOURCE_ID = "halo:world_data";
    private static final HaloManager INSTANCE = new HaloManager();
    private final HaloSource worldDataSource = HaloApi.registerSource(WORLD_DATA_SOURCE_ID, 0);
    private final Set<String> reportedConflicts = new HashSet<>();
    private MinecraftServer server;
    private ServerRuntime runtime;
    private HaloSourceHost sourceHost;
    private HaloConfig config = new HaloConfig();

    private HaloManager() {}
    public static HaloManager getInstance() { return INSTANCE; }

    public void bind(MinecraftServer value) {
        if (server == value && runtime != null) return;
        if (sourceHost != null) sourceHost.close();
        server = value;
        config = new HaloConfig();
        reportedConflicts.clear();
        network.azusake.halo.platform.IntegratedBridge.clearDiagnostics();
        runtime = new ServerRuntime(new ServerRuntime.OwnershipStore() {
            private HaloWorldSaveData data() { return HaloWorldSaveData.get(value.getOverworld()); }
            public Identifier get(UUID id) { return data().get(id); }
            public void set(UUID id, Identifier def) { data().set(id, def); }
            public void remove(UUID id) { data().remove(id); }
        }, new ServerRuntime.Updates() {
            public void attach(UUID id, Identifier def) {
                HaloNetwork.sendHaloAttach(value, id, def);
                LivingEntity entity = findEntity(id);
                if (entity != null) HaloEntityData.attachHalo(entity, def);
            }
            public void remove(UUID id, Identifier def) {
                HaloNetwork.sendHaloRemove(value, id, def);
                LivingEntity entity = findEntity(id);
                if (entity != null) HaloEntityData.removeHalo(entity);
            }
        });
        sourceHost = new HaloSourceHost(runtime, HaloSourcePriorityStore.priorities());
        syncPriorityFileAndWarnings();
    }

    public void stop() {
        if (sourceHost != null) sourceHost.close();
        sourceHost = null; runtime = null; server = null; reportedConflicts.clear();
        network.azusake.halo.platform.IntegratedBridge.clearDiagnostics();
    }

    public void showHaloOn(LivingEntity entity, Identifier definition) {
        if (entity.getServer() == null) return;
        bind(entity.getServer());
        HaloWorldSaveData.get(entity.getServer().getOverworld()).set(entity.getUuid(), definition);
        worldDataSource.set(entity.getUuid(), definition.toString());
        sourceHost.activate(entity.getUuid());
        syncMirror(entity);
    }

    public void showHaloOn(LivingEntity entity, net.minecraft.util.Identifier definition) {
        showHaloOn(entity, network.azusake.halo.platform.PlatformTypes.core(definition));
    }

    /** Activate all retained candidates, then restore Halo's own persisted candidate if present. */
    public void restore(LivingEntity entity) {
        if (entity.getServer() == null) return;
        bind(entity.getServer());
        Identifier id = HaloWorldSaveData.get(entity.getServer().getOverworld()).get(entity.getUuid());
        if (id != null) worldDataSource.set(entity.getUuid(), id.toString());
        else sourceHost.activate(entity.getUuid());
        syncMirror(entity);
    }

    public void entityLoaded(LivingEntity entity) {
        if (entity.getServer() == null) return;
        bind(entity.getServer()); sourceHost.activate(entity.getUuid()); syncMirror(entity);
    }

    public void hideHaloOn(LivingEntity entity) {
        if (entity.getServer() == null) return;
        bind(entity.getServer());
        HaloWorldSaveData.get(entity.getServer().getOverworld()).remove(entity.getUuid());
        worldDataSource.clear(entity.getUuid());
        syncMirror(entity);
    }

    public void died(LivingEntity entity, boolean player) {
        if (entity.getServer() == null) return;
        bind(entity.getServer());
        if (player) sourceHost.deactivate(entity.getUuid());
        else {
            HaloWorldSaveData.get(entity.getServer().getOverworld()).remove(entity.getUuid());
            sourceHost.forget(entity.getUuid());
        }
        HaloEntityData.removeHalo(entity);
    }

    public void removeHalo(UUID uuid, MinecraftServer ignored) {
        if (runtime != null && sourceHost != null) sourceHost.deactivate(uuid);
    }
    public void forceRemoveHalo(UUID uuid) { if (runtime != null && sourceHost != null) sourceHost.deactivate(uuid); }

    public void tickAll(MinecraftServer server) {
        bind(server);
        syncPriorityFileAndWarnings();
        for (UUID uuid : runtime.snapshot().keySet()) if (findEntity(uuid) == null) sourceHost.deactivate(uuid);
    }

    public HaloSourceHost.PrioritySnapshot prioritySnapshot() {
        return sourceHost == null ? new HaloSourceHost.PrioritySnapshot(Map.of(), HaloSourcePriorityStore.priorities())
            : sourceHost.priorities();
    }

    public HaloSourcePriorityStore.LoadResult reloadPriorities() {
        var result = HaloSourcePriorityStore.load();
        if (result.success() && sourceHost != null) {
            sourceHost.reconfigure(HaloSourcePriorityStore.priorities());
            reportedConflicts.clear(); syncPriorityFileAndWarnings();
        }
        return result;
    }

    public void setPriority(String sourceId, int priority) {
        HaloSourcePriorityStore.set(sourceId, priority);
        if (sourceHost != null) {
            sourceHost.reconfigure(HaloSourcePriorityStore.priorities());
            reportedConflicts.clear(); syncPriorityFileAndWarnings();
        }
    }

    public List<HaloSourceHost.Candidate> candidates(UUID entity) {
        return sourceHost == null ? List.of() : sourceHost.candidates(entity);
    }
    public ServerRuntime.Selection selection(UUID entity) { return runtime == null ? null : runtime.selection(entity); }

    public void notifyPriorityConflicts(ServerPlayerEntity player) {
        if (!player.hasPermissionLevel(HaloModConfigStore.getPermissionLevel()) || sourceHost == null) return;
        sourceHost.priorities().entries().values().stream()
            .filter(entry -> entry.status() == HaloSourceHost.PriorityStatus.DISABLED_CONFLICT)
            .forEach(entry -> player.sendMessage(priorityConflictMessage(entry), false));
    }

    private void syncPriorityFileAndWarnings() {
        if (sourceHost == null) return;
        var snapshot = sourceHost.priorities();
        HaloSourcePriorityStore.merge(snapshot.persistedPriorities());
        Set<String> currentConflicts = new HashSet<>();
        snapshot.entries().values().stream()
            .filter(entry -> entry.status() == HaloSourceHost.PriorityStatus.DISABLED_CONFLICT)
            .forEach(entry -> {
                currentConflicts.add(entry.sourceId());
                if (reportedConflicts.add(entry.sourceId())) {
                    Text message = priorityConflictMessage(entry);
                    HaloMod.LOGGER.warn(message.getString());
                    if (server != null) server.getPlayerManager().getPlayerList().stream()
                        .filter(player -> player.hasPermissionLevel(HaloModConfigStore.getPermissionLevel()))
                        .forEach(player -> player.sendMessage(message, false));
                }
            });
        reportedConflicts.retainAll(currentConflicts);
    }

    private Text priorityConflictMessage(HaloSourceHost.PriorityEntry entry) {
        String fallback = entry.configuredPriority() == Integer.MIN_VALUE
            ? "; it cannot auto-demote below Integer.MIN_VALUE"
            : "; fallback priority " + (entry.configuredPriority() - 1) + " conflicts with '"
                + entry.fallbackConflictWith() + "'";
        return Text.literal("[Halo] Source '" + entry.sourceId() + "' is disabled: requested priority "
            + entry.configuredPriority() + " conflicts with '" + entry.conflictWith() + "'" + fallback + ". Config: "
            + HaloSourcePriorityStore.file() + ". Use /halo priority set " + entry.sourceId()
            + " <priority> to apply and persist immediately without restart. After a manual edit, run /halo priority reload "
            + "without restart; otherwise the edit is read on the next server/game restart.");
    }

    private void syncMirror(LivingEntity entity) {
        Identifier selected = runtime == null ? null : runtime.get(entity.getUuid());
        if (selected == null) HaloEntityData.removeHalo(entity); else HaloEntityData.attachHalo(entity, selected);
    }

    private LivingEntity findEntity(UUID uuid) {
        if (server == null) return null;
        for (var world : server.getWorlds()) {
            var entity = world.getEntity(uuid);
            if (entity instanceof LivingEntity living && living.isAlive()) return living;
        }
        return null;
    }

    public HaloConfig getConfig() { return config; }
    public void publishConfig() {
        if (server != null && !server.isDedicated())
            network.azusake.halo.platform.IntegratedBridge.config.accept(
                network.azusake.halo.core.runtime.RuntimeConfigSnapshot.of(config));
    }
    public HaloInstance getHaloInstance(UUID uuid) {
        Identifier id = runtime == null ? null : runtime.get(uuid); if (id == null) return null;
        var status = server.isDedicated() ? null : network.azusake.halo.platform.IntegratedBridge.status(uuid, id);
        long created = status == null ? runtime.createdAt(uuid) : status.createdAt();
        HaloInstance view = new HaloInstance(uuid, id, () -> created);
        view.setNeedsSnap(status != null && status.needsSnap());
        if (status != null) { view.setTransitionState(status.transition()); if (!status.active()) view.deactivate(); }
        return view;
    }
    public HaloInstance getInstance(UUID uuid) { return getHaloInstance(uuid); }
    public Map<UUID, HaloInstance> getActiveHalos() {
        Map<UUID, HaloInstance> views = new LinkedHashMap<>();
        if (runtime != null) runtime.snapshot().keySet().forEach(id -> views.put(id, getHaloInstance(id)));
        return Map.copyOf(views);
    }
    public Collection<HaloInstance> getAllInstances() { return getActiveHalos().values(); }
    public Map<UUID, Identifier> ownershipSnapshot() { return runtime == null ? Map.of() : runtime.snapshot(); }
    public int getActiveCount() { return runtime == null ? 0 : runtime.snapshot().size(); }
}
