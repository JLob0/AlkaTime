package com.alkacode.time.command;

import com.alkacode.core.scheduler.AlkaScheduler;
import com.alkacode.time.database.TimeRepository;
import com.alkacode.time.gui.TimeMenu;
import com.alkacode.time.gui.TopMenu;
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
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CommandTempo implements CommandExecutor {

    private final JavaPlugin plugin;
    private final PlayerTimeManager timeManager;
    private final RewardManager rewardManager;
    private final TimeRepository repository;
    private final AlkaScheduler scheduler;
    private final TimeEconomyService economyService;
    private final Messages messages;

    public CommandTempo(JavaPlugin plugin, PlayerTimeManager timeManager, RewardManager rewardManager,
                         TimeRepository repository, AlkaScheduler scheduler,
                         TimeEconomyService economyService, Messages messages) {
        this.plugin = plugin;
        this.timeManager = timeManager;
        this.rewardManager = rewardManager;
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
        String title = plugin.getConfig().getString("menu.tempo-title", "<green>Seu Tempo Online");
        int rows = plugin.getConfig().getInt("menu.tempo-rows", 3);
        new TimeMenu(plugin, player, title, rows, timeManager, rewardManager, economyService, messages, this::openTopMenu).open();
    }

    public void openTopMenu(Player player) {
        int limit = plugin.getConfig().getInt("menu.top-limit", 50);
        scheduler.runAsync(() -> {
            var entries = repository.getTopEntries(limit);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                String title = plugin.getConfig().getString("menu.top-title", "<gold>TOP Tempo Online");
                int rows = plugin.getConfig().getInt("menu.top-rows", 6);
                new TopMenu(plugin, player, title, rows, entries, messages, this::openTimeMenu).open();
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
}
