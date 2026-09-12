package network.azusake.halo.client;

import net.fabricmc.loader.api.FabricLoader;
import network.azusake.halo.core.runtime.LocalOwnership;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import java.net.SocketAddress;

/** Source/binary-compatible local API; paths and file I/O belong to Fabric. */
public final class HaloLocalManager {
    private static final HaloLocalManager INSTANCE=new HaloLocalManager();
    private final LocalOwnership ownership=new LocalOwnership(new LocalOwnership.TextStore() {
        private Path file(){return FabricLoader.getInstance().getConfigDir().resolve("halo-azusake/halo_local_halos.json");}
        public String read() throws IOException { return Files.exists(file())?Files.readString(file()):null; }
        public void write(String text) throws IOException { Files.createDirectories(file().getParent());Files.writeString(file(),text); }
    });
    private HaloLocalManager() {}
    public static HaloLocalManager getInstance(){return INSTANCE;}
    public void showHalo(String key,UUID uuid,net.minecraft.util.Identifier id){showHalo(key,uuid,network.azusake.halo.platform.PlatformTypes.core(id));}
    public void showHalo(String key,UUID uuid,network.azusake.halo.core.Identifier id){ownership.showHalo(key,uuid,id);}
    public void hideHalo(String key,UUID uuid){ownership.hideHalo(key,uuid);}
    public Optional<net.minecraft.util.Identifier> getHalo(String key,UUID uuid){return getCoreHalo(key,uuid).map(network.azusake.halo.platform.PlatformTypes::game);}
    public Optional<network.azusake.halo.core.Identifier> getCoreHalo(String key,UUID uuid){return ownership.getHalo(key,uuid);}
    public Set<UUID> getHalosForServer(String key){return ownership.getHalosForServer(key);}
    public void restoreInto(String key,network.azusake.halo.core.runtime.ClientPort client){ownership.restoreInto(key,client);}
    public void clearServer(String key){ownership.clearServer(key);}
    public static String serverKeyFromAddress(SocketAddress address){return LocalOwnership.serverKeyFromAddress(address);}
}
