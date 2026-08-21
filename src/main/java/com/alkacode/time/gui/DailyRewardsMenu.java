package com.alkacode.time.gui;

import com.alkacode.core.gui.BaseGui;
import com.alkacode.time.AlkaTimePlugin;
import com.alkacode.time.config.MenuConfig;
import com.alkacode.time.gui.layout.GuiLayoutLoader;
import com.alkacode.time.manager.DailyRewardManager;
import com.alkacode.time.manager.PlayerTimeManager;
import com.alkacode.time.manager.RewardManager;
import com.alkacode.time.manager.TimeEconomyService;
import com.alkacode.time.util.ItemBuilder;
import com.alkacode.time.util.Messages;
import com.alkacode.time.util.TimeFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Recompensas DIARIAS (reseta a meia-noite) - submenu com Voltar pro hub
 * (ver [[feedback-menu-back-button]]). Icone/texto/layout estatico vem de
 * menus.yml/gui-layouts.yml (R8, chave "alkatime-daily"); os slots das
 * recompensas em si continuam vindo de rewards.yml (cada uma define o
 * proprio slot/icone, ja e 100% YML por design).
 */
public final class DailyRewardsMenu extends BaseGui {

    private static final String KEY = "alkatime-daily";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PlayerTimeManager timeManager;
    private final DailyRewardManager rewardManager;
    private final TimeEconomyService economyService;
    private final Messages messages;
    private final Consumer<Player> back;

    public DailyRewardsMenu(JavaPlugin plugin, Player player,
                             PlayerTimeManager timeManager, DailyRewardManager rewardManager,
                             TimeEconomyService economyService, Messages messages, Consumer<Player> back) {
        super(plugin, player, menu(plugin).title(KEY, null), layout(plugin).rows(), KEY);
        this.timeManager = timeManager;
        this.rewardManager = rewardManager;
        this.economyService = economyService;
        this.messages = messages;
        this.back = back;
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

        long today = timeManager.getTodaySecondsSync(player.getUniqueId());
        String formatted = TimeFormatter.format(today);

        setItem(layout.firstSlot('I'), menu.item(KEY + ".tempo-hoje", Map.of("tempo", formatted)));
        setItem(layout.firstSlot('V'), menu.item("common.voltar", null), e -> back.accept(player));

        for (int seconds : rewardManager.getOrderedSeconds()) {
            ConfigurationSection reward = rewardManager.getReward(seconds);
            if (reward == null) {
                continue;
            }
            int slot = reward.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }
            boolean claimed = rewardManager.isClaimed(player.getUniqueId(), seconds);
            boolean available = today >= seconds;
            setItem(slot, buildRewardItem(menu, reward, seconds, claimed, available),
                    (!claimed && available) ? e -> attemptClaim(seconds) : null);
        }

        fill(menu.item("common.border", null));
    }

    private void attemptClaim(int seconds) {
        RewardManager.ClaimOutcome outcome = rewardManager.claim(player, seconds);
        switch (outcome.result()) {
            case SUCCESS -> {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                player.sendMessage(messages.get("recompensa.coletada",
                        "<amount>", String.valueOf((long) outcome.amount()),
                        "<currency>", economyService.getCurrencyDisplayName(outcome.currencyId()),
                        "<tempo>", TimeFormatter.format(seconds)));
                refresh();
            }
            case ALREADY_CLAIMED -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
                player.sendMessage(messages.get("recompensa.ja-coletada"));
            }
            case NOT_ENOUGH_TIME -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 1);
                player.sendMessage(messages.get("recompensa.invalida"));
            }
            case INVALID_REWARD -> refresh();
        }
    }

    private ItemStack buildRewardItem(MenuConfig menu, ConfigurationSection reward, int seconds, boolean claimed, boolean available) {
        ItemStack item = ItemBuilder.fromConfig(reward);
        ItemMeta meta = item.getItemMeta();

        if (claimed) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            appendLore(meta, menu.text(KEY + ".status-coletado", null));
        } else if (available) {
            appendLore(meta, menu.text(KEY + ".status-disponivel", null));
        } else {
            item.setType(Material.GRAY_DYE);
            meta = item.getItemMeta();
            meta.displayName(reward.contains("name")
                    ? MM.deserialize(reward.getString("name")).decoration(TextDecoration.ITALIC, false)
                    : MM.deserialize(menu.text(KEY + ".bloqueado-nome-padrao", null)).decoration(TextDecoration.ITALIC, false));
            long faltam = seconds - timeManager.getTodaySecondsSync(player.getUniqueId());
            appendLore(meta, menu.text(KEY + ".status-bloqueado", Map.of("tempo", TimeFormatter.format(faltam))));
        }

        item.setItemMeta(meta);
        return item;
    }

    private void appendLore(ItemMeta meta, String extraLine) {
        List<Component> lore = new ArrayList<>(meta.hasLore() && meta.lore() != null ? meta.lore() : List.of());
        lore.add(Component.empty());
        lore.add(MM.deserialize(extraLine).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
    }
}
