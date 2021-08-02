package com.exaroton.bungee.subcommands;

import com.exaroton.api.APIException;
import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import com.exaroton.bungee.ExarotonPlugin;
import com.exaroton.bungee.SubCommand;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.logging.Level;

public class RestartServer extends SubCommand {

    /**
     * @param plugin exaroton plugin
     */
    public RestartServer(ExarotonPlugin plugin) {
        super("restart", plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(new TextComponent(ChatColor.RED + "Usage: /exaroton restart <server>"));
            return;
        }

        try {
            Server server = plugin.findServer(args[0], true);
            if (server == null) {
                sender.sendMessage(new TextComponent(ChatColor.RED + "Server not found!"));
                return;
            }

            if (!server.hasStatus(ServerStatus.ONLINE)) {
                sender.sendMessage(new TextComponent(ChatColor.RED + "Server is not online"));
                return;
            }

            plugin.listenToStatus(server, sender, plugin.findServerName(server.getAddress()), ServerStatus.ONLINE);
            server.restart();
            sender.sendMessage(new TextComponent(ChatColor.WHITE + "Restarting server..."));
        } catch (APIException e) {
            logger.log(Level.SEVERE, "An API Error occurred!", e);
            sender.sendMessage(new TextComponent(ChatColor.RED + "An API Error occurred. Check your log for more Info!"));
        }
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        return plugin.serverCompletions(args[0], ServerStatus.ONLINE);
    }

    @Override
    public String getPermission() {
        return "exaroton.restart";
    }
}
