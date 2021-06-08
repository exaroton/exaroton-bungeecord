package com.exaroton.bungee.subcommands;

import com.exaroton.api.APIException;
import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import com.exaroton.bungee.ExarotonPlugin;
import com.exaroton.bungee.ServerStatusListener;
import com.exaroton.bungee.SubCommand;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.logging.Level;

public class StartServer extends SubCommand {

    /**
     * @param plugin exaroton plugin
     */
    public StartServer(ExarotonPlugin plugin) {
        super("start", plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(new TextComponent(ChatColor.RED + "Usage: /exaroton start <server>"));
            return;
        }

        try {
            Server server = plugin.findServer(args[0]);
            if (server == null) {
                sender.sendMessage(new TextComponent(ChatColor.RED + "Server not found!"));
                return;
            }

            if (!server.hasStatus(ServerStatus.OFFLINE)) {
                sender.sendMessage(new TextComponent(ChatColor.RED + "Server is not offline"));
                return;
            }

            plugin.listenToStatus(server, sender, null);
            server.start();
            sender.sendMessage(new TextComponent(ChatColor.WHITE + "Starting server..."));
        } catch (APIException e) {
            logger.log(Level.SEVERE, "An API Error occurred!", e);
            sender.sendMessage(new TextComponent(ChatColor.RED + "An API Error occurred. Check your log for more Info!"));
        }
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        return plugin.serverCompletions(args[0], ServerStatus.OFFLINE);
    }

    @Override
    public String getPermission() {
        return "exaroton.start";
    }
}
