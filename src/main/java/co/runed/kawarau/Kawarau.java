package co.runed.kawarau;

import co.runed.kawarau.events.RedisMessageEvent;
import co.runed.redismessaging.RedisChannels;
import co.runed.redismessaging.payload.Payload;
import co.runed.redismessaging.request.RegisterServerPayload;
import co.runed.redismessaging.request.UnregisterServerPayload;
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
import redis.clients.jedis.JedisPubSub;

import java.net.InetSocketAddress;
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
            JedisPubSub redisListener = new RedisManager(this, subRedis, pubRedis);
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

    public void addServer(String id, String gameMode, String name, String ipAddress, int port, String motd, boolean restricted)
    {
        ServerInfo info = this.getProxy().constructServerInfo(name, InetSocketAddress.createUnresolved(ipAddress, port), motd, restricted);

        ServerData serverData = new ServerData(id, gameMode, info);

        this.getProxy().getServers().put(id, info);

        this.serverData.put(id, serverData);

        getLogger().info("Added server '" + id + "' (" + ipAddress + ":" + port + ")");
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
    }

    public ServerData getServerData(String id)
    {
        return this.serverData.get(id);
    }

    @EventHandler
    public void onRedisMessage(RedisMessageEvent event)
    {
        switch (event.getChannel())
        {
            case RedisChannels.REGISTER_SERVER:
            {
                RegisterServerPayload payload = Payload.fromJson(event.getMessage(), RegisterServerPayload.class);

                addServer(payload.serverId, payload.gameMode, payload.name, payload.ipAddress, payload.port, payload.status, false);

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


    public static class ServerData
    {
        public String id;
        public String gameMode;
        public ServerInfo info;

        private ServerData(String id, String gameMode, ServerInfo info)
        {
            this.id = id;
            this.gameMode = gameMode;
            this.info = info;
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
