package com.exaroton.bungee;

import com.exaroton.api.APIException;
import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ExarotonPluginAPI {

    private static ExarotonPlugin plugin;

    /**
     * find a server by it's
     * - proxy name
     * - id
     * - address (e.g. example.exaroton.me)
     * - server name
     * @param query search query
     * @return found server
     * @throws APIException exception while fetching server list
     */
    public static Server findServer(String query) throws APIException {
        return plugin.findServer(query, true);
    }

    /**
     * start a server and add it to the proxy
     * @param server server to start
     * @return started successfully
     * @throws APIException exception while starting server
     */
    public static boolean startServer(Server server) throws APIException {
        if (server == null) {
            throw new NullPointerException("No server provided!");
        }

        if (!server.hasStatus(new int[]{ServerStatus.OFFLINE, ServerStatus.CRASHED})) {
            return false;
        }

        watchServer(server);
        server.start();
        return true;
    }

    /**
     * stop a server and remove it from the proxy
     * the server will automatically be added again when it goes online again
     * @param server server to stop
     * @return stopped successfully
     * @throws APIException exception while stopping server
     */
    public static boolean stopServer(Server server) throws APIException {
        if (server == null) {
            throw new NullPointerException("No server provided!");
        }

        if (!server.hasStatus(ServerStatus.ONLINE)) {
            return false;
        }

        watchServer(server);
        server.stop();
        return true;
    }

    /**
     * restart a server and add it to the proxy
     * @param server server to restart
     * @return restarted successfully
     * @throws APIException exception while restarting server
     */
    public static boolean restartServer(Server server) throws APIException {
        if (server == null) {
            throw new NullPointerException("No server provided!");
        }

        if (!server.hasStatus(ServerStatus.ONLINE)) {
            return false;
        }

        watchServer(server);
        server.restart();
        return true;
    }

    /**
     * watch status updates for this server and add it to the proxy if it's currently online
     * @param server server to add
     * @return was it added
     */
    public static boolean addServer(Server server) {
        if (server == null) {
            throw new NullPointerException("No server provided!");
        }

        ServerStatusListener listener = watchServer(server);
        String name = listener.getName(server);

        if (server.hasStatus(ServerStatus.ONLINE)) {
            if (plugin.getProxy().getServers().containsKey(name)) {
                return false;
            }
            else {
                plugin.getProxy().getServers().put(name, plugin.constructServerInfo(name, server, false));
                return true;
            }
        }
        return false;
    }

    /**
     * watch this server
     * add it the proxy when it becomes online
     * remove it when it goes offline
     * @param server server to watch
     */
    public static ServerStatusListener watchServer(Server server) {
        if (server == null) {
            throw new NullPointerException("No server provided!");
        }

        return plugin.listenToStatus(server, plugin.findServerName(server.getAddress()));
    }

    /**
     * stop watching updates for this server
     * @param server server
     */
    public static void stopWatchingServer(Server server) {
        if (server == null) {
            throw new NullPointerException("No server provided!");
        }

        plugin.stopListeningToStatus(server.getId());
    }

    /**
     * send this player to this server
     * start the server or add it to the network if necessary
     * @param player player to move
     * @param server server to move to
     * @throws APIException exception starting the server
     * @throws InterruptedException interrupted while
     */
    public static void switchServer(ProxiedPlayer player, Server server) throws APIException, InterruptedException {
        if (server == null) {
            throw new NullPointerException("No server provided!");
        }

        if (server.hasStatus(new int[]{ServerStatus.OFFLINE, ServerStatus.CRASHED, ServerStatus.LOADING, ServerStatus.STARTING, ServerStatus.PREPARING})) {
            ServerStatusListener listener = watchServer(server);
            if (server.hasStatus(new int[]{ServerStatus.OFFLINE, ServerStatus.CRASHED})) {
                server.start();
            }
            try {
                server = listener.waitForStatus(ServerStatus.ONLINE).get();
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to start server", e);
            }
        }

        movePlayer(player, server);
    }

    private static void movePlayer(ProxiedPlayer player, Server server) {
        String name = plugin.findServerName(server.getAddress(), server.getName());
        Map<String, ServerInfo> servers = plugin.getProxy().getServers();

        // add to proxy if needed
        if (!servers.containsKey(name)) {
            servers.put(name, plugin.constructServerInfo(name, server, false));
        }
        player.connect(servers.get(name));
    }

    static void setPlugin(ExarotonPlugin plugin) {
        ExarotonPluginAPI.plugin = plugin;
    }
}
