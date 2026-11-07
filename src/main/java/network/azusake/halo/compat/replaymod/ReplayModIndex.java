package network.azusake.halo.compat.replaymod;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketByteBuf;
import java.util.*;
import network.azusake.halo.replay.*;

/** Scans the original ReplayStudio stream, including protocol-state transitions handled by ReplayInputStream. */
final class ReplayModIndex {
    static ReplayTimeline read(Object handler)throws Exception{
        Object file=ReplayModAccess.call(handler,"getReplayFile");
        Object registry=ReplayModAccess.invokeStatic("com.replaymod.core.versions.MCVer","getPacketTypeRegistry",true);
        // MCVer(boolean) chooses LOGIN when true; use its current runtime protocol registry.
        Object input=ReplayModAccess.call(file,"getPacketData",registry);
        var entries=new ArrayList<ReplayTimeline.Entry>(); long order=0;
        try{
            Object data;
            while((data=ReplayModAccess.call(input,"readPacket"))!=null){
                try{
                    Object packet=ReplayModAccess.call(data,"getPacket");
                    if(!"PluginMessage".equals(ReplayModAccess.call(packet,"getType").toString()))continue;
                    ByteBuf raw=(ByteBuf)ReplayModAccess.call(packet,"getBuf");
                    var buf=new PacketByteBuf(raw.duplicate());
                    if(!ReplayPayloadCodec.CHANNEL.equals(buf.readString(32767)))continue;
                    byte[] bytes=new byte[buf.readableBytes()];buf.readBytes(bytes);
                    long millis=((Number)ReplayModAccess.call(data,"getTime")).longValue();
                    entries.add(new ReplayTimeline.Entry(millis*1_000_000L,order++,ReplayPayloadCodec.decode(bytes)));
                }finally{ReplayModAccess.call(data,"release");}
            }
        }finally{ReplayModAccess.call(input,"close");}
        return new ReplayTimeline(entries);
    }
}
