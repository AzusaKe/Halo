package network.azusake.halo.compat.caustica;

import dev.comfyfluffy.caustica.minecraft.api.*;

/** NeoForge's service entry point; absent Caustica never loads this class. */
public final class CausticaExtension implements MinecraftExtension {
    @Override public void registerMinecraft(MinecraftApi api) {
        if(CausticaBridge.ENABLED)api.sessions().add(CausticaSession::new);
    }
}
