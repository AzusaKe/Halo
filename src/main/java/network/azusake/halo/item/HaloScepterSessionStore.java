package network.azusake.halo.item;

import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-owned player-to-target locks for open halo-scepter screens. */
public final class HaloScepterSessionStore {

    public record Session(UUID playerUuid, UUID targetUuid, Identifier worldId) {
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    public void open(UUID playerUuid, UUID targetUuid, Identifier worldId) {
        sessions.put(playerUuid, new Session(playerUuid, targetUuid, worldId));
    }

    public Session get(UUID playerUuid) {
        return sessions.get(playerUuid);
    }

    public void close(UUID playerUuid) {
        sessions.remove(playerUuid);
    }

    public Collection<Session> snapshot() {
        return new ArrayList<>(sessions.values());
    }

    public Collection<Session> closeTarget(UUID targetUuid) {
        Collection<Session> removed = new ArrayList<>();
        for (Session session : snapshot()) {
            if (session.targetUuid().equals(targetUuid)
                && sessions.remove(session.playerUuid(), session)) {
                removed.add(session);
            }
        }
        return removed;
    }

    public int size() {
        return sessions.size();
    }
}
