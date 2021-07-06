package co.runed.kawarau;

import co.runed.bolster.common.redis.RedisChannels;
import co.runed.bolster.common.redis.RedisManager;
import co.runed.bolster.common.redis.payload.Payload;
import co.runed.bolster.common.redis.request.RequestPlayerDataPayload;
import co.runed.bolster.common.redis.request.UpdatePlayerDataPayload;
import co.runed.bolster.common.redis.response.RequestPlayerDataResponsePayload;
import co.runed.kawarau.events.RedisMessageEvent;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import org.bson.Document;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerManager implements Listener
{
    private Map<UUID, String> playerData = new HashMap<>();

    private static PlayerManager _instance;

    public PlayerManager()
    {
        _instance = this;
    }

    private void load(UUID uuid)
    {
        MongoClient mongoClient = Kawarau.getMongoClient();
        MongoDatabase db = mongoClient.getDatabase(Kawarau.getInstance().config.databaseName);
        MongoCollection<Document> collection = db.getCollection("players");
        Document query = new Document("uuid", uuid.toString());

        Document document = collection.find(query).first();

        if (document == null)
        {
            document = new Document();
        }

        document.put("uuid", uuid.toString());

        playerData.put(uuid, document.toJson());
    }

    private void save(UUID uuid)
    {
        MongoClient mongoClient = Kawarau.getMongoClient();
        MongoDatabase db = mongoClient.getDatabase(Kawarau.getInstance().config.databaseName);
        MongoCollection<Document> collection = db.getCollection("players");
        Document query = new Document("uuid", uuid.toString());

        Document document = Document.parse(playerData.get(uuid));
        ReplaceOptions options = new ReplaceOptions();
        options.upsert(true);
        collection.replaceOne(query, document, options);
    }

    public void setPlayerData(UUID uuid, String playerData)
    {
        this.playerData.put(uuid, playerData);

        this.save(uuid);
    }

    public String getPlayerData(UUID uuid)
    {
        if (!this.playerData.containsKey(uuid)) this.load(uuid);

        return this.playerData.get(uuid);
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event)
    {
        UUID uuid = event.getPlayer().getUniqueId();

        this.load(uuid);
    }

    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event)
    {
        UUID uuid = event.getPlayer().getUniqueId();

        this.save(uuid);

        playerData.remove(uuid);
    }

    @EventHandler
    public void onRedisMessage(RedisMessageEvent event)
    {
        switch (event.getChannel())
        {
            case RedisChannels.REQUEST_PLAYER_DATA:
            {
                RequestPlayerDataPayload payload = Payload.fromJson(event.getMessage(), RequestPlayerDataPayload.class);

                RequestPlayerDataResponsePayload response = new RequestPlayerDataResponsePayload();
                response.target = payload.sender;
                response.playerData = getPlayerData(payload.uuid);

                RedisManager.getInstance().publish(RedisChannels.REQUEST_PLAYER_DATA_RESPONSE, response);
                break;
            }
            case RedisChannels.UPDATE_PLAYER_DATA:
            {
                UpdatePlayerDataPayload payload = Payload.fromJson(event.getMessage(), UpdatePlayerDataPayload.class);

                setPlayerData(payload.uuid, payload.playerData);
                break;
            }
        }
    }

    public static PlayerManager getInstance()
    {
        return _instance;
    }
}
