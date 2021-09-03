package com.exaroton.bungee;

import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import com.exaroton.api.ws.subscriber.ServerStatusSubscriber;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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

    /**
     * exaroton server
     */
    private final Server server;

    private final Map<Integer, List<CompletableFuture<Server>>> waitingFor = new HashMap<>();

    public ServerStatusListener(ExarotonPlugin plugin, boolean restricted, Server server) {
        this.proxy = plugin.getProxy();
        this.logger = plugin.getLogger();
        this.plugin = plugin;
        this.restricted = restricted;
        this.server = server;
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
        plugin.updateServer(newServer);

        if (waitingFor.containsKey(newServer.getStatus())) {
            for (CompletableFuture<Server> future: waitingFor.get(newServer.getStatus())) {
                future.complete(newServer);
            }
        }

        String serverName = this.name == null ? newServer.getName() : this.name;
        if (!oldServer.hasStatus(ServerStatus.ONLINE) && newServer.hasStatus(ServerStatus.ONLINE)) {
            if (proxy.getServers().containsKey(serverName)) {
                this.sendInfo("Server "+serverName+" already exists in bungee network", true);
                return;
            }
            proxy.getServers().put(serverName, plugin.constructServerInfo(serverName, newServer, restricted));
            this.sendInfo(Message.statusChange(serverName, true).getMessage(), expectedStatus == ServerStatus.ONLINE);
        }
        else if (oldServer.hasStatus(ServerStatus.ONLINE) && !newServer.hasStatus(ServerStatus.ONLINE)) {
            proxy.getServers().remove(serverName);
            this.sendInfo(Message.statusChange(serverName, false).getMessage(), expectedStatus == ServerStatus.OFFLINE);
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

    /**
     * unsubscribe from this server
     */
    public void unsubscribe() {
        this.server.unsubscribe();
    }

    /**
     * wait until this server has reached this status
     * @param status expected status
     * @return server with status
     */
    public CompletableFuture<Server> waitForStatus(int status) {
        CompletableFuture<Server> future = new CompletableFuture<>();
        if (!waitingFor.containsKey(status)) {
            waitingFor.put(status, new ArrayList<>());
        }
        waitingFor.get(status).add(future);
        return future;
    }
}
