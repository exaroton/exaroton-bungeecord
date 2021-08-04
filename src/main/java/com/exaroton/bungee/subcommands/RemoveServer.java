package com.exaroton.bungee.subcommands;

import com.exaroton.api.APIException;
import com.exaroton.api.server.Server;
import com.exaroton.bungee.ExarotonPlugin;
import com.exaroton.bungee.Message;
import com.exaroton.bungee.SubCommand;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.config.ServerInfo;

import java.util.Map;
import java.util.logging.Level;

public class RemoveServer extends SubCommand {

    /**
     * @param plugin exaroton plugin
     */
    public RemoveServer(ExarotonPlugin plugin) {
        super("remove", plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Message.usage("remove").toComponent());
            return;
        }

        try {
            String name = args[0];
            Map<String, ServerInfo> servers = plugin.getProxy().getServers();
            if (!servers.containsKey(name)) {
                sender.sendMessage(Message.SERVER_NOT_FOUND);
                return;
            }

            Server server = plugin.findServer(name, false);
            plugin.stopListeningToStatus(server.getId());
            plugin.getProxy().getServers().remove(name);
            sender.sendMessage(Message.removed(name).toComponent());
            if (!sender.equals(plugin.getProxy().getConsole())) {
                logger.info(sender.getName() + " removed " + name + " from the proxy.");
            }
        } catch (APIException e) {
            logger.log(Level.SEVERE, "An API Error occurred!", e);
            sender.sendMessage(Message.API_ERROR);
        }
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        return plugin.getProxy().getServers().keySet();
    }

    @Override
    public String getPermission() {
        return "exaroton.remove";
    }
}
