package com.exaroton.bungee.subcommands;

import com.exaroton.api.APIException;
import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import com.exaroton.bungee.*;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public class SwitchServer extends SubCommand {

    /**
     * @param plugin exaroton plugin
     */
    public SwitchServer(ExarotonPlugin plugin) {
        super("switch", "Switch to a server and start it if necessary", plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(Message.usage("switch").toComponent());
            return;
        }

        try {
            if (!(sender instanceof ProxiedPlayer)) {
                sender.sendMessage(Message.NOT_PLAYER);
                return;
            }

            Server server = plugin.findServer(args[0], true);
            if (server == null) {
                sender.sendMessage(Message.SERVER_NOT_FOUND);
                return;
            }

            sender.sendMessage(Message.switching(plugin.findServerName(server.getAddress(), server.getName())).toComponent());
            ExarotonPluginAPI.switchServer((ProxiedPlayer) sender, server);
        } catch (APIException e) {
            logger.log(Level.SEVERE, "An API Error occurred!", e);
            sender.sendMessage(Message.API_ERROR);
        } catch (RuntimeException | InterruptedException e) {
            logger.log(Level.SEVERE, "Failed to execute switch command", e);
            sender.sendMessage(Message.error("Failed to execute switch command. Check your console for details.").toComponent());
        }
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        return plugin.serverCompletions(args[0], null);
    }

    @Override
    public String getPermission() {
        return "exaroton.switch";
    }
}
