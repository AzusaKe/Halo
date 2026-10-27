package network.azusake.halo.replay;

import java.util.*;
import network.azusake.halo.api.v2.replay.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReplayPayloadCodecTest {
    @Test void roundTripsSnapshotsShutdownValuesAndAllEventKinds() {
        UUID track=UUID.randomUUID(), wearer=UUID.randomUUID();
        var ui=new ReplayUiState(7,true,wearer,"test wearer");
        var group=new ReplayGroupState(List.of(1f,2f,3f),List.of(1f,1f,1f),.25f,List.of(0f,30f,0f));
        var visual=new ReplayVisualState("halo:test",5_000_000_000L,ReplayVisualState.Transition.ENDING,
            100_000_000,.8,true,false,false,false,Map.of("ring",group));
        var snapshot=new ReplaySnapshot(10,Map.of(),Map.of(wearer,visual),ui);
        for(var record:List.of(new ReplayPayloadCodec.Record(track,snapshot,null),
            new ReplayPayloadCodec.Record(track,null,new ReplayEvent.WearerChanged(11,wearer,null,visual)),
            new ReplayPayloadCodec.Record(track,null,new ReplayEvent.UiChanged(12,ui)),
            new ReplayPayloadCodec.Record(track,null,new ReplayEvent.Reset(snapshot))))
            assertEquals(record,ReplayPayloadCodec.decode(ReplayPayloadCodec.encode(record)));
    }
    @Test void unknownSchemaFailsExplicitlyAndEmptySnapshotRemainsAFullReplacement() {
        UUID track=UUID.randomUUID();
        var empty=new ReplayPayloadCodec.Record(track,new ReplaySnapshot(0,Map.of(),Map.of(),ReplayUiState.CLOSED),null);
        assertEquals(empty,ReplayPayloadCodec.decode(ReplayPayloadCodec.encode(empty)));
        String invalid=new String(ReplayPayloadCodec.encode(empty),java.nio.charset.StandardCharsets.UTF_8).replace("\"schema\":1","\"schema\":999");
        assertThrows(IllegalArgumentException.class,()->ReplayPayloadCodec.decode(invalid.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
