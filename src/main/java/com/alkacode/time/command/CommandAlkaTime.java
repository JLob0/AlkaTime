package com.alkacode.time.command;

import com.alkacode.time.manager.DailyRewardManager;
import com.alkacode.time.manager.PlayerTimeManager;
import com.alkacode.time.manager.RewardManager;
import com.alkacode.time.npc.TimeNpcManager;
import com.alkacode.time.util.DurationParser;
import com.alkacode.time.util.Messages;
import com.alkacode.time.util.TimeFormatter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class CommandAlkaTime implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final PlayerTimeManager timeManager;
    private final RewardManager milestoneManager;
    private final DailyRewardManager dailyRewardManager;
    private final TimeNpcManager npcManager;
    private final Messages messages;

    public CommandAlkaTime(JavaPlugin plugin, PlayerTimeManager timeManager, RewardManager milestoneManager,
                            DailyRewardManager dailyRewardManager, TimeNpcManager npcManager, Messages messages) {
        this.plugin = plugin;
        this.timeManager = timeManager;
        this.milestoneManager = milestoneManager;
        this.dailyRewardManager = dailyRewardManager;
        this.npcManager = npcManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // permissao "alkatime.admin" ja e checada pelo proprio Bukkit via plugin.yml antes de chegar aqui.
        if (args.length == 0) {
            sender.sendMessage(messages.get("erro.uso-invalido", "<uso>", "/alkatime <setnpc|delnpc|reload|set|add|remove|reset>"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setnpc" -> handleSetNpc(sender);
            case "delnpc" -> handleDelNpc(sender);
            case "reload" -> handleReload(sender);
            case "set" -> handleSet(sender, args);
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "reset" -> handleReset(sender, args);
            default -> sender.sendMessage(messages.get("erro.uso-invalido", "<uso>", "/alkatime <setnpc|delnpc|reload|set|add|remove|reset>"));
        }
        return true;
    }

    private void handleSetNpc(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("erro.apenas-jogador"));
            return;
        }
        if (npcManager.setNpc(player)) {
            sender.sendMessage(messages.get("admin.npc-setado"));
        } else {
            sender.sendMessage(messages.get("admin.npc-indisponivel"));
        }
    }

    private void handleDelNpc(CommandSender sender) {
        if (!npcManager.hasNpc()) {
            sender.sendMessage(messages.get("admin.npc-inexistente"));
            return;
        }
        npcManager.delNpc();
        sender.sendMessage(messages.get("admin.npc-deletado"));
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        messages.load();
        milestoneManager.load();
        dailyRewardManager.load();
        sender.sendMessage(messages.get("admin.reload-sucesso"));
    }

    private void handleSet(CommandSender sender, String[] args) {
        withTargetAndSeconds(sender, args, (target, seconds) ->
                timeManager.setTotalSeconds(target.getUniqueId(), target.getName(), seconds).thenAccept(total ->
                        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(messages.get("admin.set-sucesso",
                                "<jogador>", target.getName(), "<tempo>", TimeFormatter.format(total))))));
    }

    private void handleAdd(CommandSender sender, String[] args) {
        withTargetAndSeconds(sender, args, (target, seconds) ->
                timeManager.addTotalSeconds(target.getUniqueId(), target.getName(), seconds).thenAccept(total ->
                        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(messages.get("admin.add-sucesso",
                                "<jogador>", target.getName(), "<tempo>", TimeFormatter.format(seconds))))));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        withTargetAndSeconds(sender, args, (target, seconds) ->
                timeManager.removeTotalSeconds(target.getUniqueId(), target.getName(), seconds).thenAccept(total ->
                        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(messages.get("admin.remove-sucesso",
                                "<jogador>", target.getName(), "<tempo>", TimeFormatter.format(seconds))))));
    }

    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get("erro.uso-invalido", "<uso>", "/alkatime reset <jogador>"));
            return;
        }
        OfflinePlayer target = resolveTarget(sender, args[1]);
        if (target == null) {
            return;
        }
        timeManager.setTotalSeconds(target.getUniqueId(), target.getName(), 0).thenAccept(total ->
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(messages.get("admin.reset-sucesso",
                        "<jogador>", target.getName()))));
    }

    private interface TargetSecondsAction {
        void accept(OfflinePlayer target, long seconds);
    }

    private void withTargetAndSeconds(CommandSender sender, String[] args, TargetSecondsAction action) {
        if (args.length < 3) {
            sender.sendMessage(messages.get("erro.uso-invalido", "<uso>", "/alkatime " + args[0] + " <jogador> <tempo>"));
            return;
        }
        OfflinePlayer target = resolveTarget(sender, args[1]);
        if (target == null) {
            return;
        }
        long seconds = DurationParser.parseSeconds(args[2]);
        if (seconds < 0) {
            sender.sendMessage(messages.get("erro.numero-invalido", "<valor>", args[2]));
            return;
        }
        action.accept(target, seconds);
    }

    private OfflinePlayer resolveTarget(CommandSender sender, String name) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(name);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(messages.get("erro.jogador-nao-encontrado"));
            return null;
        }
        return target;
    }

    private static final List<String> SUBCOMMANDS = List.of("setnpc", "delnpc", "reload", "set", "add", "remove", "reset");
    private static final List<String> PLAYER_ARG_SUBCOMMANDS = List.of("set", "add", "remove", "reset");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && PLAYER_ARG_SUBCOMMANDS.contains(args[0].toLowerCase(Locale.ROOT))) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
            return filter(names, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
