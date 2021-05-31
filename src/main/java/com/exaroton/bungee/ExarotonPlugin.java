package com.exaroton.bungee;

import com.exaroton.api.APIException;
import com.exaroton.api.ExarotonClient;
import com.exaroton.api.server.Server;
import com.exaroton.api.server.ServerStatus;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

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
     * logger
     */
    private final Logger logger = this.getLogger();

    /**
     * server cache
     */
    private Server[] serverCache;

    @Override
    public void onEnable() {
        try {
            this.loadConfig();
        }
        catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to load config file!", e);
            return;
        }
        if (this.createExarotonClient()) {
            this.registerCommands();
            this.startWatchingServers();
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
        for (String key: defaultConfig.getKeys()) {
            if (!config.getKeys().contains(key)) {
                config.set(key, defaultConfig.get(key));
            }
            provider.save(config, configFile);
        }
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
     * @return found server or null
     * @throws APIException exceptions from the API
     */
    public Server findServer(String query) throws APIException {
        Server[] servers = fetchServers();

        servers = Arrays.stream(servers)
                .filter(server -> matchExact(server, query))
                .toArray(Server[]::new);

        switch (servers.length) {
            case 0:
                return null;

            case 1:
                return servers[0];

            default:
                Optional<Server> server = Arrays.stream(servers).filter(s -> s.getId().equals(query)).findFirst();
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
        ArrayList<String> result = new ArrayList<>();

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
     * start watching servers in the bungee config
     */
    public void startWatchingServers() {
        if (config.getBoolean("watch-servers")) {
            this.getProxy().getScheduler().runAsync(this, () -> {
                this.watchServers();
            });
        }
    }

    /**
     * watch servers in the bungee config
     */
    public void watchServers(){
        try {
            Configuration bungeeConfig = ConfigurationProvider.getProvider(YamlConfiguration.class)
                    .load(new File(getProxy().getPluginsFolder().getParent(), "config.yml"));
            Configuration servers = bungeeConfig.getSection("servers");
            for (String serverName: servers.getKeys()) {
                String address = servers.getString(serverName+".address");
                address = address.replaceAll(":\\d+$", "");
                if (address.endsWith(".exaroton.me")) {
                    logger.info("Found exaroton server: " + address + ", start watching status changes");
                    try {
                        Server server = this.findServer(address);
                        if (!server.hasStatus(ServerStatus.ONLINE)) {
                            this.getProxy().getServers().remove(serverName);
                            logger.info("Server " + address + " is offline, removed it from the server list!");
                        }
                        server.subscribe();
                        server.addStatusSubscriber(new ServerStatusListener(this.getProxy(), serverName));
                    } catch (APIException e) {
                        logger.log(Level.SEVERE, "Failed to access API, not watching "+address);
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load bungee config, cant watch servers!", e);
        }
    }
}
