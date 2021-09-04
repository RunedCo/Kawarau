package co.runed.kawarau;

import co.runed.bolster.common.gson.GsonUtil;
import co.runed.bolster.common.redis.RedisChannels;
import co.runed.bolster.common.redis.RedisManager;
import co.runed.bolster.common.redis.payload.Payload;
import co.runed.bolster.common.redis.request.RequestPlayerDataPayload;
import co.runed.bolster.common.redis.request.UpdatePlayerDataPayload;
import co.runed.bolster.common.redis.response.RequestPlayerDataResponsePayload;
import co.runed.kawarau.events.RedisMessageEvent;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mongodb.client.model.ReplaceOptions;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.bson.Document;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManager implements Listener {
    private Map<UUID, Map<String, Object>> playerData = new HashMap<>();

    private static PlayerManager _instance;
    private static final Gson _gson = GsonUtil.create();

    final Type mapType = new TypeToken<Map<String, Object>>() {
    }.getType();

    public PlayerManager() {
        _instance = this;
    }

    private void load(UUID uuid) {
        var mongoClient = Kawarau.getMongoClient();
        var db = mongoClient.getDatabase(Kawarau.getInstance().config.databaseName);
        var collection = db.getCollection("players");
        var query = new Document("uuid", uuid.toString());

        var document = collection.find(query).first();

        if (document == null) {
            document = new Document();
        }

        document.put("uuid", uuid.toString());

        playerData.put(uuid, _gson.fromJson(document.toJson(), mapType));
    }

    private void save(UUID uuid) {
        var mongoClient = Kawarau.getMongoClient();
        var db = mongoClient.getDatabase(Kawarau.getInstance().config.databaseName);
        var collection = db.getCollection("players");
        var query = new Document("uuid", uuid.toString());

        var document = Document.parse(_gson.toJson(playerData.get(uuid)));
        var options = new ReplaceOptions();
        options.upsert(true);
        collection.replaceOne(query, document, options);
    }

    public void setPlayerData(UUID uuid, String playerData) {
        this.playerData.put(uuid, _gson.fromJson(playerData, mapType));

        this.save(uuid);
    }

    public Map<String, Object> getPlayerData(UUID uuid) {
        if (!this.playerData.containsKey(uuid)) this.load(uuid);

        return this.playerData.get(uuid);
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        var uuid = event.getPlayer().getUniqueId();

        this.load(uuid);
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        var uuid = event.getPlayer().getUniqueId();

        this.save(uuid);

        playerData.remove(uuid);
    }

    @EventHandler
    public void onRedisMessage(RedisMessageEvent event) {
        switch (event.getChannel()) {
            case RedisChannels.REQUEST_PLAYER_DATA: {
                var payload = Payload.fromJson(event.getMessage(), RequestPlayerDataPayload.class);

                var response = new RequestPlayerDataResponsePayload();
                response.target = payload.sender;
                response.playerData = _gson.toJson(getPlayerData(payload.uuid));

                RedisManager.getInstance().publish(RedisChannels.REQUEST_PLAYER_DATA_RESPONSE, response);
                break;
            }
            case RedisChannels.UPDATE_PLAYER_DATA: {
                var payload = Payload.fromJson(event.getMessage(), UpdatePlayerDataPayload.class);

                for (var entry : payload.playerData.entrySet()) {
                    setPlayerData(entry.getKey(), entry.getValue());
                }

                break;
            }
        }
    }

    public static PlayerManager getInstance() {
        return _instance;
    }
}
