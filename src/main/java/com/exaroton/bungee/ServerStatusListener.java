package com.exaroton.bungee;

import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import com.exaroton.api.ws.subscriber.ServerStatusSubscriber;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;

import java.net.InetSocketAddress;

public class ServerStatusListener extends ServerStatusSubscriber {

    /**
     * bungee proxy
     */
    private final ProxyServer proxy;

    /**
     * optional command sender
     */
    private CommandSender sender;

    /**
     * optional server name
     */
    private String name;

    public ServerStatusListener(ProxyServer proxy) {
        this.proxy = proxy;
    }

    public ServerStatusListener setName(String name) {
        if (name != null) {
            this.name = name;
        }
        return this;
    }

    public ServerStatusListener setSender(CommandSender sender) {
        if (sender != null) {
            this.sender = sender;
        }
        return this;
    }

    @Override
    public void statusUpdate(Server oldServer, Server newServer) {
        String serverName = this.name == null ? newServer.getName() : this.name;
        if (!oldServer.hasStatus(ServerStatus.ONLINE) && newServer.hasStatus(ServerStatus.ONLINE)) {
            if (proxy.getServers().containsKey(serverName)) {
                this.sendInfo("Server "+serverName+" already exists in bungee network");
                return;
            }
            proxy.getServers().put(serverName,
                    proxy.constructServerInfo(newServer.getName(),
                            new InetSocketAddress(newServer.getHost(), newServer.getPort()), newServer.getMotd(), false)
            );
            this.sendInfo(ChatColor.GREEN + "[exaroton] " + newServer.getAddress() + " went online!");
        }
        else if (oldServer.hasStatus(ServerStatus.ONLINE) && !newServer.hasStatus(ServerStatus.ONLINE)) {
            proxy.getServers().remove(serverName);
            this.sendInfo(ChatColor.RED + "[exaroton] " + newServer.getAddress() + " is no longer online!");
        }
    }

    /**
     * send message to all subscribed sources
     * @param message message
     */
    public void sendInfo(String message) {
        TextComponent text = new TextComponent(message);
        proxy.getConsole().sendMessage(text);
        if (sender != null) {
            sender.sendMessage(text);
            //unsubscribe user from further updates
            this.sender = null;
        }
    }
}
