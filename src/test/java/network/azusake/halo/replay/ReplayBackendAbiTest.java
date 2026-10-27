package network.azusake.halo.replay;

import java.nio.file.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests actual release bytecode; it does not substitute for runtime Mixin or game validation. */
class ReplayBackendAbiTest {
    @Test void officialReplayModRetainsRequiredCallbacksPacketIndexAndOutputBoundaries()throws Exception{
        try(var zip=fixture("HALO_REPLAYMOD_TEST_JAR")){
            method(zip,"com.replaymod.recording.packet.PacketListener","save","(Lnet/minecraft/class_2596;)V");
            method(zip,"com.replaymod.replay.ReplayHandler","getReplayFile",null);
            method(zip,"com.replaymod.replay.ReplayHandler","doJump","(IZ)V");
            method(zip,"com.replaymod.render.rendering.VideoRenderer","updateForNextFrame","()F");
            var video=node(zip,"com.replaymod.render.rendering.VideoRenderer");
            assertTrue(video.fields.stream().anyMatch(f->f.name.equals("timeline")));
            for(String callback:new String[]{"ReplayOpenedCallback","ReplayClosingCallback"})
                assertTrue(node(zip,"com.replaymod.replay.events."+callback).fields.stream().anyMatch(f->f.name.equals("EVENT")));
            method(zip,"com.replaymod.replaystudio.io.ReplayInputStream","readPacket",null);
            method(zip,"com.replaymod.editor.gui.MarkerProcessor","apply",null);
            method(zip,"com.replaymod.replaystudio.filter.DimensionTracker","onPacket",null);
            method(zip,"com.replaymod.replaystudio.filter.SquashFilter","copy",null);
            method(zip,"com.replaymod.replaystudio.filter.SquashFilter","onEnd",null);
            method(zip,"com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations","on",null);
            method(zip,"com.replaymod.lib.de.johni0702.minecraft.gui.utils.EventRegistrations","register","()V");
            method(zip,"com.replaymod.core.versions.MCVer","getPacketTypeRegistry","(Z)Lcom/replaymod/replaystudio/protocol/PacketTypeRegistry;");
        }
    }
    @Test void officialFlashbackHasSnapshotTimingAndExactPerOutputFrameCalls()throws Exception{
        try(var zip=fixture("HALO_FLASHBACK_TEST_JAR")){
            method(zip,"com.moulberry.flashback.record.Recorder","writeCustomSnapshot","(Ljava/util/function/Consumer;)V");
            method(zip,"com.moulberry.flashback.io.ReplayReader","handleSnapshot",null);
            var server=node(zip,"com.moulberry.flashback.playback.ReplayServer");
            assertTrue(server.fields.stream().anyMatch(f->f.name.equals("currentTick")));
            assertTrue(server.fields.stream().anyMatch(f->f.name.equals("playableChunksByStart")));
            var export=method(zip,"com.moulberry.flashback.exporting.ExportJob","doExport",null);
            assertTrue(invokes(export,"com/moulberry/flashback/exporting/ExportJob","setServerTickAndWait","(Lcom/moulberry/flashback/playback/ReplayServer;IZ)V"));
            assertTrue(invokes(export,"com/moulberry/flashback/state/EditorState","applyKeyframes","(Lcom/moulberry/flashback/keyframe/handler/KeyframeHandler;F)V"));
        }
    }
    private static ZipFile fixture(String variable)throws Exception{
        String path=System.getenv(variable);Assumptions.assumeTrue(path!=null && !path.isBlank(),"set "+variable+" for release verification");
        assertTrue(Files.isRegularFile(Path.of(path)));return new ZipFile(path);
    }
    private static ClassNode node(ZipFile zip,String name)throws Exception{
        var entry=zip.getEntry(name.replace('.','/')+".class");assertNotNull(entry,name);
        var node=new ClassNode();try(var in=zip.getInputStream(entry)){new ClassReader(in).accept(node,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);}return node;
    }
    private static MethodNode method(ZipFile zip,String owner,String name,String descriptor)throws Exception{
        return node(zip,owner).methods.stream().filter(m->m.name.equals(name) && (descriptor==null || descriptor.equals(m.desc))).findFirst().orElseThrow(()->new AssertionError(owner+"."+name));
    }
    private static boolean invokes(MethodNode method,String owner,String name,String descriptor){
        for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(descriptor))return true;
        return false;
    }
}
