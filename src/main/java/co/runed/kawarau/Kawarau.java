package co.runed.kawarau;

import co.runed.bolster.common.ServerData;
import co.runed.bolster.common.gson.GsonUtil;
import co.runed.bolster.common.redis.RedisChannels;
import co.runed.bolster.common.redis.RedisManager;
import co.runed.bolster.common.redis.payload.Payload;
import co.runed.bolster.common.redis.request.ListServersPayload;
import co.runed.bolster.common.redis.request.ServerDataPayload;
import co.runed.bolster.common.redis.request.UnregisterServerPayload;
import co.runed.bolster.common.redis.response.ListServersResponsePayload;
import co.runed.bolster.common.redis.response.RegisterServerResponsePayload;
import co.runed.kawarau.events.RedisMessageEvent;
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

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Kawarau extends Plugin implements Listener
{
    public Config config;
    private MongoClient mongoClient;
    private PlayerManager playerManager;
    private RedisManager redisManager;

    private Map<String, BungeeServerData> serverData = new HashMap<>();

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
        var redisChannels = Arrays.asList(RedisChannels.REGISTER_SERVER, RedisChannels.UNREGISTER_SERVER,
                RedisChannels.UPDATE_SERVER, RedisChannels.LIST_SERVERS, RedisChannels.REQUEST_PLAYER_DATA,
                RedisChannels.UPDATE_PLAYER_DATA);

        this.redisManager = new RedisManager(config.redisHost, config.redisPort, null, null, redisChannels);
        this.redisManager.setSenderId("proxy");
        this.redisManager.setMessageHandler((channel, message) -> getProxy().getPluginManager().callEvent(new RedisMessageEvent(channel, message)));

        getProxy().getScheduler().runAsync(this, redisManager::setup);

        this.playerManager = new PlayerManager();

        getProxy().getScheduler().schedule(this, this::loadServers, 2L, TimeUnit.SECONDS);

//        getProxy().getScheduler().schedule(this, this::heartbeat, 0L, 1, TimeUnit.MINUTES);

        getProxy().getPluginManager().registerListener(this, this);
        getProxy().getPluginManager().registerListener(this, this.playerManager);
    }

    private void heartbeat()
    {
        Map<String, BungeeServerData> servers = new HashMap<>(this.serverData);

        for (Map.Entry<String, BungeeServerData> entry : servers.entrySet())
        {
            entry.getValue().info.ping((serverPing, error) -> {
                if (error != null)
                {
                    removeServer(entry.getKey());
                }
            });
        }
    }

    public String getNextServerId(String gameMode)
    {
        int serverNumber = 1;
        String id = null;

        while (id == null || serverData.containsKey(id))
        {
            id = gameMode + "-" + serverNumber;

            serverNumber++;
        }

        return id;
    }

    public BungeeServerData addServer(ServerData serverData)
    {
        if (serverData.id == null) serverData.id = this.getNextServerId(serverData.gameMode);

        if (serverData.ipAddress == null || serverData.port <= 0)
        {
            getLogger().severe("Error adding server '" + serverData.id + "'");
            return null;
        }

        BungeeServerData bungeeServerData = new BungeeServerData(serverData);
        ServerInfo info = bungeeServerData.getServerInfo();

        this.getProxy().getServers().put(serverData.id, info);

        this.serverData.put(serverData.id, bungeeServerData);

        this.saveServers();

        return bungeeServerData;
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

    public BungeeServerData getServerData(String id)
    {
        return this.serverData.get(id);
    }

    private void saveServers()
    {
        String serverJson = GsonUtil.create().toJson(this.serverData);

        RedisManager.getInstance().set("ServerData", serverJson);
    }

    private void loadServers()
    {
        String serverJson = RedisManager.getInstance().get("ServerData");
        Type typeToken = new TypeToken<Map<String, BungeeServerData>>()
        {
        }.getType();

        Map<String, BungeeServerData> serverMap = GsonUtil.create().fromJson(serverJson, typeToken);

        if (serverMap == null) return;

        for (Map.Entry<String, BungeeServerData> entry : serverMap.entrySet())
        {
            ServerInfo info = entry.getValue().getServerInfo();
            this.getProxy().getServers().put(entry.getKey(), info);
        }

        this.serverData = serverMap;
    }

    @EventHandler
    public void onRedisMessage(RedisMessageEvent event)
    {
        switch (event.getChannel())
        {
            case RedisChannels.UPDATE_SERVER:
            case RedisChannels.REGISTER_SERVER:
            {
                ServerDataPayload payload = Payload.fromJson(event.getMessage(), ServerDataPayload.class);

                BungeeServerData serverData = addServer(payload.serverData);

                if (event.getChannel().equals(RedisChannels.REGISTER_SERVER) && serverData != null)
                {
                    RegisterServerResponsePayload response = new RegisterServerResponsePayload();
                    response.target = payload.sender;
                    response.serverId = serverData.id;

                    RedisManager.getInstance().publish(RedisChannels.REGISTER_SERVER_RESPONSE, response);
                }

                getLogger().info((event.getChannel().equals(RedisChannels.REGISTER_SERVER) ? "Added" : "Updated")
                        + " server '" + serverData.id + "' (" + serverData.ipAddress + ":" + serverData.port + ")");

                sendServerData("*");

                break;
            }
            case RedisChannels.LIST_SERVERS:
            {
                ListServersPayload payload = Payload.fromJson(event.getMessage(), ListServersPayload.class);

                sendServerData(payload.sender);

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

    private void sendServerData(String target)
    {
        ListServersResponsePayload payload = new ListServersResponsePayload();

        for (var entry : this.serverData.entrySet())
        {
            payload.servers.put(entry.getKey(), entry.getValue());
        }

        RedisManager.getInstance().publish(target, RedisChannels.LIST_SERVERS_RESPONSE, payload);
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
