package com.alkacode.time.gui;

import com.alkacode.core.gui.BaseGui;
import com.alkacode.time.AlkaTimePlugin;
import com.alkacode.time.config.MenuConfig;
import com.alkacode.time.gui.layout.GuiLayoutLoader;
import com.alkacode.time.manager.DailyRewardManager;
import com.alkacode.time.manager.PlayerTimeManager;
import com.alkacode.time.manager.RewardManager;
import com.alkacode.time.manager.TimeEconomyService;
import com.alkacode.time.util.Messages;
import com.alkacode.time.util.TimeFormatter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Hub principal do AlkaTime - so navegacao (Diarias / Marcos / TOP), sem
 * recompensa nenhuma direto aqui (isso vive em DailyRewardsMenu/MilestonesMenu,
 * ver [[feedback-menu-back-button]] pra padrao de submenu com Voltar).
 * Icone/texto/layout vem de menus.yml/gui-layouts.yml (R8, chave "alkatime-hub").
 */
public final class TimeMenu extends BaseGui {

    private static final String KEY = "alkatime-hub";

    private final PlayerTimeManager timeManager;
    private final DailyRewardManager dailyRewardManager;
    private final RewardManager milestoneManager;
    private final TimeEconomyService economyService;
    private final Messages messages;
    private final Consumer<Player> openTop;

    public TimeMenu(JavaPlugin plugin, Player player,
                     PlayerTimeManager timeManager, DailyRewardManager dailyRewardManager, RewardManager milestoneManager,
                     TimeEconomyService economyService, Messages messages, Consumer<Player> openTop) {
        super(plugin, player, menu(plugin).title(KEY, null), layout(plugin).rows(), KEY);
        this.timeManager = timeManager;
        this.dailyRewardManager = dailyRewardManager;
        this.milestoneManager = milestoneManager;
        this.economyService = economyService;
        this.messages = messages;
        this.openTop = openTop;
    }

    private static MenuConfig menu(JavaPlugin plugin) {
        return ((AlkaTimePlugin) plugin).getMenuConfig();
    }

    private static GuiLayoutLoader.GuiLayout layout(JavaPlugin plugin) {
        return ((AlkaTimePlugin) plugin).getGuiLayoutLoader().getLayout(KEY);
    }

    @Override
    public void render() {
        GuiLayoutLoader.GuiLayout layout = layout(plugin);
        MenuConfig menu = menu(plugin);

        long total = timeManager.getOnlineSecondsSync(player.getUniqueId());
        setItem(layout.firstSlot('I'), menu.item(KEY + ".tempo-jogado", Map.of("tempo", TimeFormatter.format(total))));

        setItem(layout.firstSlot('D'), menu.item(KEY + ".botao-diarias", null), e -> openDaily());
        setItem(layout.firstSlot('M'), menu.item(KEY + ".botao-marcos", null), e -> openMilestones());
        setItem(layout.firstSlot('T'), menu.item(KEY + ".ver-top", null), e -> openTop.accept(player));

        fill(menu.item("common.border", null));
    }

    private void openDaily() {
        new DailyRewardsMenu(plugin, player, timeManager, dailyRewardManager, economyService, messages, this::reopen).open();
    }

    private void openMilestones() {
        new MilestonesMenu(plugin, player, timeManager, milestoneManager, economyService, messages, this::reopen, 0).open();
    }

    private void reopen(Player p) {
        new TimeMenu(plugin, p, timeManager, dailyRewardManager, milestoneManager,
                economyService, messages, openTop).open();
    }
}
