package network.azusake.halo.network;

import java.util.UUID;


import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraft.client.Minecraft;

import network.azusake.halo.client.HaloPhaseTracker;
import network.azusake.halo.client.HaloScepterScreen;
import network.azusake.halo.core.Identifier;
import network.azusake.halo.json.HaloJsonLoader;
import static network.azusake.halo.platform.PlatformTypes.game;

/** Client side of Halo's 1.21 CustomPayload transport. */

public final class HaloNetworkClient {
    private HaloNetworkClient() {}

    public static void registerReceivers(PayloadRegistrar registrar) {
        registrar.playToClient(HaloPayloads.Sync.ID, HaloPayloads.Sync.CODEC, (payload, context) -> {
            var incoming = HaloPacketCodec.decodeSnapshot(payload.buf());
            Minecraft.getInstance().execute(() ->
                network.azusake.halo.platform.HaloClientState.get().replaceAllClientHalos(incoming));
        });
        registrar.playToClient(HaloPayloads.Update.ID, HaloPayloads.Update.CODEC, (payload, context) -> {
            var update = HaloPacketCodec.decodeUpdate(payload.buf());
            Minecraft.getInstance().execute(() -> {
                var runtime = network.azusake.halo.platform.HaloClientState.get();
                if (update.attach()) runtime.attach(update.entity(), update.definition(), true);
                else runtime.hide(update.entity(), update.definition());
            });
        });
        registrar.playToClient(HaloPayloads.Hello.ID, HaloPayloads.Hello.CODEC, (payload, context) ->
            Minecraft.getInstance().execute(() -> HaloPhaseTracker.getInstance().transitionToMultiplayer()));
        registrar.playToClient(HaloPayloads.ScepterOpen.ID, HaloPayloads.ScepterOpen.CODEC, (payload, context) -> {
            var buf = payload.buf();
            int targetEntityId = buf.readInt();
            UUID targetUuid = HaloNetwork.readUuid(buf);
            String targetName = buf.readUtf(128);
            Minecraft.getInstance().execute(() -> Minecraft.getInstance().gui.setScreen(
                new HaloScepterScreen(targetEntityId, targetUuid, targetName)));
        });
        registrar.playToClient(HaloPayloads.ScepterCloseScreen.ID, HaloPayloads.ScepterCloseScreen.CODEC, (payload, context) ->
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().gui.screen() instanceof HaloScepterScreen) Minecraft.getInstance().gui.setScreen(null);
            }));
    }

    private static boolean canSend(net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> type) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type);
    }

    public static void sendScepterSelection(Identifier definitionId) {
        if (!canSend(HaloPayloads.ScepterSelect.ID)) return;
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeIdentifier(game(definitionId));
        ClientPacketDistributor.sendToServer(new HaloPayloads.ScepterSelect(buf));
    }

    public static void sendScepterClose() {
        if (canSend(HaloPayloads.ScepterClose.ID))
            ClientPacketDistributor.sendToServer(new HaloPayloads.ScepterClose(new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer())));
    }

    public static void sendScepterRemoveSelf() {
        if (canSend(HaloPayloads.ScepterRemoveSelf.ID))
            ClientPacketDistributor.sendToServer(new HaloPayloads.ScepterRemoveSelf(new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer())));
    }

    public static void sendDefsReport() {
        if (!canSend(HaloPayloads.DefsReport.ID)) return;
        var definitions = HaloJsonLoader.getDefinitions();
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeInt(definitions.size());
        for (Identifier id : definitions.keySet()) buf.writeIdentifier(game(id));
        ClientPacketDistributor.sendToServer(new HaloPayloads.DefsReport(buf));
    }
}
