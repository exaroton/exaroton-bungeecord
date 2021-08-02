package com.exaroton.bungee;

import com.exaroton.api.APIException;
import com.exaroton.api.ExarotonClient;
import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ExarotonPlugin extends Plugin {

    /**
     * exaroton API client
     */
    private ExarotonClient exarotonClient;

    /**
     * main configuration (config.yml)
     */
    private Configuration config;

    /**
     * bungee config
     */
    private Configuration bungeeConfig;

    /**
     * logger
     */
    private final Logger logger = this.getLogger();

    /**
     * server cache
     */
    private Server[] serverCache;

    /**
     * exaroton servers from bungee config
     * name -> address
     */
    private Map<String, String> bungeeServers = new HashMap<>();

    /**
     * server status listeners
     * serverid -> status listener
     */
    private final HashMap<String, ServerStatusListener> statusListeners = new HashMap<>();

    @Override
    public void onEnable() {
        try {
            this.loadConfig();
            this.loadBungeeConfig();
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to load config file!", e);
            return;
        }
        if (this.createExarotonClient()) {
            this.registerCommands();
            this.runAsyncTasks();
        }
    }

    /**
     * load and/or create config.yml
     * @return configuration file
     * @throws IOException exception loading config
     */
    private File getConfigFile() throws IOException {
        if (this.getDataFolder().mkdir()) {
            logger.log(Level.INFO, "Creating config file");
        }
        File configFile = new File(this.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            InputStream in = getResourceAsStream("config.yml");
            Files.copy(in, configFile.toPath());
        }
        return configFile;
    }

    /**
     * load main configuration
     * @throws IOException exception loading config
     */
    private void loadConfig() throws IOException {
        File configFile = this.getConfigFile();
        ConfigurationProvider provider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        this.config = provider.load(configFile);
        Configuration defaultConfig = provider.load(getResourceAsStream("config.yml"));
        provider.save(this.addDefaults(config, defaultConfig), configFile);
    }

    private void loadBungeeConfig() throws IOException {
        this.bungeeConfig = ConfigurationProvider.getProvider(YamlConfiguration.class)
                .load(new File(getProxy().getPluginsFolder().getParent(), "config.yml"));
        Configuration servers = bungeeConfig.getSection("servers");
        for (String name: servers.getKeys()) {
            String address = servers.getString(name+".address");
            if (address.matches(".*\\.exaroton\\.me(:\\d+)?")) {
                this.bungeeServers.put(name, address.replaceAll(":\\d+", ""));
            }
        }
    }

    /**
     * update config recursively
     * @param config current configuration
     * @param defaults defaults
     * @return config with defaults
     */
    private Configuration addDefaults(Configuration config, Configuration defaults) {
        for (String key: defaults.getKeys()) {
            Object value = defaults.get(key);

            if (value instanceof Configuration) {
                addDefaults(config.getSection(key), defaults.getSection(key));
            }
            else if (!config.getKeys().contains(key)) {
                config.set(key, value);
            }
        }
        return config;
    }

    /**
     * create exaroton client
     * @return was the client successfully created
     */
    public boolean createExarotonClient() {
        String apiToken = this.config.getString("apiToken");
        if (apiToken == null || apiToken.length() == 0 || apiToken.equals("example-token")) {
            logger.log(Level.SEVERE, "Invalid API Token specified!");
            return false;
        }
        else {
            this.exarotonClient = new ExarotonClient(apiToken);
            return true;
        }
    }

    /**
     * register commands
     */
    private void registerCommands() {
        PluginManager pluginManager = this.getProxy().getPluginManager();
        pluginManager.registerCommand(this, new ExarotonCommand(this));
    }

    /**
     * update server cache to provided servers
     * @throws APIException API exceptions
     * @return exaroton servers
     */
    public Server[] fetchServers() throws APIException {
        this.getProxy().getScheduler().schedule(this, () -> this.serverCache = null, 1, TimeUnit.MINUTES);
        return this.serverCache = exarotonClient.getServers();
    }

    /**
     * find a server
     * if a server can't be uniquely identified then the id will be preferred
     * @param query server name, address or id
     * @param force skip cache
     * @return found server or null
     * @throws APIException exceptions from the API
     */
    public Server findServer(String query, boolean force) throws APIException {
        if (bungeeServers.containsKey(query)) {
            query = bungeeServers.get(query);
        }
        final String finalQuery = query;

        Server[] servers = serverCache != null && !force ? serverCache : fetchServers();

        servers = Arrays.stream(servers)
                .filter(server -> matchExact(server, finalQuery))
                .toArray(Server[]::new);

        switch (servers.length) {
            case 0:
                return null;

            case 1:
                return servers[0];

            default:
                Optional<Server> server = Arrays.stream(servers).filter(s -> s.getId().equals(finalQuery)).findFirst();
                return server.orElse(servers[0]);
        }
    }

    /**
     * does this server match the query exactly
     * @param server exaroton server
     * @param query server name, address or id
     * @return does the server match exactly
     */
    public boolean matchExact(Server server, String query) {
        return server.getAddress().equals(query) || server.getName().equals(query) || server.getId().equals(query);
    }

    /**
     * does this server start with the query
     * @param server exaroton server
     * @param query partial server name, address or id
     * @return does the server start with the query
     */
    public boolean matchBeginning(Server server, String query) {
        return server.getAddress().startsWith(query) || server.getName().startsWith(query) || server.getId().startsWith(query);
    }

    /**
     * find auto completions by a query and status
     * @param query partial server name, address or ID
     * @param status server status
     * @return all matching server names, addresses and IDs
     */
    public Iterable<String> serverCompletions(String query, int status) {
        if (serverCache == null) {
            try {
                this.fetchServers();
            } catch (APIException e) {
                logger.log(Level.SEVERE, "Failed to load completions", e);
                return new ArrayList<>();
            }
        }
        Server[] matching = Arrays.stream(serverCache).filter(server -> matchBeginning(server, query) && server.hasStatus(status)).toArray(Server[]::new);
        List<String> result = this.bungeeServers.keySet().stream().filter(s -> s.startsWith(query)).collect(Collectors.toList());

        for (Server server: matching) {
            result.add(server.getName());
        }
        for (Server server: matching) {
            result.add(server.getAddress());
        }
        for (Server server: matching) {
            result.add(server.getId());
        }

        return result;
    }

    /**
     * listen to server status
     * if there already is a status listener then add the sender and/or name
     * @param server server to subscribe to
     * @param sender command sender to update
     * @param name server name in bungee server list
     * @param expectedStatus status user is waiting for
     */
    public ServerStatusListener listenToStatus(Server server, CommandSender sender, String name, int expectedStatus) {
        return this.listenToStatus(server, sender, name, expectedStatus, false);
    }

    /**
     * listen to server status
     * if there already is a status listener then add the sender and/or name
     * @param server server to subscribe to
     * @param sender command sender to update
     * @param name server name in bungee server list
     * @param expectedStatus status user is waiting for
     * @param restricted is server restricted
     */
    public ServerStatusListener listenToStatus(Server server, CommandSender sender, String name, int expectedStatus, boolean restricted) {
        if (statusListeners.containsKey(server.getId())) {
            return statusListeners.get(server.getId())
                    .setSender(sender, expectedStatus)
                    .setName(name);
        }
        server.subscribe();
        ServerStatusListener listener = new ServerStatusListener(this, restricted)
                .setSender(sender, expectedStatus)
                .setName(name);
        server.addStatusSubscriber(listener);
        statusListeners.put(server.getId(), listener);
        return listener;
    }

    /**
     * start watching servers in the bungee config
     */
    public void runAsyncTasks() {
        this.getProxy().getScheduler().runAsync(this, () -> {
            this.watchServers();
            this.autoStartServers();
        });
    }


    /**
     * watch this server
     * @param name server name (lobby)
     * @param address server address (example.exaroton.me)
     * @param restricted is server restricted
     */
    public void watchServer(String name, String address, boolean restricted) {
        try {
            Server server = this.findServer(address, false);
            if (server.hasStatus(ServerStatus.ONLINE)) {
                logger.info("Updating server address and port for " + name + "...");
                this.getProxy().getServers().remove(name);
                this.getProxy().getServers().put(name, this.constructServerInfo(name, server, restricted));
            } else {
                this.getProxy().getServers().remove(name);
                logger.info("Server " + name + " is offline, removed it from the server list!");
            }
            this.listenToStatus(server, null, name, -1 , restricted);
        } catch (APIException e) {
            logger.log(Level.SEVERE, "Failed to access API, not watching "+name);
        }
    }

    /**
     * watch servers in bungee config
     */
    public void watchServers() {
        if (!config.getBoolean("watch-servers", false)) return;
        Configuration servers = bungeeConfig.getSection("servers");
        for (String name: servers.getKeys()) {
            String address = servers.getString(name+".address");
            if (address.matches(".*\\.exaroton\\.me(:\\d+)?")) {
                this.watchServer(name, address, servers.getBoolean(name+".restricted", false));
            }
        }
    }

    /**
     * generate server info
     * @param name server name in network
     * @param server server
     * @param restricted restricted
     * @return bungee server info
     */
    public ServerInfo constructServerInfo(String name, Server server, boolean restricted) {
        return this.getProxy().constructServerInfo(name, new InetSocketAddress(server.getHost(), server.getPort()), server.getMotd(), restricted);
    }

    /**
     * automatically start servers from the config (asynchronous)
     */
    public void autoStartServers() {
        if (!config.getBoolean("auto-start.enabled")) return;
        for (String query: config.getStringList("auto-start.servers")) {
            try {
                Server server = this.findServer(query, false);

                if (server == null) {
                    logger.log(Level.WARNING, "Can't start " + query + ": Server not found");
                    continue;
                }

                if (server.hasStatus(new int[]{ServerStatus.ONLINE, ServerStatus.STARTING,
                        ServerStatus.LOADING, ServerStatus.PREPARING, ServerStatus.RESTARTING})) {
                    logger.log(Level.INFO, server.getAddress() + " is already online or starting!");
                    this.listenToStatus(server, null, findServerName(server.getAddress()), ServerStatus.ONLINE);
                    return;
                }

                if (!server.hasStatus(new int[]{ServerStatus.OFFLINE, ServerStatus.CRASHED})) {
                    logger.log(Level.SEVERE, "Can't start " + server.getAddress() + ": Server isn't offline.");
                    continue;
                }

                logger.log(Level.INFO, "Starting "+ server.getAddress());
                this.listenToStatus(server, null, findServerName(server.getAddress()), ServerStatus.ONLINE);
                server.start();

            } catch (APIException e) {
                logger.log(Level.SEVERE, "Failed to start start "+ query +"!", e);
            }
        }
    }

    /**
     * try to find this server in the bungee config
     * @param address exaroton address e.g. example.exaroton.me
     * @return server name e.g. lobby
     */
    public String findServerName(String address) {
        Configuration servers = this.bungeeConfig.getSection("servers");
        for (String serverName: servers.getKeys()) {
            if (servers.getString(serverName + ".address").matches(Pattern.quote(address) + ":\\d+")) {
                return serverName;
            }
        }
        return null;
    }

    /**
     * get bungeecord configuration
     * @return bungee config
     */
    private Configuration getBungeeConfig() {
        return this.bungeeConfig;
    }
}
