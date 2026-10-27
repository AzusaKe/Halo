package network.azusake.halo.compat.replaymod;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import network.azusake.halo.api.v2.replay.*;
import network.azusake.halo.replay.*;
import org.slf4j.LoggerFactory;

/** ReplayMod objects and source/output time conversion stay confined to this backend. */
public final class ReplayModBackend {
    private static final ReplayModBackend INSTANCE=new ReplayModBackend();
    private Object recorder,handler,video,recordingWorld;
    private ReplayRecordingSession recording;
    private ReplayPlaybackSession playback;
    private CompletableFuture<ReplayTimeline> indexing;
    private ReplayTimeline timeline;
    private UUID track;
    private boolean writing,failed,seek,exportSample;
    private long samples,epoch,lastSource=-1,lastSnapshotMillis;
    private ReplayModBackend(){}
    public static void initialize(){
        if(ReplayModBackend.class.getClassLoader().getResource("com/replaymod/replay/ReplayModReplay.class")==null)return;
        try{
            for(String target:new String[]{"com.replaymod.recording.packet.PacketListener","com.replaymod.replay.ReplayHandler",
                "com.replaymod.render.rendering.VideoRenderer","com.replaymod.editor.gui.MarkerProcessor",
                "com.replaymod.replaystudio.filter.DimensionTracker","com.replaymod.replaystudio.filter.SquashFilter"})
                {Class.forName(target,false,ReplayModBackend.class.getClassLoader());
                 if(!ReplayModHooks.has(target))throw new IllegalStateException("required replay boundary hook is unavailable: "+target);}
            ReplayModAccess.event("com.replaymod.replay.events.ReplayOpenedCallback",args->INSTANCE.open(args[0]));
            ReplayModAccess.event("com.replaymod.replay.events.ReplayClosingCallback",args->INSTANCE.close());
            ReplayModAccess.event("com.replaymod.render.events.ReplayRenderCallback$Pre",args->{
                try{INSTANCE.startExport(args[0]);}catch(Exception failure){INSTANCE.failure(failure);}
            });
            ReplayModAccess.event("com.replaymod.render.events.ReplayRenderCallback$Post",args->INSTANCE.endExport());
            ReplayClient.registerPreparation(INSTANCE::prepare);
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->INSTANCE.recordingTick());
            LoggerFactory.getLogger("halo").info("[Halo ReplayMod] public callbacks registered; optional boundary hooks enabled");
        }catch(Exception | LinkageError failure){INSTANCE.failure(failure);}
    }
    public static void recorderCreated(Object listener){
        var client=MinecraftClient.getInstance();
        if(client.isOnThread())INSTANCE.beginRecording(listener);else client.execute(()->INSTANCE.beginRecording(listener));
    }
    public static void beforeSave(Object listener){if(MinecraftClient.getInstance().isOnThread())INSTANCE.ensureSnapshot(listener);}
    public static void seekRequested(){INSTANCE.seek=true;}
    public static void outputFrame(Object renderer){INSTANCE.sampleExport(renderer);}
    public static void exportEnded(){INSTANCE.endExport();}
    private void beginRecording(Object listener){
        if(failed || ReplayClient.isPlayback() || recorder==listener && recording!=null && recording.isValid())return;
        if(recording!=null && recording.isValid())recording.close();
        recorder=listener;track=UUID.randomUUID();recordingWorld=null;
        recording=HaloReplayApi.openRecording("halo:replaymod",event->{
            if(recordingWorld!=null)save(new ReplayPayloadCodec.Record(track,null,event));
        }).orElse(null);
    }
    private void ensureSnapshot(Object listener){
        if(writing || failed || ReplayClient.isPlayback())return;
        beginRecording(listener);
        var client=MinecraftClient.getInstance();
        if(recording!=null && client.world!=null && client.getNetworkHandler()!=null && recordingWorld!=client.world){
            recordingWorld=client.world;save(new ReplayPayloadCodec.Record(track,recording.snapshot(),null));
            lastSnapshotMillis=System.currentTimeMillis();
        }
    }
    private void recordingTick(){
        if(failed || ReplayClient.isPlayback())return;
        try{
            Object module=ReplayModAccess.singleton("com.replaymod.recording.ReplayModRecording");
            Object listener=ReplayModAccess.call(ReplayModAccess.call(module,"getConnectionEventHandler"),"getPacketListener");
            if(listener==null){if(recording!=null && recording.isValid())recording.close();recording=null;recorder=null;return;}
            ensureSnapshot(listener);
            if(recording!=null && recordingWorld!=null && System.currentTimeMillis()-lastSnapshotMillis>=2000){
                save(new ReplayPayloadCodec.Record(track,recording.snapshot(),null));lastSnapshotMillis=System.currentTimeMillis();
            }
        }catch(Exception failure){failure(failure);}
    }
    private void save(ReplayPayloadCodec.Record record){
        if(writing || recorder==null)return;
        writing=true;
        try{ReplayModAccess.call(recorder,"save",ReplayTransport.packet(record));}
        catch(Exception failure){failure(failure);}
        finally{writing=false;}
    }
    private void open(Object value){
        close();handler=value;failed=false;seek=true;timeline=null;lastSource=-1;
        playback=HaloReplayApi.openPlayback("halo:replaymod").orElse(null);
        indexing=CompletableFuture.supplyAsync(()->{
            try{return ReplayModIndex.read(value);}catch(Exception failure){throw new java.util.concurrent.CompletionException(failure);}
        });
        LoggerFactory.getLogger("halo").info("[Halo ReplayMod] indexing original Halo packet track; rendering waits for index readiness");
    }
    private void close(){
        if(playback!=null && playback.isValid())playback.close();
        if(indexing!=null)indexing.cancel(true);
        playback=null;handler=null;timeline=null;indexing=null;video=null;exportSample=false;
        ReplayClient.setPreparing(false);
    }
    private void prepare(){
        if(failed || handler==null)return;
        try{
            if(playback==null || !playback.isValid()){
                playback=HaloReplayApi.openPlayback("halo:replaymod").orElse(null);
                if(timeline!=null)timeline.reset();seek=true;
            }
            if(playback==null)return;
            if(timeline==null){
                if(indexing==null || !indexing.isDone()){ReplayClient.setPreparing(true);return;}
                timeline=indexing.join();ReplayClient.setPreparing(false);
                if(!timeline.hasTrack())LoggerFactory.getLogger("halo").warn("[Halo ReplayMod] legacy replay: no complete Halo track; initial progress/UI close may be unavailable");
            }
            if(video!=null && exportSample)return;
            Object sender=ReplayModAccess.call(handler,"getReplaySender");
            long source=Math.max(0,((Number)ReplayModAccess.call(sender,"currentTimeStamp")).longValue())*1_000_000L;
            advance(source,source,false);
        }catch(Exception failure){failure(failure);}
    }
    private void advance(long source,long output,boolean exporting){
        if(playback==null || !playback.isValid() || timeline==null)return;
        boolean discontinuity=seek || source<lastSource;
        if(source==lastSource && !exporting && !discontinuity)return;
        if(source!=lastSource || exporting || discontinuity)++samples;
        if(timeline.hasTrack())timeline.advance(playback,source,output,samples,discontinuity);
        else{
            if(discontinuity)epoch=ReplayTimeline.nextEpoch();
            playback.frame(new ReplayFrameTime(epoch,samples,source,output,discontinuity));
        }
        lastSource=source;seek=false;
    }
    private void startExport(Object renderer){
        video=renderer;exportSample=false;
        if(indexing!=null && timeline==null)timeline=indexing.join();
        ReplayClient.setPreparing(false);
        if(playback!=null && playback.isValid())playback.mode(ReplayPlaybackSession.Mode.EXPORT_PREPARING);
    }
    private void sampleExport(Object renderer){
        if(failed)return;
        try{
            if(video!=renderer)startExport(renderer);
            if(playback==null || !playback.isValid()){prepare();if(playback==null)return;}
            int frame=((Number)ReplayModAccess.call(renderer,"getFramesDone")).intValue();
            Object settings=ReplayModAccess.call(renderer,"getRenderSettings");
            double fps=((Number)ReplayModAccess.call(settings,"getFramesPerSecond")).doubleValue();
            long output=Math.round(frame*1_000_000_000.0/fps);
            // Evaluate the public timeline at this output frame before updateForNextFrame ticks the client.
            var field=renderer.getClass().getDeclaredField("timeline");field.setAccessible(true);
            Object path=field.get(renderer);
            Object property=ReplayModAccess.type("com.replaymod.pathing.properties.TimestampProperty").getField("PROPERTY").get(null);
            Object optional=ReplayModAccess.call(path,"getValue",property,output/1_000_000);
            long source=(boolean)ReplayModAccess.call(optional,"isPresent")
                ? ((Number)ReplayModAccess.call(optional,"get")).longValue()*1_000_000L
                : ((Number)ReplayModAccess.call(ReplayModAccess.call(handler,"getReplaySender"),"currentTimeStamp")).longValue()*1_000_000L;
            playback.mode(ReplayPlaybackSession.Mode.EXPORTING);
            advance(Math.max(0,source),output,true);exportSample=true;
        }catch(Exception failure){failure(failure);}
    }
    private void endExport(){
        if(video==null)return;video=null;exportSample=false;seek=true;
        if(playback!=null && playback.isValid())playback.mode(ReplayPlaybackSession.Mode.PLAYBACK);
    }
    private void failure(Throwable failure){
        LoggerFactory.getLogger("halo").error("[Halo ReplayMod] compatibility failed; this backend is disabled",failure);
        failed=true;close();if(recording!=null && recording.isValid())recording.close();recording=null;recorder=null;
    }
}
