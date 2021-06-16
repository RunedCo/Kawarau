package co.runed.kawarau;

import co.runed.bolster.common.redis.RedisChannels;
import co.runed.bolster.common.redis.payload.Payload;
import co.runed.kawarau.events.RedisMessageEvent;
import net.md_5.bungee.api.plugin.Plugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

public class RedisManager extends JedisPubSub
{
    private final Plugin plugin;
    private final Jedis subRedis;
    private final Jedis pubRedis;

    private static RedisManager _instance;

    public RedisManager(Plugin plugin, Jedis subRedis, Jedis pubRedis)
    {
        super();

        _instance = this;

        this.plugin = plugin;
        this.subRedis = subRedis;
        this.pubRedis = pubRedis;

        subRedis.subscribe(this, RedisChannels.REGISTER_SERVER, RedisChannels.UNREGISTER_SERVER,
                RedisChannels.UPDATE_SERVER, RedisChannels.REQUEST_SERVERS, RedisChannels.REQUEST_PLAYER_DATA,
                RedisChannels.UPDATE_PLAYER_DATA);
    }

    @Override
    public void onMessage(String channel, String message)
    {
        Payload payload = Payload.fromJson(message, PayloadImpl.class);

        if (payload.target == null || !payload.target.equals("proxy")) return;

        System.out.println("Channel " + channel + " has sent a message from " + payload.sender);

        plugin.getProxy().getPluginManager().callEvent(new RedisMessageEvent(channel, message));
    }

    @Override
    public void onSubscribe(String channel, int subscribedChannels)
    {
        System.out.println("Client is Subscribed to channel : " + channel);
        System.out.println("Client is Subscribed to " + subscribedChannels + " no. of channels");
    }

    @Override
    public void onUnsubscribe(String channel, int subscribedChannels)
    {
        System.out.println("Client is Unsubscribed from channel : " + channel);
        System.out.println("Client is Subscribed to " + subscribedChannels + " no. of channels");
    }

    public void publish(String channel, Payload payload)
    {
        payload.sender = "proxy";

        pubRedis.publish(channel, payload.toJson());
    }

    public static RedisManager getInstance()
    {
        return _instance;
    }

    private static class PayloadImpl extends Payload
    {

    }
}
