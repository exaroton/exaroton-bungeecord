package com.exaroton.bungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class Message {

    public static final String prefix = ChatColor.GRAY + "["+ ChatColor.GREEN +"exaroton"+ ChatColor.GRAY + "] ";

    public static final TextComponent SERVER_NOT_FOUND = Message.error("Server not found.").toComponent();

    public static final TextComponent SERVER_NOT_ONLINE = Message.error("Server is not online.").toComponent();

    public static final TextComponent SERVER_NOT_OFFLINE = Message.error("Server is not offline.").toComponent();

    public static final TextComponent API_ERROR = Message.error("An API Error occurred. Check your log for details!").toComponent();

    public static final TextComponent NOT_PLAYER = Message.error("This command can only be executed by players!").toComponent();

    private final String message;

    private Message(String message) {
        this.message = prefix + message + ChatColor.RESET;
    }

    /**
     * show command usage
     * @param command command name
     */
    public static Message usage(String command) {
        return new Message("Usage: " + "/exaroton " + command + ChatColor.GREEN + " <server> ");
    }

    /**
     * @param message error message
     */
    public static Message error(String message) {
        return new Message(ChatColor.RED + message);
    }

    /**
     * show that an action is being executed
     * @param action action name (e.g. "Starting")
     * @param name server name
     */
    public static Message action(String action, String name) {
        return new Message(action + " server " + ChatColor.GREEN + name + ChatColor.GRAY + ".");
    }

    /**
     * show that a server has been added to the proxy
     * @param name server name
     */
    public static Message added(String name) {
        return new Message("Added server " + ChatColor.GREEN + name + ChatColor.GRAY + " to the proxy.");
    }

    /**
     * show that a server has been removed from the proxy
     * @param name server name
     */
    public static Message removed(String name) {
        return new Message("Removed server " + ChatColor.GREEN + name + ChatColor.GRAY + " from the proxy. No longer watching status updates.");
    }

    /**
     * show that a server's status is being watched
     * @param name server name
     */
    public static Message watching(String name) {
        return new Message("Watching status updates for " + ChatColor.GREEN + name + ChatColor.GRAY + ".");
    }

    /**
     * @param name server name
     * @param online is server online
     */
    public static Message statusChange(String name, boolean online) {
        return new Message("Server " + ChatColor.GREEN + name + ChatColor.GRAY + " went " +
                (online ? ChatColor.GREEN + "online" : ChatColor.RED + "offline") + ChatColor.GRAY + ".");
    }

    /**
     * list sub-commands
     * @param subcommands sub-commands
     */
    public static Message subCommandList(Collection<SubCommand> subcommands) {
        StringBuilder text = new StringBuilder(ChatColor.GRAY + "Available sub-commands:\n");
        for (SubCommand subcommand: subcommands) {
            text.append(ChatColor.GRAY)
                .append("- ")
                .append(ChatColor.GREEN)
                .append(subcommand.name)
                .append(": ")
                .append(ChatColor.GRAY)
                .append(subcommand.description)
                .append("\n");
        }
        return new Message(text.subSequence(0, text.lastIndexOf("\n")).toString());
    }

    /**
     * show that this player is being moved to a server
     * @param serverName server name in network
     * @return message
     */
    public static Message switching(String serverName) {
        return new Message("Switching to " + ChatColor.GREEN + serverName + ChatColor.GRAY + "...");
    }

    /**
     * convert to text component
     * @return bungee text component
     */
    public TextComponent toComponent() {
        return new TextComponent(this.message);
    }

    /**
     * @return message string with color codes
     */
    public String getMessage() {
        return this.message;
    }
}
