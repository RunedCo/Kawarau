package co.runed.kawarau;

import co.runed.dayroom.gson.GsonUtil;
import co.runed.dayroom.redis.RedisChannels;
import co.runed.dayroom.redis.RedisManager;
import co.runed.dayroom.redis.payload.Payload;
import co.runed.dayroom.redis.request.RequestMatchHistoryIdPayload;
import co.runed.dayroom.redis.request.UpdateMatchHistoryPayload;
import co.runed.dayroom.redis.response.RequestMatchHistoryIdResponsePayload;
import co.runed.kawarau.events.RedisMessageEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mongodb.client.model.ReplaceOptions;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.bson.Document;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MatchManager implements Listener {
    private static final Gson _gson = GsonUtil.create();
    private Map<UUID, Map<String, Object>> matches = new HashMap<>();

    final Type mapType = new TypeToken<Map<String, Object>>() {
    }.getType();

    private UUID getNextMatchId() {
        UUID uuid = null;

        while (uuid == null || isIdTaken(uuid)) {
            uuid = UUID.randomUUID();
        }

        return uuid;
    }

    private boolean isIdTaken(UUID id) {
        var mongoClient = Kawarau.getMongoClient();
        var db = mongoClient.getDatabase(Kawarau.getInstance().config.databaseName);
        var collection = db.getCollection("matches");
        var query = new Document("matchId", id.toString());

        var results = collection.countDocuments(query);

        return results > 1;
    }

    private void save(UUID uuid) {
        var mongoClient = Kawarau.getMongoClient();
        var db = mongoClient.getDatabase(Kawarau.getInstance().config.databaseName);
        var collection = db.getCollection("matches");
        var query = new Document("matchId", uuid.toString());

        var document = Document.parse(_gson.toJson(matches.get(uuid)));
        var options = new ReplaceOptions();
        options.upsert(true);
        collection.replaceOne(query, document, options);
    }

    private void update(UUID uuid, String contents) {
        this.matches.put(uuid, _gson.fromJson(contents, mapType));

        save(uuid);
    }

    @EventHandler
    public void onRedisMessage(RedisMessageEvent event) {
        switch (event.getChannel()) {
            case RedisChannels.REQUEST_MATCH_HISTORY_ID -> {
                var payload = Payload.fromJson(event.getMessage(), RequestMatchHistoryIdPayload.class);

                var response = new RequestMatchHistoryIdResponsePayload();
                response.target = payload.sender;
                response.matchId = getNextMatchId();

                RedisManager.getInstance().publish(RedisChannels.REQUEST_MATCH_HISTORY_ID_RESPONSE, response);
            }

            case RedisChannels.UPDATE_MATCH_HISTORY -> {
                var payload = Payload.fromJson(event.getMessage(), UpdateMatchHistoryPayload.class);

                if (payload.matchId == null || payload.json == null) return;

                update(payload.matchId, payload.json);
            }

            case RedisChannels.END_MATCH -> {
                var payload = Payload.fromJson(event.getMessage(), UpdateMatchHistoryPayload.class);

                if (payload.matchId == null) return;

                save(payload.matchId);

                matches.remove(payload.matchId);
            }
        }
    }
}
