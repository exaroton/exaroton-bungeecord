package com.exaroton.bungee;

import com.exaroton.bungee.subcommands.*;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import net.md_5.bungee.api.scheduler.TaskScheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

public class ExarotonCommand extends Command implements TabExecutor {

    /**
     * exaroton plugin
     */
    private final ExarotonPlugin plugin;

    /**
     * task scheduler
     */
    private final TaskScheduler taskScheduler;

    /**
     * registered sub-commands
     * name -> command
     */
    private final HashMap<String, SubCommand> subCommands = new HashMap<>();

    public ExarotonCommand(ExarotonPlugin plugin) {
        super("exaroton");
        if (plugin == null) {
            throw new IllegalArgumentException("Invalid plugin");
        }
        this.plugin = plugin;
        this.loadCommands();
        this.taskScheduler = plugin.getProxy().getScheduler();
    }

    /**
     * register all sub-commands
     */
    private void loadCommands() {
        this.registerCommand(new StartServer(plugin));
        this.registerCommand(new StopServer(plugin));
        this.registerCommand(new RestartServer(plugin));
        this.registerCommand(new AddServer(plugin));
        this.registerCommand(new RemoveServer(plugin));
        this.registerCommand(new SwitchServer(plugin));
    }

    /**
     * register a sub command
     * @param subCommand sub command
     */
    private void registerCommand(SubCommand subCommand) {
        this.subCommands.put(subCommand.getName(), subCommand);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(this.getUsage());
            return;
        }

        SubCommand command = this.subCommands.get(args[0]);
        if (command == null) {
            sender.sendMessage(Message.error("Unknown sub-command.").toComponent());
            return;
        }

        if (command.getPermission() != null && !sender.hasPermission(command.getPermission())) {
            sender.sendMessage(Message.error("You don't have the required permissions to execute this command.").toComponent());
            return;
        }

        taskScheduler.runAsync(plugin, () -> command.execute(sender, Arrays.copyOfRange(args, 1, args.length)));
    }

    /**
     * get list of available subcommands
     * @return command usage
     */
    private TextComponent getUsage() {
        return (subCommands.size() == 0 ? Message.error("No sub-commands registered.") :
                Message.subCommandList(subCommands.values())).toComponent();
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return subCommands
                    .keySet()
                    .stream()
                    .filter(name -> name.startsWith(args[0]))
                    .collect(Collectors.toList());
        }
        else {
            SubCommand command = subCommands
                    .get(args[0]);
            if (command == null || !sender.hasPermission(command.getPermission())) return new ArrayList<>();
            return command
                    .onTabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        }
    }
}
