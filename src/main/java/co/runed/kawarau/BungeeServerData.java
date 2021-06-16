package co.runed.kawarau;

import co.runed.bolster.common.ServerData;
import co.runed.kawarau.util.JsonExclude;
import net.md_5.bungee.api.config.ServerInfo;

import java.net.InetSocketAddress;

public class BungeeServerData extends ServerData
{
    @JsonExclude
    public ServerInfo info = null;

    public BungeeServerData(String id, String gameMode, String name, String ipAddress, int port, String motd, boolean restricted)
    {
        super(id, gameMode, name, ipAddress, port, motd, restricted);
    }

    public ServerInfo getServerInfo()
    {
        if (info == null)
        {
            this.info = Kawarau.getInstance().getProxy().constructServerInfo(id, InetSocketAddress.createUnresolved(ipAddress, port), motd, restricted);
        }

        return this.info;
    }
}
