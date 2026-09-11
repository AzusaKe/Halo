package network.azusake.halo.item;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HaloScepterSessionStoreTest {

    private static final Identifier OVERWORLD =
        Identifier.fromNamespaceAndPath("minecraft", "overworld");

    @Test
    void openingAgainReplacesTheLockedTarget() {
        HaloScepterSessionStore store = new HaloScepterSessionStore();
        UUID player = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        store.open(player, first, OVERWORLD);
        store.open(player, second, OVERWORLD);

        assertEquals(second, store.get(player).targetUuid());
        assertEquals(1, store.size());
    }

    @Test
    void closeReleasesAPlayerSession() {
        HaloScepterSessionStore store = new HaloScepterSessionStore();
        UUID player = UUID.randomUUID();
        store.open(player, UUID.randomUUID(), OVERWORLD);

        store.close(player);

        assertNull(store.get(player));
        assertEquals(0, store.size());
    }

    @Test
    void invalidatingTargetClosesEveryPlayerLockedToIt() {
        HaloScepterSessionStore store = new HaloScepterSessionStore();
        UUID target = UUID.randomUUID();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        UUID unaffectedPlayer = UUID.randomUUID();

        store.open(firstPlayer, target, OVERWORLD);
        store.open(secondPlayer, target, OVERWORLD);
        store.open(unaffectedPlayer, UUID.randomUUID(), OVERWORLD);

        assertEquals(2, store.closeTarget(target).size());
        assertNull(store.get(firstPlayer));
        assertNull(store.get(secondPlayer));
        assertEquals(1, store.size());
    }
}
