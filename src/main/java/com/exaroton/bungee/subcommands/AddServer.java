package com.exaroton.bungee.subcommands;

import com.exaroton.api.APIException;
import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import com.exaroton.bungee.ExarotonPlugin;
import com.exaroton.bungee.Message;
import com.exaroton.bungee.ServerStatusListener;
import com.exaroton.bungee.SubCommand;
import net.md_5.bungee.api.CommandSender;

import java.util.List;
import java.util.logging.Level;

public class AddServer extends SubCommand {

    /**
     * @param plugin exaroton plugin
     */
    public AddServer(ExarotonPlugin plugin) {
        super("add", plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Message.usage("add").toComponent());
            return;
        }

        try {
            Server server = plugin.findServer(args[0], true);
            if (server == null) {
                sender.sendMessage(Message.SERVER_NOT_FOUND);
                return;
            }

            ServerStatusListener listener = plugin.listenToStatus(server, sender, plugin.findServerName(server.getAddress()), ServerStatus.ONLINE);
            String name = listener.getName(server);
            sender.sendMessage(Message.watching(name).toComponent());

            if (server.hasStatus(ServerStatus.ONLINE)) {
                if (plugin.getProxy().getServers().containsKey(name)) {
                    sender.sendMessage(Message.error("Failed to add server: A server with the name " + name + " already exists in proxy.").toComponent());
                }
                else {
                    plugin.getProxy().getServers().put(name, plugin.constructServerInfo(name, server, false));
                    sender.sendMessage(Message.added(name).toComponent());
                    if (!sender.equals(plugin.getProxy().getConsole())) {
                        logger.info(sender.getName() + " is adding " + name + " to the proxy.");
                    }
                }
            }
        } catch (APIException e) {
            logger.log(Level.SEVERE, "An API Error occurred!", e);
            sender.sendMessage(Message.API_ERROR);
        }
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        return plugin.serverCompletionsNotInProxy(args[0]);
    }

    @Override
    public String getPermission() {
        return "exaroton.add";
    }
}
