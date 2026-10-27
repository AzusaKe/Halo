package network.azusake.halo.compat.replaymod;

import java.util.*;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketByteBuf;
import network.azusake.halo.core.runtime.ReplayStateAccumulator;
import network.azusake.halo.replay.*;

/** Rebuild Halo's baseline when ReplayStudio squashes a cut or split interval. */
public final class ReplayMarkerSupport {
    private static final ThreadLocal<State> CONTEXT=new ThreadLocal<>();
    public static final class State {
        private final ReplayStateAccumulator accumulator=new ReplayStateAccumulator();
        private UUID track;
        private Object registry;
        private State copy(){
            var result=new State();result.track=track;result.registry=registry;
            result.accumulator.restore(accumulator.snapshotAt(accumulator.lastSourceNanos()),accumulator.lastSourceNanos());return result;
        }
    }
    private ReplayMarkerSupport(){}
    public static void begin(){CONTEXT.set(new State());}
    public static void end(){CONTEXT.remove();}
    public static State filterState(){var context=CONTEXT.get();return context==null?new State():context.copy();}
    public static State copy(State state){return state.copy();}
    public static void contextPacket(Object data){var context=CONTEXT.get();if(context!=null)capture(context,data);}
    /** True means the ordinary unknown-payload squash path must not retain this Halo packet as well. */
    public static boolean capture(State state,Object data){
        try{
            Object packet=ReplayModAccess.call(data,"getPacket");
            if(!"PluginMessage".equals(ReplayModAccess.call(packet,"getType").toString()))return false;
            var buf=new PacketByteBuf(((ByteBuf)ReplayModAccess.call(packet,"getBuf")).duplicate());
            if(!ReplayPayloadCodec.CHANNEL.equals(buf.readString(32767)))return false;
            byte[] bytes=new byte[buf.readableBytes()];buf.readBytes(bytes);
            var record=ReplayPayloadCodec.decode(bytes);
            long source=((Number)ReplayModAccess.call(data,"getTime")).longValue()*1_000_000L;
            state.track=record.track();state.registry=ReplayModAccess.call(packet,"getRegistry");
            if(record.snapshot()!=null)state.accumulator.restore(record.snapshot(),source);
            else state.accumulator.apply(record.event(),source);
            return true;
        }catch(Exception failure){throw new IllegalStateException("Cannot preserve Halo state while processing replay markers",failure);}
    }
    public static void flush(State state,Object stream,long containerMillis){
        if(state.track==null || state.registry==null)return;
        try{
            long source=Math.max(state.accumulator.lastSourceNanos(),containerMillis*1_000_000L);
            var snapshot=state.accumulator.snapshotAt(source);
            byte[] payload=ReplayPayloadCodec.encode(new ReplayPayloadCodec.Record(state.track,snapshot,null));
            Class<?> packetClass=ReplayModAccess.type("com.replaymod.replaystudio.protocol.Packet");
            Class<?> registryClass=ReplayModAccess.type("com.replaymod.replaystudio.protocol.PacketTypeRegistry");
            Class<?> typeClass=ReplayModAccess.type("com.replaymod.replaystudio.protocol.PacketType");
            Object packet=packetClass.getConstructor(registryClass,typeClass).newInstance(state.registry,typeClass.getField("PluginMessage").get(null));
            Object writer=ReplayModAccess.call(packet,"overwrite");
            try{ReplayModAccess.call(writer,"writeString",ReplayPayloadCodec.CHANNEL);ReplayModAccess.call(writer,"writeBytes",payload);}
            finally{((AutoCloseable)writer).close();}
            ReplayModAccess.call(stream,"insert",containerMillis,packet);
            state.accumulator.restore(snapshot,source);
        }catch(Exception failure){throw new IllegalStateException("Cannot write Halo's rebased marker snapshot",failure);}
    }
}
