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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final Map<String, String> bungeeServers = new HashMap<>();

    /**
     * server status listeners
     * server id -> status listener
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
            ExarotonPluginAPI.setPlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (this.exarotonClient != null) {
            this.autoStopServers();
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
        for (String name : servers.getKeys()) {
            String address = servers.getString(name + ".address");
            if (address.matches(".*\\.exaroton\\.me(:\\d+)?")) {
                this.bungeeServers.put(name, address.replaceAll(":\\d+$", ""));
            }
        }
    }

    /**
     * update config recursively
     * @param config   current configuration
     * @param defaults defaults
     * @return config with defaults
     */
    private Configuration addDefaults(Configuration config, Configuration defaults) {
        for (String key : defaults.getKeys()) {
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
     * @return exaroton servers
     * @throws APIException API exceptions
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

        Server[] servers = force ? fetchServers() : getServerCache();

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
     * @param query  server name, address or id
     * @return does the server match exactly
     */
    public boolean matchExact(Server server, String query) {
        query = query.toLowerCase(Locale.ROOT);
        return server.getAddress().toLowerCase(Locale.ROOT).equals(query) ||
                server.getName().toLowerCase(Locale.ROOT).equals(query) ||
                server.getId().equals(query);
    }

    /**
     * does this server start with the query
     * @param server exaroton server
     * @param query  partial server name, address or id
     * @return does the server start with the query
     */
    public boolean matchBeginning(Server server, String query) {
        return server.getAddress().startsWith(query) || server.getName().startsWith(query) || server.getId().startsWith(query);
    }

    /**
     * @return get server cache (request if necessary)
     */
    public Server[] getServerCache() throws APIException {
        if (serverCache == null) {
            this.fetchServers();
        }
        return serverCache;
    }

    /**
     * @param servers server list
     * @param status status code
     * @return severs that have the requested status
     */
    public Stream<Server> findWithStatus(Stream<Server> servers, int status) {
        return servers.filter(server -> server.hasStatus(status));
    }

    /**
     * @param servers list of server names
     * @param status status code
     * @return server names that have the requested status
     */
    public Stream<String> findWithStatusByName(Stream<String> servers, int status) {
        return servers.filter(server -> {
            try {
                return this.findServer(server, false).hasStatus(status);
            } catch (APIException e) {
                logger.log(Level.SEVERE, "Failed to request API", e);
                return true;
            }
        });
    }

    public Stream<Server> findWithQuery(Stream<Server> servers, String query) {
        return servers.filter(server -> matchBeginning(server, query));
    }

    public List<String> getAllNames(Server[] servers) {
        List<String> result = new ArrayList<>();

        for (Server server : servers) {
            result.add(server.getName());
        }
        for (Server server : servers) {
            result.add(server.getAddress());
        }
        for (Server server : servers) {
            result.add(server.getId());
        }

        return result;
    }

    public Stream<String> getBungeeServers() {
        return this.bungeeServers.keySet().stream();
    }

    /**
     * find auto completions by a query and status
     * @param query partial server name, address or ID
     * @param status server status
     * @return all matching server names, addresses and IDs
     */
    public Iterable<String> serverCompletions(String query, Integer status) {
        Stream<Server> servers;
        try {
            servers = Arrays.stream(getServerCache());
        } catch (APIException exception) {
            logger.log(Level.SEVERE, "Failed to access API", exception);
            return new ArrayList<>();
        }
        if (status != null)
            servers = findWithStatus(servers, status);
        servers = findWithQuery(servers, query);
        Server[] matching = servers.toArray(Server[]::new);

        Stream<String> bungeeServers = getBungeeServers()
                .filter(s -> s.startsWith(query));

        if (status != null)
            bungeeServers = findWithStatusByName(bungeeServers, status);

        List<String> result = bungeeServers.collect(Collectors.toList());
        result.addAll(getAllNames(matching));

        return result;
    }

    public Iterable<String> serverCompletionsNotInProxy(String query) {
        Stream<Server> servers;
        try {
            servers = Arrays.stream(getServerCache());
        } catch (APIException exception) {
            logger.log(Level.SEVERE, "Failed to access API", exception);
            return new ArrayList<>();
        }
        servers = findWithQuery(servers, query);
        servers = servers.filter(s -> {
            String name = findServerName(s.getAddress(), s.getName());
            return !this.getProxy().getServers().containsKey(name);
        });

        return getAllNames(servers.toArray(Server[]::new));
    }

    /**
     * listen to server status
     * if there already is a status listener then add the sender and/or name
     * @param server         server to subscribe to
     * @param name           server name in bungee server list
     */
    public ServerStatusListener listenToStatus(Server server, String name) {
        return this.listenToStatus(server, null, name, -1, false);
    }

    /**
     * listen to server status
     * if there already is a status listener then add the sender and/or name
     * @param server         server to subscribe to
     * @param sender         command sender to update
     * @param name           server name in bungee server list
     * @param expectedStatus status user is waiting for
     */
    public ServerStatusListener listenToStatus(Server server, CommandSender sender, String name, int expectedStatus) {
        return this.listenToStatus(server, sender, name, expectedStatus, false);
    }

    /**
     * listen to server status
     * if there already is a status listener then add the sender and/or name
     * @param server         server to subscribe to
     * @param sender         command sender to update
     * @param name           server name in bungee server list
     * @param expectedStatus status user is waiting for
     * @param restricted     is server restricted
     */
    public ServerStatusListener listenToStatus(Server server, CommandSender sender, String name, int expectedStatus, boolean restricted) {
        if (statusListeners.containsKey(server.getId())) {
            return statusListeners.get(server.getId())
                    .setSender(sender, expectedStatus)
                    .setName(name);
        }
        server.subscribe();
        ServerStatusListener listener = new ServerStatusListener(this, restricted, server)
                .setSender(sender, expectedStatus)
                .setName(name);
        server.addStatusSubscriber(listener);
        statusListeners.put(server.getId(), listener);
        return listener;
    }

    /**
     * stop listening to server status
     * @param serverId ID of the server to unsubscribe from
     */
    public void stopListeningToStatus(String serverId) {
        if (!this.statusListeners.containsKey(serverId)) {
            return;
        }
        this.statusListeners.get(serverId).unsubscribe();
        this.statusListeners.remove(serverId);
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
     * @param name       server name (lobby)
     * @param address    server address (example.exaroton.me)
     * @param restricted is server restricted
     */
    public void watchServer(String name, String address, boolean restricted) {
        try {
            Server server = this.findServer(address, false);
            if (server == null) {
                logger.warning("Can't find server " + address + ". Unable to watch status changes");
                return;
            }
            logger.info("Found exaroton server: " + address + ". Starting to watch status changes");
            if (server.hasStatus(ServerStatus.ONLINE)) {
                logger.info("Updating server address and port for " + name + "...");
                this.getProxy().getServers().remove(name);
                this.getProxy().getServers().put(name, this.constructServerInfo(name, server, restricted));
            } else {
                this.getProxy().getServers().remove(name);
                logger.info("Server " + name + " is offline, removed it from the server list!");
            }
            this.listenToStatus(server, null, name, -1, restricted);
        } catch (APIException e) {
            logger.log(Level.SEVERE, "Failed to access API, not watching " + name);
        }
    }

    /**
     * watch servers in bungee config
     */
    public void watchServers() {
        if (!config.getBoolean("watch-servers", false)) return;
        Configuration servers = bungeeConfig.getSection("servers");
        for (Map.Entry<String, String> entry: bungeeServers.entrySet()) {
            this.watchServer(entry.getKey(), entry.getValue(), servers.getBoolean(entry.getKey() + ".restricted", false));
        }
    }

    /**
     * generate server info
     * @param name       server name in network
     * @param server     server
     * @param restricted restricted
     * @return bungee server info
     */
    public ServerInfo constructServerInfo(String name, Server server, boolean restricted) {
        return this.getProxy().constructServerInfo(name, new InetSocketAddress(server.getHost(), server.getPort()), server.getMotd(), restricted);
    }

    /**
     * automatically start servers from the config
     */
    public void autoStartServers() {
        if (!config.getBoolean("auto-start.enabled")) return;
        for (String query : config.getStringList("auto-start.servers")) {
            try {
                Server server = this.findServer(query, false);

                if (server == null) {
                    logger.log(Level.WARNING, "Can't start " + query + ": Server not found");
                    continue;
                }

                String name = findServerName(server.getAddress(), server.getName());
                if (server.hasStatus(ServerStatus.ONLINE)) {
                    if (name == null) {
                        logger.log(Level.INFO, server.getAddress() + " is already online, adding it to proxy!");
                        this.getProxy().getServers().put(server.getName(), this.constructServerInfo(server.getName(), server, false));
                    } else {
                        logger.log(Level.INFO, name + " is already online!");
                    }
                    this.listenToStatus(server, null, name, -1);
                    continue;
                }

                if (server.hasStatus(ServerStatus.STARTING, ServerStatus.LOADING, ServerStatus.PREPARING, ServerStatus.RESTARTING)) {
                    logger.log(Level.INFO, name + " is already online or starting!");
                    this.listenToStatus(server, null, name, ServerStatus.ONLINE);
                    continue;
                }

                if (!server.hasStatus(ServerStatus.OFFLINE, ServerStatus.CRASHED)) {
                    logger.log(Level.SEVERE, "Can't start " + name + ": Server isn't offline.");
                    continue;
                }

                logger.log(Level.INFO, "Starting " + name);
                this.listenToStatus(server, null, name, ServerStatus.ONLINE);
                server.start();

            } catch (APIException e) {
                logger.log(Level.SEVERE, "Failed to start " + query + "!", e);
            }
        }
    }

    /**
     * try to find this server in the bungee config
     * @param address exaroton address e.g. example.exaroton.me
     * @return server name e.g. lobby
     */
    public String findServerName(String address) {
        return findServerName(address, null);
    }

    /**
     * try to find this server in the bungee config
     * @param address exaroton address e.g. example.exaroton.me
     * @param fallback fallback name e.g. example
     * @return server name e.g. lobby
     */
    public String findServerName(String address, String fallback) {
        for (Map.Entry<String, String> entry : this.bungeeServers.entrySet()) {
            if (entry.getValue().equals(address)) return entry.getKey();
        }
        return fallback;
    }

    public void updateServer(Server server) {
        if (serverCache == null) return;
        int index = 0;
        for (; index < serverCache.length; index++) {
            if (serverCache[index].getId().equals(server.getId())) break;
        }
        if (index < serverCache.length) {
            serverCache[index] = server;
        }
    }

    /**
     * automatically stop servers from the config
     */
    public void autoStopServers() {
        if (!config.getBoolean("auto-stop.enabled")) return;

        ExecutorService executor = Executors.newCachedThreadPool();
        ArrayList<Callable<Object>> stopping = new ArrayList<>();

        for (String query : config.getStringList("auto-stop.servers")) {
            try {
                Server server = this.findServer(query, false);

                if (server == null) {
                    logger.log(Level.WARNING, "Can't stop " + query + ": Server not found");
                    continue;
                }

                String name = findServerName(server.getAddress(), server.getName());
                if (server.hasStatus(ServerStatus.OFFLINE, ServerStatus.CRASHED)) {
                    logger.log(Level.INFO, name + " is already offline!");
                    continue;
                }

                if (server.hasStatus(ServerStatus.SAVING, ServerStatus.STOPPING)) {
                    logger.log(Level.INFO, name + " is already stopping!");
                    continue;
                }

                if (!server.hasStatus(ServerStatus.ONLINE)) {
                    logger.log(Level.SEVERE, "Can't stop " + name + ": Server isn't online.");
                    continue;
                }

                logger.log(Level.INFO, "Stopping " + name);
                stopping.add(() -> {
                    server.stop();
                    return null;
                });
            } catch (APIException e) {
                logger.log(Level.SEVERE, "Failed to stop " + query + "!", e);
            }
        }
        if (stopping.size() == 0)
            return;

        try {
            executor.invokeAll(stopping);
        } catch (InterruptedException e) {
            logger.log(Level.SEVERE, "Failed to stop servers", e);
            return;
        }

        int count = stopping.size();
        logger.info("Successfully stopped " + count + " server" + (count == 1 ? "" : "s") + "!");
    }
}
