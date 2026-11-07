package network.azusake.halo.replay;

import java.util.List;
import network.azusake.halo.api.v2.replay.ReplayPlaybackSession;

/** Converts host codec envelopes to core's neutral timeline; restoration rules remain in core. */
public final class ReplayTimeline {
    public static long nextEpoch(){return network.azusake.halo.core.runtime.ReplayTimeline.nextEpoch();}
    public record Entry(long sourceNanos,long order,ReplayPayloadCodec.Record record){}
    private final network.azusake.halo.core.runtime.ReplayTimeline delegate;
    public ReplayTimeline(List<Entry> input){
        delegate=new network.azusake.halo.core.runtime.ReplayTimeline(input.stream().map(ReplayTimeline::neutral).toList());
    }
    private static network.azusake.halo.core.runtime.ReplayTimeline.Entry neutral(Entry entry){
        return new network.azusake.halo.core.runtime.ReplayTimeline.Entry(entry.sourceNanos(),entry.order(),entry.record().snapshot(),entry.record().event());
    }
    public boolean hasTrack(){return delegate.hasTrack();}
    public boolean needsAdvance(long source){return delegate.needsAdvance(source);}
    public void append(Entry entry){delegate.append(neutral(entry));}
    public void advance(ReplayPlaybackSession playback,long source,long output,long sample,boolean seek){
        delegate.advance(playback,source,output,sample,seek);
    }
    public void reset(){delegate.reset();}
}
