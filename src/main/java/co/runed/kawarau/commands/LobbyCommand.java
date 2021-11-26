package co.runed.kawarau.commands;

import co.runed.kawarau.Kawarau;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class LobbyCommand extends Command {
    public LobbyCommand() {
        super("lobby", "kawarau.command.lobby");
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (sender instanceof ProxiedPlayer player) {
            var server = Kawarau.getInstance().getBestServer("lobby");

            if (server == null) return;

            var info = server.getServerInfo();

            for (var p : info.getPlayers()) {
                if (p.getUniqueId().equals(player.getUniqueId())) {
                    player.sendMessage(ChatMessageType.CHAT, new ComponentBuilder().append("You are already in the lobby!").color(ChatColor.RED).create());
                    return;
                }
            }

            player.sendMessage(ChatMessageType.CHAT, new ComponentBuilder().append("Connecting to lobby...").create());
            player.connect(info);
        }
    }
}
