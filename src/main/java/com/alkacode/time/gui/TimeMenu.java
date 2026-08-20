package com.alkacode.time.gui;

import com.alkacode.core.gui.BaseGui;
import com.alkacode.time.manager.DailyRewardManager;
import com.alkacode.time.manager.PlayerTimeManager;
import com.alkacode.time.manager.RewardManager;
import com.alkacode.time.manager.TimeEconomyService;
import com.alkacode.time.util.Messages;
import com.alkacode.time.util.TimeFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Hub principal do AlkaTime - so navegacao (Diarias / Marcos / TOP), sem
 * recompensa nenhuma direto aqui (isso vive em DailyRewardsMenu/MilestonesMenu,
 * ver [[feedback-menu-back-button]] pra padrao de submenu com Voltar).
 */
public final class TimeMenu extends BaseGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PlayerTimeManager timeManager;
    private final DailyRewardManager dailyRewardManager;
    private final RewardManager milestoneManager;
    private final TimeEconomyService economyService;
    private final Messages messages;
    private final Consumer<Player> openTop;

    public TimeMenu(JavaPlugin plugin, Player player, String title, int rows,
                     PlayerTimeManager timeManager, DailyRewardManager dailyRewardManager, RewardManager milestoneManager,
                     TimeEconomyService economyService, Messages messages, Consumer<Player> openTop) {
        super(plugin, player, title, rows, "alkatime_hub");
        this.timeManager = timeManager;
        this.dailyRewardManager = dailyRewardManager;
        this.milestoneManager = milestoneManager;
        this.economyService = economyService;
        this.messages = messages;
        this.openTop = openTop;
    }

    @Override
    public void render() {
        long total = timeManager.getOnlineSecondsSync(player.getUniqueId());
        setItem(4, buildInfoItem(TimeFormatter.format(total)));

        setItem(11, buildDailyButton(), e -> openDaily());
        setItem(13, buildMilestonesButton(), e -> openMilestones());
        setItem(15, buildTopButton(), e -> openTop.accept(player));

        fill(glass());
    }

    private void openDaily() {
        String title = plugin.getConfig().getString("menu.diarias-title", "<green>Recompensas Diárias");
        int rows = plugin.getConfig().getInt("menu.diarias-rows", 3);
        new DailyRewardsMenu(plugin, player, title, rows, timeManager, dailyRewardManager, economyService, messages, this::reopen).open();
    }

    private void openMilestones() {
        new MilestonesMenu(plugin, player, timeManager, milestoneManager, economyService, messages, this::reopen, 0).open();
    }

    private void reopen(Player p) {
        new TimeMenu(plugin, p, title(), inventory.getSize() / 9, timeManager, dailyRewardManager, milestoneManager,
                economyService, messages, openTop).open();
    }

    private String title() {
        return plugin.getConfig().getString("menu.tempo-title", "<green>Seu Tempo Online");
    }

    private ItemStack buildInfoItem(String formatted) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(messages.raw("menu.tempo-jogado-nome").replace("<tempo>", formatted))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : messages.rawList("menu.tempo-jogado-lore")) {
            lore.add(MM.deserialize(line.replace("<tempo>", formatted)).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildDailyButton() {
        ItemStack item = new ItemStack(Material.SUNFLOWER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(messages.raw("menu.botao-diarias")).decoration(TextDecoration.ITALIC, false));
        meta.lore(messages.getList("menu.botao-diarias-lore").stream()
                .map(c -> (Component) c.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildMilestonesButton() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(messages.raw("menu.botao-marcos")).decoration(TextDecoration.ITALIC, false));
        meta.lore(messages.getList("menu.botao-marcos-lore").stream()
                .map(c -> (Component) c.decoration(TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildTopButton() {
        ItemStack item = new ItemStack(Material.GOLDEN_HELMET);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(messages.raw("menu.ver-top")).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack glass() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.empty());
        glass.setItemMeta(meta);
        return glass;
    }
}
