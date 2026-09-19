package network.azusake.halo.network;

import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs;
import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.client.HaloScepterScreen;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.json.HaloJsonLoader;
import static network.azusake.halo.platform.PlatformTypes.game;

/** Client side of Halo's 1.21 CustomPayload transport. */
@Environment(EnvType.CLIENT)
public final class HaloNetworkClient {
    private HaloNetworkClient() {}

    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(HaloPayloads.Sync.ID, (payload, context) -> {
            var incoming = HaloPacketCodec.decodeSnapshot(payload.buf());
            context.client().execute(() ->
                network.azusake.halo.platform.HaloClientState.get().replaceAllClientHalos(incoming));
        });
        ClientPlayNetworking.registerGlobalReceiver(HaloPayloads.Update.ID, (payload, context) -> {
            var update = HaloPacketCodec.decodeUpdate(payload.buf());
            context.client().execute(() -> {
                var runtime = network.azusake.halo.platform.HaloClientState.get();
                if (update.attach()) runtime.attach(update.entity(), update.definition(), true);
                else runtime.hide(update.entity(), update.definition());
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(HaloPayloads.Hello.ID, (payload, context) ->
            context.client().execute(() -> HaloPhaseTracker.getInstance().transitionToMultiplayer()));
        ClientPlayNetworking.registerGlobalReceiver(HaloPayloads.ScepterOpen.ID, (payload, context) -> {
            var buf = payload.buf();
            int targetEntityId = buf.readInt();
            UUID targetUuid = HaloNetwork.readUuid(buf);
            String targetName = buf.readUtf(128);
            context.client().execute(() -> context.client().gui.setScreen(
                new HaloScepterScreen(targetEntityId, targetUuid, targetName)));
        });
        ClientPlayNetworking.registerGlobalReceiver(HaloPayloads.ScepterCloseScreen.ID, (payload, context) ->
            context.client().execute(() -> {
                if (context.client().gui.screen() instanceof HaloScepterScreen) context.client().gui.setScreen(null);
            }));
    }

    public static void sendScepterSelection(Identifier definitionId) {
        if (!ClientPlayNetworking.canSend(HaloPayloads.ScepterSelect.ID)) return;
        var buf = FriendlyByteBufs.create();
        buf.writeIdentifier(game(definitionId));
        ClientPlayNetworking.send(new HaloPayloads.ScepterSelect(buf));
    }

    public static void sendScepterClose() {
        if (ClientPlayNetworking.canSend(HaloPayloads.ScepterClose.ID))
            ClientPlayNetworking.send(new HaloPayloads.ScepterClose(FriendlyByteBufs.create()));
    }

    public static void sendScepterRemoveSelf() {
        if (ClientPlayNetworking.canSend(HaloPayloads.ScepterRemoveSelf.ID))
            ClientPlayNetworking.send(new HaloPayloads.ScepterRemoveSelf(FriendlyByteBufs.create()));
    }

    public static void sendDefsReport() {
        if (!ClientPlayNetworking.canSend(HaloPayloads.DefsReport.ID)) return;
        var definitions = HaloJsonLoader.getDefinitions();
        var buf = FriendlyByteBufs.create();
        buf.writeInt(definitions.size());
        for (Identifier id : definitions.keySet()) buf.writeIdentifier(game(id));
        ClientPlayNetworking.send(new HaloPayloads.DefsReport(buf));
    }
}
