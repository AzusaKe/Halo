package network.azusake.halo.compat.flashback.mixin;

import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;

/** Capability probe without loading optional classes ahead of Mixin. */
public final class FlashbackMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String pkg){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin){
        try{
            var node=MixinService.getService().getBytecodeProvider().getClassNode(target);
            String method=mixin.endsWith("FlashbackLifecycleMixin")?"updateIsInReplay":mixin.endsWith("RecorderMixin")?"writeCustomSnapshot":mixin.endsWith("ReplayReaderMixin")?"handleSnapshot"
                :mixin.endsWith("ReplayServerMixin")?"handleGamePacket":"doExport";
            return node.methods.stream().anyMatch(m->m.name.equals(method));
        }catch(Exception absent){return false;}
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){
        if(node.methods.stream().anyMatch(method->method.name.contains("halo$")))
            network.azusake.halo.compat.flashback.FlashbackHooks.applied(target);
    }
}
