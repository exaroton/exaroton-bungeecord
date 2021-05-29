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

    public ServerStatusListener(ProxyServer proxy) {
        this.proxy = proxy;
    }

    public ServerStatusListener(ProxyServer proxy, CommandSender sender) {
        this.proxy = proxy;
        this.sender = sender;
    }

    @Override
    public void statusUpdate(Server oldServer, Server newServer) {
        if (!oldServer.hasStatus(ServerStatus.ONLINE) && newServer.hasStatus(ServerStatus.ONLINE)) {
            proxy.getServers().put(newServer.getName(),
                    proxy.constructServerInfo(newServer.getName(),
                            new InetSocketAddress(newServer.getAddress(), newServer.getPort()), newServer.getMotd(), false)
            );
            this.sendInfo(ChatColor.GREEN + "[exaroton] " + newServer.getAddress() + " went online!");
        }
        else if (oldServer.hasStatus(ServerStatus.ONLINE) && !newServer.hasStatus(ServerStatus.ONLINE)) {
            proxy.getServers().remove(newServer.getName());
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
