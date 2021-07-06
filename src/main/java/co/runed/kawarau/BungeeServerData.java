package co.runed.kawarau;

import co.runed.bolster.common.ServerData;
import co.runed.kawarau.util.JsonExclude;
import net.md_5.bungee.api.config.ServerInfo;

import java.net.InetSocketAddress;

public class BungeeServerData extends ServerData
{
    @JsonExclude
    public ServerInfo info = null;

    public BungeeServerData(ServerData data)
    {
        super(data.id, data.gameMode, data.name, data.iconMaterial, data.status, data.ipAddress, data.port, data.restricted);

        this.maxPlayers = data.maxPlayers;
        this.currentPlayers = data.currentPlayers;
        this.maxPremiumPlayers = data.maxPremiumPlayers;
    }

    public ServerInfo getServerInfo()
    {
        if (info == null)
        {
            this.info = Kawarau.getInstance().getProxy().constructServerInfo(id, InetSocketAddress.createUnresolved(ipAddress, port), status, restricted);
        }

        return this.info;
    }
}
