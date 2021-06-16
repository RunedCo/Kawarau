package co.runed.kawarau;

import co.runed.kawarau.util.JsonExclude;
import net.md_5.bungee.api.config.ServerInfo;

import java.net.InetSocketAddress;

public class ServerData
{
    public String id;
    public String gameMode;
    String name;
    String ipAddress;
    int port;
    String motd;
    boolean restricted;

    @JsonExclude
    public ServerInfo info = null;

    ServerData(String id, String gameMode, String name, String ipAddress, int port, String motd, boolean restricted)
    {
        this.id = id;
        this.gameMode = gameMode;
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.motd = motd;
        this.restricted = restricted;
    }

    public ServerInfo getServerInfo()
    {
        if (info == null)
        {
            this.info = Kawarau.getInstance().getProxy().constructServerInfo(name, InetSocketAddress.createUnresolved(ipAddress, port), motd, restricted);
        }

        return this.info;
    }
}
