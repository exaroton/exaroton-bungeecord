package com.exaroton.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.logging.Logger;

public abstract class SubCommand implements TabExecutor {

    /**
     * sub-command name
     */
    private final String name;

    /**
     * plugin
     */
    protected final ExarotonPlugin plugin;

    /**
     * logger
     */
    protected final Logger logger;

    /**
     * @param plugin exaroton plugin
     */
    public SubCommand(String name, ExarotonPlugin plugin){
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Invalid sub-command name!");
        }
        this.name = name;
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
    };

    /**
     * execute command
     * @param sender command sender
     * @param args command arguments
     */
    public abstract void execute(CommandSender sender, String[] args);
}
