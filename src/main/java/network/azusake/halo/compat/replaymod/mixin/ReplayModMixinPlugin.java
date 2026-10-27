package network.azusake.halo.compat.replaymod.mixin;

import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;

/** Probe bytecode without loading optional target classes before Mixin transforms them. */
public final class ReplayModMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String pkg){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin){
        try{
            var node=MixinService.getService().getBytecodeProvider().getClassNode(target);
            String method=mixin.endsWith("PacketListenerMixin")?"save":mixin.endsWith("ReplayHandlerMixin")?"doJump"
                :mixin.endsWith("MarkerProcessorMixin")?"apply":mixin.endsWith("DimensionTrackerMixin") || mixin.endsWith("SquashFilterMixin")?"onPacket":"updateForNextFrame";
            return node.methods.stream().anyMatch(m->m.name.equals(method));
        }catch(Exception absent){return false;}
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){
        if(node.methods.stream().anyMatch(method->method.name.contains("halo$")))
            network.azusake.halo.compat.replaymod.ReplayModHooks.applied(target);
    }
}
