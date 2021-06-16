package co.runed.kawarau;

import co.runed.bolster.common.redis.RedisChannels;
import co.runed.bolster.common.redis.payload.Payload;
import co.runed.bolster.common.redis.request.RegisterServerPayload;
import co.runed.bolster.common.redis.request.UnregisterServerPayload;
import co.runed.bolster.common.redis.response.RegisterServerResponsePayload;
import co.runed.kawarau.events.RedisMessageEvent;
import co.runed.kawarau.util.GsonUtil;
import com.google.gson.reflect.TypeToken;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;
import org.bson.UuidRepresentation;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class Kawarau extends Plugin implements Listener
{
    public Config config;
    private MongoClient mongoClient;
    private JedisPool jedisPool;
    private PlayerManager playerManager;

    private Map<String, ServerData> serverData = new HashMap<>();

    private static Kawarau _instance;

    @Override
    public void onLoad()
    {
        this.config = new Config(this);

        _instance = this;
    }

    @Override
    public void onEnable()
    {
        getLogger().info("Loading Kawarau...");

        /* Connect to MongoDB */
        MongoCredential credential = MongoCredential.createCredential(this.config.databaseUsername, "admin", this.config.databasePassword.toCharArray());
        ConnectionString connectionString = new ConnectionString("mongodb://" + this.config.databaseUrl + ":" + this.config.databasePort);
        CodecRegistry pojoCodecRegistry = CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build());
        CodecRegistry codecRegistry = CodecRegistries.fromRegistries(MongoClientSettings.getDefaultCodecRegistry(),
                pojoCodecRegistry);

        MongoClientSettings clientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .credential(credential)
                .codecRegistry(codecRegistry)
                .uuidRepresentation(UuidRepresentation.STANDARD)
                .build();

        this.mongoClient = MongoClients.create(clientSettings);

        /* Connect to Redis */
        this.jedisPool = new JedisPool(config.redisHost, config.redisPort);

        getProxy().getScheduler().runAsync(this, this::setupRedisListener);

        this.playerManager = new PlayerManager();

        this.loadServers();

        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerListener(this, this.playerManager);
    }

    @Override
    public void onDisable()
    {
        this.jedisPool.close();
    }

    private void setupRedisListener()
    {
        Jedis subRedis = null;
        Jedis pubRedis = null;

        try
        {
            /* Creating Jedis object for connecting with redis server */
            subRedis = this.jedisPool.getResource();
            pubRedis = this.jedisPool.getResource();

            /* Creating JedisPubSub object for subscribing with channels */
            RedisManager redisManager = new RedisManager(this, subRedis, pubRedis);
        }
        catch (Exception ex)
        {
            System.out.println("Exception : " + ex.getMessage());
        }
        finally
        {
            if (subRedis != null)
            {
                subRedis.close();
            }

            if (pubRedis != null)
            {
                pubRedis.close();
            }
        }
    }

    public String getNextServerId(String gameMode)
    {
        return gameMode + "-1";
    }

    public ServerData addServer(String id, String gameMode, String name, String ipAddress, int port, String motd, boolean restricted)
    {
        if (id == null) id = this.getNextServerId(gameMode);

        ServerData serverData = new ServerData(id, gameMode, name, ipAddress, port, motd, restricted);
        ServerInfo info = serverData.getServerInfo();

        this.getProxy().getServers().put(id, info);

        this.serverData.put(id, serverData);

        getLogger().info("Added server '" + id + "' (" + ipAddress + ":" + port + ")");

        this.saveServers();

        return serverData;
    }

    public void removeServer(String id)
    {
        ServerInfo info = this.getProxy().getServerInfo(id);

        for (ProxiedPlayer player : info.getPlayers())
        {

        }

        this.getProxy().getServers().remove(id);
        this.serverData.remove(id);

        getLogger().info("Removed server '" + id + "'");

        this.saveServers();
    }

    public ServerData getServerData(String id)
    {
        return this.serverData.get(id);
    }

    private void saveServers()
    {
        Jedis jedis = this.jedisPool.getResource();
        String serverJson = GsonUtil.create().toJson(this.serverData);

        jedis.set("ServerData", serverJson);
    }

    private void loadServers()
    {
        Jedis jedis = this.jedisPool.getResource();
        String serverJson = jedis.get("ServerData");
        Type typeToken = new TypeToken<Map<String, ServerData>>()
        {
        }.getType();

        Map<String, ServerData> serverMap = GsonUtil.create().fromJson(serverJson, typeToken);

        if (serverMap == null) return;

        for (Map.Entry<String, ServerData> entry : serverMap.entrySet())
        {
            this.getProxy().getServers().put(entry.getKey(), entry.getValue().getServerInfo());
        }

        this.serverData = serverMap;
    }

    @EventHandler
    public void onRedisMessage(RedisMessageEvent event)
    {
        switch (event.getChannel())
        {
            case RedisChannels.REGISTER_SERVER:
            {
                RegisterServerPayload payload = Payload.fromJson(event.getMessage(), RegisterServerPayload.class);

                ServerData serverData = addServer(payload.serverId, payload.gameMode, payload.name, payload.ipAddress, payload.port, payload.status, false);

                RegisterServerResponsePayload response = new RegisterServerResponsePayload();
                response.target = payload.sender;
                response.serverId = serverData.id;

                RedisManager.getInstance().publish(RedisChannels.REGISTER_SERVER_RESPONSE, response);

                break;
            }
            case RedisChannels.UNREGISTER_SERVER:
            {
                UnregisterServerPayload payload = Payload.fromJson(event.getMessage(), UnregisterServerPayload.class);

                removeServer(payload.serverId);

                break;
            }
        }
    }


    public static MongoClient getMongoClient()
    {
        return Kawarau.getInstance().mongoClient;
    }

    public static Kawarau getInstance()
    {
        return _instance;
    }
}
