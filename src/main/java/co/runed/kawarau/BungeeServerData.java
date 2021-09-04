package co.runed.kawarau;

import co.runed.dayroom.ServerData;
import co.runed.dayroom.gson.JsonExclude;
import net.md_5.bungee.api.config.ServerInfo;

import java.net.InetSocketAddress;

public class BungeeServerData extends ServerData {
    @JsonExclude
    public ServerInfo info = null;

    public BungeeServerData(ServerData data) {
        super(data.id, data.gameMode, data.name, data.iconMaterial, data.status, data.ipAddress, data.port, data.restricted);

        this.maxPlayers = data.maxPlayers;
        this.onlinePlayers = data.onlinePlayers;
        this.maxPremiumPlayers = data.maxPremiumPlayers;
    }

    public ServerInfo getServerInfo() {
        if (info == null) {
            this.info = Kawarau.getInstance().getProxy().constructServerInfo(id, InetSocketAddress.createUnresolved(ipAddress, port), status, restricted);
        }

        return this.info;
    }
}
