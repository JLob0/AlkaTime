package com.alkacode.time.command;

import com.alkacode.core.scheduler.AlkaScheduler;
import com.alkacode.time.database.TimeRepository;
import com.alkacode.time.gui.TimeMenu;
import com.alkacode.time.gui.TopMenu;
import com.alkacode.time.manager.DailyRewardManager;
import com.alkacode.time.manager.PlayerTimeManager;
import com.alkacode.time.manager.RewardManager;
import com.alkacode.time.manager.TimeEconomyService;
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

public final class CommandTempo implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final PlayerTimeManager timeManager;
    private final DailyRewardManager dailyRewardManager;
    private final RewardManager milestoneManager;
    private final TimeRepository repository;
    private final AlkaScheduler scheduler;
    private final TimeEconomyService economyService;
    private final Messages messages;

    public CommandTempo(JavaPlugin plugin, PlayerTimeManager timeManager, DailyRewardManager dailyRewardManager,
                         RewardManager milestoneManager, TimeRepository repository, AlkaScheduler scheduler,
                         TimeEconomyService economyService, Messages messages) {
        this.plugin = plugin;
        this.timeManager = timeManager;
        this.dailyRewardManager = dailyRewardManager;
        this.milestoneManager = milestoneManager;
        this.repository = repository;
        this.scheduler = scheduler;
        this.economyService = economyService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Apenas jogadores podem usar esse comando.");
            return true;
        }

        if (args.length == 0) {
            openTimeMenu(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("top")) {
            if (!player.hasPermission("alkatime.top")) {
                player.sendMessage(messages.get("erro.sem-permissao"));
                return true;
            }
            openTopMenu(player);
            return true;
        }

        if (!player.hasPermission("alkatime.look.others")) {
            player.sendMessage(messages.get("erro.sem-permissao"));
            return true;
        }
        lookupOther(player, args[0]);
        return true;
    }

    public void openTimeMenu(Player player) {
        new TimeMenu(plugin, player, timeManager, dailyRewardManager, milestoneManager,
                economyService, messages, this::openTopMenu).open();
    }

    public void openTopMenu(Player player) {
        int limit = plugin.getConfig().getInt("menu.top-limit", 50);
        scheduler.runAsync(() -> {
            var entries = repository.getTopEntries(limit);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                new TopMenu(plugin, player, entries, messages, this::openTimeMenu).open();
            });
        });
    }

    private void lookupOther(Player sender, String targetName) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(messages.get("erro.jogador-nunca-entrou"));
            return;
        }
        if (!target.isOnline() && !sender.hasPermission("alkatime.veroff")) {
            sender.sendMessage(messages.get("erro.sem-permissao"));
            return;
        }
        timeManager.getOnlineSecondsAsync(target.getUniqueId()).thenAccept(seconds ->
                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(messages.get(
                        "consulta.tempo-de-outro",
                        "<jogador>", target.getName() != null ? target.getName() : targetName,
                        "<tempo>", TimeFormatter.format(seconds)))));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return List.of();
        }
        List<String> options = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        if (player.hasPermission("alkatime.top")) {
            options.add("top");
        }
        String lower = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(lower)).collect(Collectors.toList());
    }
}
