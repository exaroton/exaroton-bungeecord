package com.exaroton.bungee;

import com.exaroton.api.APIException;
import com.exaroton.api.server.Server;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PluginMessageEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginMessenger implements Listener {
    private final ExarotonPlugin plugin;
    private final Logger logger;

    public PluginMessenger(ExarotonPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @EventHandler
    public void onPluginMessageEvent(PluginMessageEvent event) {
        if (!(event.getSender() instanceof net.md_5.bungee.api.connection.Server) || !event.getTag().equals("exaroton:proxy")) {
            return;
        }

        ByteArrayDataInput input = ByteStreams.newDataInput(event.getData());
        String subchannel = input.readUTF();

        switch (subchannel) {
            case "switch":
                try {
                    String username = input.readUTF();
                    ProxiedPlayer player = plugin.getProxy().getPlayer(username);
                    Server server = plugin.findServer(input.readUTF(), true);
                    ExarotonPluginAPI.switchServer(player, server);
                } catch (APIException|InterruptedException e) {
                    logger.log(Level.SEVERE, "Failed to switch player: ", e);
                }
                break;

            case "info":
                //get server info
                break;

            default:
                logger.warning("Received message on unknown subchannel '" + subchannel + "'");
        }
    }
}
