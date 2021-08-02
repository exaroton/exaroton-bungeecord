package com.exaroton.bungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.Set;

public class Message {

    public static final String prefix = ChatColor.GRAY + "["+ ChatColor.GREEN +"exaroton"+ ChatColor.GRAY + "] ";

    public static final TextComponent SERVER_NOT_FOUND = Message.error("Server wasn't found.").toComponent();

    public static final TextComponent SERVER_NOT_ONLINE = Message.error("Server isn't online.").toComponent();

    public static final TextComponent SERVER_NOT_OFFLINE = Message.error("Server isn't offline.").toComponent();

    public static final TextComponent API_ERROR = Message.error("An API Error occurred. Check your log for details!").toComponent();

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
        return new Message(action + " server " + ChatColor.GREEN + name + ChatColor.GRAY + "...");
    }

    /**
     * @param name server name
     * @param online is server online
     */
    public static Message statusChange(String name, boolean online) {
        return new Message("Server" + ChatColor.GREEN + name + ChatColor.GRAY + " went " +
                (online ? ChatColor.GREEN + "online" : ChatColor.RED + "offline") + ChatColor.GRAY + ".");
    }

    /**
     * list sub-commands
     * @param subcommands sub-command names
     */
    public static Message subCommandList(Set<String> subcommands) {
        StringBuilder text = new StringBuilder(ChatColor.GRAY + "Available sub-commands:\n");
        for (String subcommand: subcommands) {
            text.append(ChatColor.GRAY)
                .append("- ")
                .append(ChatColor.GREEN)
                .append(subcommand)
                .append("\n");
        }
        return new Message(text.subSequence(0, text.lastIndexOf("\n")).toString());
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