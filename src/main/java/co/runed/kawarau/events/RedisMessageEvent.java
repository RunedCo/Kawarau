package co.runed.kawarau.events;

import net.md_5.bungee.api.plugin.Event;

public class RedisMessageEvent extends Event
{
    String channel;
    String message;

    public RedisMessageEvent(String channel, String message)
    {
        this.channel = channel;
        this.message = message;
    }

    public String getChannel()
    {
        return channel;
    }

    public String getMessage()
    {
        return message;
    }
}