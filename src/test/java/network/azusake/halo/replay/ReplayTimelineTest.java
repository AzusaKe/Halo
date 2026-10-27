package network.azusake.halo.replay;

import java.util.*;
import network.azusake.halo.api.v2.replay.*;
import network.azusake.halo.core.runtime.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayTimelineTest {
    private final UUID wearer=UUID.randomUUID(),track=UUID.randomUUID();
    private ReplayTimeline.Entry snapshot(long time,long order,long seq,Map<UUID,String> state){
        return new ReplayTimeline.Entry(time,order,new ReplayPayloadCodec.Record(track,new ReplaySnapshot(seq,state,Map.of(),ReplayUiState.CLOSED),null));
    }
    private ReplayTimeline.Entry event(long time,long order,long seq,String id){
        return new ReplayTimeline.Entry(time,order,new ReplayPayloadCodec.Record(track,null,new ReplayEvent.WearerChanged(seq,wearer,id,null)));
    }
    @Test void fullAndQuickStyleJumpsRestoreTheSameStateAndEmptyFutureIsCleared(){
        ClientRuntime runtime=new ClientRuntime(()->0);
        var timeline=new ReplayTimeline(List.of(snapshot(0,0,0,Map.of()),event(100,1,1,"halo:first"),
            event(100,2,2,"halo:second"),snapshot(200,3,2,Map.of(wearer,"halo:second")),event(300,4,3,null)));
        try(var host=new ReplayHost(runtime,ui->{},message->fail(message))){
            var playback=HaloReplayApi.openPlayback("test:player").orElseThrow();
            timeline.advance(playback,100,50,1,false);assertEquals("halo:second",runtime.assignments().get(wearer).toString());
            timeline.advance(playback,350,175,2,true);assertTrue(runtime.assignments().isEmpty());
            timeline.advance(playback,150,75,3,true);assertEquals("halo:second",runtime.assignments().get(wearer).toString());
            timeline.advance(playback,0,0,4,true);assertTrue(runtime.assignments().isEmpty());
        }
    }
    @Test void streamingAppendDoesNotResetContinuousStateAndDeduplicatesReplayedRecords(){
        ClientRuntime runtime=new ClientRuntime(()->0);var timeline=new ReplayTimeline(List.of(snapshot(0,0,0,Map.of())));
        try(var host=new ReplayHost(runtime,ui->{},message->fail(message))){
            var playback=HaloReplayApi.openPlayback("test:player").orElseThrow();
            timeline.advance(playback,0,0,1,true);
            var change=event(100,1,1,"halo:test");timeline.append(change);timeline.append(change);
            timeline.advance(playback,100,100,2,false);assertEquals("halo:test",runtime.assignments().get(wearer).toString());
            timeline.append(snapshot(200,2,1,Map.of(wearer,"halo:test")));
            timeline.advance(playback,250,250,3,false);assertEquals(1,runtime.assignments().size());
            timeline.advance(playback,50,50,4,true);assertTrue(runtime.assignments().isEmpty());
        }
    }
    @Test void aLateRecordIsReconstructedAtCurrentSourceRatherThanDiscarded(){
        ClientRuntime runtime=new ClientRuntime(()->0);var timeline=new ReplayTimeline(List.of(snapshot(0,0,0,Map.of())));
        try(var host=new ReplayHost(runtime,ui->{},message->fail(message))){
            var playback=HaloReplayApi.openPlayback("test:player").orElseThrow();timeline.advance(playback,200,200,1,true);
            timeline.append(event(100,1,1,"halo:late"));timeline.advance(playback,250,250,2,false);
            assertEquals("halo:late",runtime.assignments().get(wearer).toString());
        }
    }
}
