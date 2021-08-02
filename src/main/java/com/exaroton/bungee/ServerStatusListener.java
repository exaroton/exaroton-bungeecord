package com.exaroton.bungee;

import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import com.exaroton.api.ws.subscriber.ServerStatusSubscriber;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ServerStatusListener extends ServerStatusSubscriber {

    /**
     * bungee proxy
     */
    private final ProxyServer proxy;

    /**
     * plugin logger
     */
    private final Logger logger;

    /**
     * exaroton plugin
     */
    private final ExarotonPlugin plugin;

    /**
     * optional command sender
     */
    private CommandSender sender;

    /**
     * optional server name
     */
    private String name;

    /**
     * is this server restricted
     */
    private final boolean restricted;

    /**
     * server status that the user is waiting for
     */
    private int expectedStatus;

    public ServerStatusListener(ExarotonPlugin plugin, boolean restricted) {
        this.proxy = plugin.getProxy();
        this.logger = plugin.getLogger();
        this.plugin = plugin;
        this.restricted = restricted;
    }

    public ServerStatusListener setName(String name) {
        if (name != null) {
            this.name = name;
        }
        return this;
    }

    public String getName(Server server) {
        return name != null ? name : server.getName();
    }

    public ServerStatusListener setSender(CommandSender sender, int expectedStatus) {
        if (sender != null) {
            this.sender = sender;
            this.expectedStatus = expectedStatus;
        }
        return this;
    }

    @Override
    public void statusUpdate(Server oldServer, Server newServer) {
        String serverName = this.name == null ? newServer.getName() : this.name;
        if (!oldServer.hasStatus(ServerStatus.ONLINE) && newServer.hasStatus(ServerStatus.ONLINE)) {
            if (proxy.getServers().containsKey(serverName)) {
                this.sendInfo("Server "+serverName+" already exists in bungee network", true);
                return;
            }
            proxy.getServers().put(serverName, plugin.constructServerInfo(serverName, newServer, restricted));
            this.sendInfo(Message.statusChange(serverName, true).getMessage(), newServer.hasStatus(expectedStatus));
        }
        else if (oldServer.hasStatus(ServerStatus.ONLINE) && !newServer.hasStatus(ServerStatus.ONLINE)) {
            proxy.getServers().remove(serverName);
            this.sendInfo(Message.statusChange(serverName, false).getMessage(), newServer.hasStatus(expectedStatus));
        }
    }

    /**
     * send message to all subscribed sources
     * @param message message
     */
    public void sendInfo(String message, boolean unsubscribe) {
        logger.log(Level.INFO, message);
        TextComponent text = new TextComponent(message + ChatColor.RESET);
        if (sender != null && !sender.equals(proxy.getConsole())) {
            sender.sendMessage(text);
            if (unsubscribe) {
                //unsubscribe user from further updates
                this.sender = null;
            }
        }
    }
}
