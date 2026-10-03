package network.azusake.halo.compat.caustica.mixin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import net.neoforged.fml.loading.LoadingModList;
import network.azusake.halo.compat.caustica.CausticaActivation;

/** Optional bridge installs with Caustica present; an explicit startup opt-out disables it. */
public final class CausticaMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String p){} public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){return CausticaActivation.ENABLED && LoadingModList.get()!=null && LoadingModList.get().getModFileById("caustica")!=null;}
    public void acceptTargets(Set<String> mine,Set<String> others){} public List<String> getMixins(){return null;}
    public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
