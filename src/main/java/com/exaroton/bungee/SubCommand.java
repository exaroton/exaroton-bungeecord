package com.exaroton.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.logging.Logger;

public abstract class SubCommand implements TabExecutor {

    /**
     * sub-command name
     */
    public final String name;

    /**
     * command description
     */
    public final String description;

    /**
     * plugin
     */
    protected final ExarotonPlugin plugin;

    /**
     * logger
     */
    protected final Logger logger;

    /**
     * @param name sub-command name
     * @param description sub-command description
     * @param plugin exaroton plugin
     */
    public SubCommand(String name, String description,  ExarotonPlugin plugin){
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Invalid sub-command name!");
        }
        this.name = name;

        if (description == null || description.length() == 0) {
            throw new IllegalArgumentException("Invalid sub-command description!");
        }
        this.description = description;

        if (plugin == null) {
            throw new IllegalArgumentException("Invalid plugin!");
        }
        this.plugin = plugin;
        logger = plugin.getLogger();
    }

    /**
     * get the sub-command name (e.g. "start")
     * @return sub-command name
     */
    public String getName() {
        return name;
    }

    /**
     * execute command
     * @param sender command sender
     * @param args command arguments
     */
    public abstract void execute(CommandSender sender, String[] args);

    /**
     * get the required permission node
     * @return permission node or null
     */
    public String getPermission() {
        return null;
    }
}
