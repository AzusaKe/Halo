package network.azusake.halo.compat.flashback;

import java.lang.reflect.*;
import java.lang.ref.Reference;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import network.azusake.halo.api.v2.replay.*;
import network.azusake.halo.replay.*;
import org.slf4j.LoggerFactory;

/** Flashback public API and small snapshot/output boundary bridges; no external values escape this package. */
public final class FlashbackBackend {
    private static final FlashbackBackend INSTANCE=new FlashbackBackend();
    private static final ConcurrentLinkedQueue<Message> INCOMING=new ConcurrentLinkedQueue<>();
    private static final ThreadLocal<Long> SNAPSHOT_TICK=new ThreadLocal<>();
    private static final java.util.concurrent.atomic.AtomicBoolean IGNORED_WARNING=new java.util.concurrent.atomic.AtomicBoolean();
    private record Message(Object server,long sourceNanos,ReplayPayloadCodec.Record record,boolean boundary){}
    private Object recorder,server,job;
    private ReplayRecordingSession recording;
    private ReplayPlaybackSession playback;
    private ReplayTimeline timeline=new ReplayTimeline(List.of());
    private UUID track;
    private long order,samples,epoch,lastSource=-1,outputFrame;
    private boolean failed,seek,exportSample;
    private FlashbackBackend(){}
    public static void initialize(){
        if(FlashbackBackend.class.getClassLoader().getResource("com/moulberry/flashback/Flashback.class")==null)return;
        try{
            for(String target:new String[]{"com.moulberry.flashback.record.Recorder","com.moulberry.flashback.io.ReplayReader",
                "com.moulberry.flashback.playback.ReplayServer","com.moulberry.flashback.exporting.ExportJob"})
                {Class.forName(target,false,FlashbackBackend.class.getClassLoader());
                 if(!FlashbackHooks.has(target))throw new IllegalStateException("required replay boundary hook is unavailable: "+target);}
            LoggerFactory.getLogger("halo").info("[Halo Flashback] snapshot, source timing and output boundary hooks verified");
        }catch(Exception | LinkageError failure){INSTANCE.failure(failure);return;}
        ReplayClient.registerPreparation(INSTANCE::prepare);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client->INSTANCE.recordingTick());
    }
    public static void lifecycle(){INSTANCE.prepare();}
    private static Object mainField(String name)throws Exception{return Class.forName("com.moulberry.flashback.Flashback").getField(name).get(null);}
    private static Object field(Object target,String name)throws Exception{
        Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    private static Object call(Object target,String name,Object...args)throws Exception{
        for(Method method:target.getClass().getMethods())if(method.getName().equals(name) && method.getParameterCount()==args.length){
            method.trySetAccessible();return method.invoke(target,args);
        }
        throw new NoSuchMethodException(target.getClass().getName()+"."+name);
    }
    private static Object mainCall(String name)throws Exception{return Class.forName("com.moulberry.flashback.Flashback").getMethod(name).invoke(null);}
    private void recordingTick(){
        if(failed || ReplayClient.isPlayback())return;
        try{
            Object value=mainField("RECORDER");
            if(value==null){if(recording!=null && recording.isValid())recording.close();recording=null;recorder=null;}
            else ensureRecording(value);
        }catch(Exception failure){failure(failure);}
    }
    private void ensureRecording(Object value){
        if(recorder==value && recording!=null && recording.isValid())return;
        if(recording!=null && recording.isValid())recording.close();recorder=value;track=UUID.randomUUID();
        recording=HaloReplayApi.openRecording("halo:flashback",event->{
            try{
                if((boolean)call(recorder,"readyToWrite") && !(boolean)call(recorder,"isPaused")){
                    Packet<?> packet=ReplayTransport.packet(new ReplayPayloadCodec.Record(track,null,event));
                    call(recorder,"writePacketAsync",packet,net.minecraft.network.NetworkPhase.PLAY);
                }
            }catch(Exception failure){failure(failure);}
        }).orElse(null);
    }
    public static void snapshot(Object recorder,Consumer<?> consumer){
        if(INSTANCE.failed || ReplayClient.isPlayback())return;
        INSTANCE.ensureRecording(recorder);
        if(INSTANCE.recording!=null){
            @SuppressWarnings("unchecked") Consumer<Packet<?>> sink=(Consumer<Packet<?>>)consumer;
            sink.accept(ReplayTransport.packet(new ReplayPayloadCodec.Record(INSTANCE.track,INSTANCE.recording.snapshot(),null)));
        }
    }
    /** Runs on the replay server thread. Only immutable neutral values cross to the client. */
    public static void snapshotBegin(Object reader,Object server){
        try{
            long tick=0;
            Map<?,?> chunks=(Map<?,?>)field(server,"playableChunksByStart");
            for(var entry:chunks.entrySet()){
                Object ref=field(entry.getValue(),"replayReader");
                if(ref instanceof Reference<?> reference && reference.get()==reader){tick=((Number)entry.getKey()).longValue();break;}
            }
            SNAPSHOT_TICK.set(tick);INCOMING.add(new Message(server,tick*50_000_000L,null,true));
        }catch(Exception failure){LoggerFactory.getLogger("halo").error("[Halo Flashback] unable to timestamp snapshot",failure);}
    }
    public static void snapshotEnd(){SNAPSHOT_TICK.remove();}
    public static void gamePacket(Object server,Object raw){
        boolean halo=false;
        try{
            var buf=new PacketByteBuf(((io.netty.buffer.ByteBuf)raw).duplicate());
            buf.readVarInt(); // vanilla packet id
            if(!ReplayPayloadCodec.CHANNEL.equals(buf.readString(32767)))return;
            halo=true;
            Object advanced=field(mainCall("getConfig"),"advanced");
            Object ignored=field(server,"ignoredCustomPayloads");
            call(ignored,"setFromConfigString",field(advanced,"ignoredCustomPayloads"));
            if((boolean)call(ignored,"isIgnored",net.minecraft.util.Identifier.of("halo","replay"))){
                if(IGNORED_WARNING.compareAndSet(false,true))LoggerFactory.getLogger("halo").warn("[Halo Flashback] halo:replay is excluded by Flashback ignoredCustomPayloads; full Halo restoration is unavailable");
                return;
            }
            byte[] bytes=new byte[buf.readableBytes()];buf.readBytes(bytes);
            Long snapshot=SNAPSHOT_TICK.get();
            long tick=snapshot!=null?snapshot:((Number)field(server,"currentTick")).longValue();
            INCOMING.add(new Message(server,tick*50_000_000L,ReplayPayloadCodec.decode(bytes),false));
        }catch(io.netty.handler.codec.DecoderException | IndexOutOfBoundsException normalPacket){}
        catch(IllegalArgumentException normalPacket){if(halo)LoggerFactory.getLogger("halo").error("[Halo Flashback] unsupported or malformed Halo replay payload",normalPacket);}
        catch(Exception failure){LoggerFactory.getLogger("halo").error("[Halo Flashback] unable to decode Halo packet",failure);}
    }
    private void prepare(){
        if(failed)return;
        try{
            Object active=mainCall("getReplayServer");
            if(active==null){if(server!=null)closePlayback();return;}
            if(server!=active){closePlayback();server=active;timeline=new ReplayTimeline(List.of());seek=true;lastSource=-1;}
            if(playback==null || !playback.isValid()){
                playback=HaloReplayApi.openPlayback("halo:flashback").orElse(null);timeline.reset();seek=true;
            }
            if(playback==null)return;
            Message message;
            while((message=INCOMING.poll())!=null){
                if(message.server()!=server)continue;
                if(message.boundary())seek=true;
                else timeline.append(new ReplayTimeline.Entry(message.sourceNanos(),order++,message.record()));
            }
            Object export=mainField("EXPORT_JOB");
            if(job!=null && (export!=job || !(boolean)call(job,"isRunning")))endExport();
            if(exportSample && job!=null)return;
            long source=Math.max(0,Math.round(((Number)call(server,"getPartialReplayTick")).doubleValue()*50_000_000));
            advance(source,source,false);
        }catch(Exception failure){failure(failure);}
    }
    private void advance(long source,long output,boolean exporting){
        if(playback==null || !playback.isValid())return;
        boolean discontinuity=seek || source<lastSource;
        if(source==lastSource && !exporting && !discontinuity && !timeline.needsAdvance(source))return;
        ++samples;
        if(timeline.hasTrack())timeline.advance(playback,source,output,samples,discontinuity);
        else{if(discontinuity)epoch=ReplayTimeline.nextEpoch();playback.frame(new ReplayFrameTime(epoch,samples,source,output,discontinuity));}
        lastSource=source;seek=false;
    }
    public static void exportStart(Object value){
        INSTANCE.prepare();INSTANCE.job=value;INSTANCE.outputFrame=0;INSTANCE.exportSample=false;
        if(INSTANCE.playback!=null)INSTANCE.playback.mode(ReplayPlaybackSession.Mode.EXPORT_PREPARING);
    }
    public static void outputFrame(Object value){
        var self=INSTANCE;if(self.failed)return;
        try{
            self.prepare();
            if(self.job!=value)exportStart(value);
            Object settings=call(value,"getSettings");
            double fps=((Number)call(settings,"framerate")).doubleValue();
            double start=((Number)call(settings,"startTick")).doubleValue();
            double tick=((Number)call(value,"getCurrentTickDouble")).doubleValue()+start;
            long output=Math.round(self.outputFrame++*1_000_000_000.0/fps);
            if(self.playback!=null)self.playback.mode(ReplayPlaybackSession.Mode.EXPORTING);
            self.advance(Math.max(0,Math.round(tick*50_000_000)),output,true);self.exportSample=true;
        }catch(Exception failure){self.failure(failure);}
    }
    public static void outputReady(){
        // Tick/server work may deliver a new snapshot or event after the frame's preparation boundary.
        var self=INSTANCE;if(self.failed || self.job==null)return;
        try{
            self.prepare();
            Object settings=call(self.job,"getSettings");
            double tick=((Number)call(self.job,"getCurrentTickDouble")).doubleValue()+((Number)call(settings,"startTick")).doubleValue();
            double fps=((Number)call(settings,"framerate")).doubleValue();
            self.advance(Math.max(0,Math.round(tick*50_000_000)),Math.round((self.outputFrame-1)*1_000_000_000.0/fps),true);
            self.exportSample=true;
        }catch(Exception failure){self.failure(failure);}
    }
    public static void exportEnd(){INSTANCE.endExport();}
    private void endExport(){job=null;exportSample=false;seek=true;if(playback!=null && playback.isValid())playback.mode(ReplayPlaybackSession.Mode.PLAYBACK);}
    private void closePlayback(){
        if(playback!=null && playback.isValid())playback.close();playback=null;server=null;job=null;exportSample=false;lastSource=-1;
    }
    private void failure(Throwable failure){
        LoggerFactory.getLogger("halo").error("[Halo Flashback] compatibility failed; this backend is disabled",failure);
        failed=true;closePlayback();if(recording!=null && recording.isValid())recording.close();recording=null;recorder=null;
    }
}
