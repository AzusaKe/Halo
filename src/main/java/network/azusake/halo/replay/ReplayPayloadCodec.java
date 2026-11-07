package network.azusake.halo.replay;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import network.azusake.halo.api.v2.replay.*;

/** Shared inner recording format. Container packet timestamps supply all event times. */
public final class ReplayPayloadCodec {
    public static final int SCHEMA = 1;
    public static final String CHANNEL = "halo:replay";
    private static final Gson GSON = new Gson();
    private ReplayPayloadCodec() {}
    public record Record(UUID track, ReplaySnapshot snapshot, ReplayEvent event) {
        public Record {
            Objects.requireNonNull(track);
            if ((snapshot == null) == (event == null)) throw new IllegalArgumentException("expected snapshot or event");
        }
    }
    public static byte[] encode(Record record) {
        JsonObject json=new JsonObject(); json.addProperty("schema",SCHEMA); json.addProperty("track",record.track().toString());
        if(record.snapshot()!=null) {json.addProperty("kind","snapshot");json.add("data",GSON.toJsonTree(record.snapshot()));}
        else {
            String kind=record.event() instanceof ReplayEvent.WearerChanged?"wearer":record.event() instanceof ReplayEvent.UiChanged?"ui":"reset";
            json.addProperty("kind",kind);json.add("data",GSON.toJsonTree(record.event()));
        }
        return GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
    }
    public static Record decode(byte[] bytes) {
        JsonObject json=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();
        if(json.get("schema").getAsInt()!=SCHEMA) throw new IllegalArgumentException("unsupported Halo replay schema");
        UUID track=UUID.fromString(json.get("track").getAsString()); JsonElement data=json.get("data");
        return switch(json.get("kind").getAsString()) {
            case "snapshot" -> new Record(track,GSON.fromJson(data,ReplaySnapshot.class),null);
            case "wearer" -> new Record(track,null,GSON.fromJson(data,ReplayEvent.WearerChanged.class));
            case "ui" -> new Record(track,null,GSON.fromJson(data,ReplayEvent.UiChanged.class));
            case "reset" -> new Record(track,null,GSON.fromJson(data,ReplayEvent.Reset.class));
            default -> throw new IllegalArgumentException("unknown Halo replay record");
        };
    }
}
