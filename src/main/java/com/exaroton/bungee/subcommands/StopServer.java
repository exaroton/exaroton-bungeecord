package com.exaroton.bungee.subcommands;

import com.exaroton.api.APIException;
import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import com.exaroton.bungee.ExarotonPlugin;
import com.exaroton.bungee.Message;
import com.exaroton.bungee.ServerStatusListener;
import com.exaroton.bungee.SubCommand;
import net.md_5.bungee.api.CommandSender;

import java.util.logging.Level;

public class StopServer extends SubCommand {

    /**
     * @param plugin exaroton plugin
     */
    public StopServer(ExarotonPlugin plugin) {
        super("stop", plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Message.usage("stop").toComponent());
            return;
        }

        try {
            Server server = plugin.findServer(args[0], true);
            if (server == null) {
                sender.sendMessage(Message.SERVER_NOT_FOUND);
                return;
            }

            if (!server.hasStatus(ServerStatus.ONLINE)) {
                sender.sendMessage(Message.SERVER_NOT_ONLINE);
                return;
            }

            ServerStatusListener listener = plugin.listenToStatus(server, sender, plugin.findServerName(server.getAddress()), ServerStatus.OFFLINE);
            server.stop();
            sender.sendMessage(Message.action("Stopping", listener.getName(server)).toComponent());
        } catch (APIException e) {
            logger.log(Level.SEVERE, "An API Error occurred!", e);
            sender.sendMessage(Message.API_ERROR);
        }
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        return plugin.serverCompletions(args[0], ServerStatus.ONLINE);
    }

    @Override
    public String getPermission() {
        return "exaroton.stop";
    }
}
