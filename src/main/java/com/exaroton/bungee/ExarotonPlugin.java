package com.exaroton.bungee;

import com.exaroton.api.APIException;
import com.exaroton.api.ExarotonClient;
import com.exaroton.api.server.Server;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
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
        this.config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(this.getConfigFile());
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
     * get exaroton client
     * @return exaroton client
     */
    public ExarotonClient getExarotonClient() {
        return exarotonClient;
    }

    /**
     * get config
     * @return configuration
     */
    public Configuration getConfig() {
        return config;
    }

    /**
     * get server cache
     * @return cached servers
     */
    public Server[] getServerCache() {
        return serverCache;
    }

    /**
     * update server cache to provided servers
     * @param servers new servers
     */
    public void updateServerCache(Server[] servers) {
        if (servers != null) {
            this.serverCache = servers;
        }
    }

    /**
     * find a server
     * if a server can't be uniquely identified then the id will be preferred
     * @param query server name, address or id
     * @return found server or null
     * @throws APIException exceptions from the API
     */
    public Server findServer(String query) throws APIException {
        Server[] servers;
        servers = exarotonClient.getServers();
        this.updateServerCache(servers);

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

    public Iterable<String> matchingServers(String query) {
        if (serverCache == null) {
            try {
                this.serverCache = exarotonClient.getServers();
            } catch (APIException e) {
                logger.log(Level.SEVERE, "Failed to load completions", e);
                return new ArrayList<>();
            }
        }
        Server[] matching = Arrays.stream(serverCache).filter(server -> matchBeginning(server, query)).toArray(Server[]::new);
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
}
