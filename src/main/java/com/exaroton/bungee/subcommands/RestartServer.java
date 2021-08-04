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
            sender.sendMessage(Message.usage("restart").toComponent());
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

            ServerStatusListener listener = plugin.listenToStatus(server, sender, plugin.findServerName(server.getAddress()), ServerStatus.ONLINE);
            server.restart();
            sender.sendMessage(Message.action("Restarting", listener.getName(server)).toComponent());
            if (!sender.equals(plugin.getProxy().getConsole())) {
                logger.info(sender.getName() + " is restarting " + listener.getName(server));
            }
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
        return "exaroton.restart";
    }
}
