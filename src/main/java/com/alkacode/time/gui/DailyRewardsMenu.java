package com.alkacode.time.gui;

import com.alkacode.core.gui.BaseGui;
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
import java.util.function.Consumer;

/** Recompensas DIARIAS (reseta a meia-noite) - mesmo layout que o antigo TimeMenu, so que agora e um submenu com Voltar pro hub (ver [[feedback-menu-back-button]]). */
public final class DailyRewardsMenu extends BaseGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PlayerTimeManager timeManager;
    private final DailyRewardManager rewardManager;
    private final TimeEconomyService economyService;
    private final Messages messages;
    private final Consumer<Player> back;

    public DailyRewardsMenu(JavaPlugin plugin, Player player, String title, int rows,
                             PlayerTimeManager timeManager, DailyRewardManager rewardManager,
                             TimeEconomyService economyService, Messages messages, Consumer<Player> back) {
        super(plugin, player, title, rows, "alkatime_daily");
        this.timeManager = timeManager;
        this.rewardManager = rewardManager;
        this.economyService = economyService;
        this.messages = messages;
        this.back = back;
    }

    @Override
    public void render() {
        long today = timeManager.getTodaySecondsSync(player.getUniqueId());
        String formatted = TimeFormatter.format(today);

        setItem(4, buildInfoItem(formatted));

        int lastRow = (inventory.getSize() / 9) - 1;
        setItem(lastRow * 9 + 4, buildBackButton(), e -> back.accept(player)); // centralizado, nao no canto

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
            setItem(slot, buildRewardItem(reward, seconds, claimed, available),
                    (!claimed && available) ? e -> attemptClaim(seconds) : null);
        }

        fill(glass());
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

    private ItemStack buildInfoItem(String formatted) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(messages.raw("menu.tempo-hoje-nome").replace("<tempo>", formatted))
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String line : messages.rawList("menu.tempo-hoje-lore")) {
            lore.add(MM.deserialize(line.replace("<tempo>", formatted)).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildBackButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(messages.raw("menu.voltar")).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildRewardItem(ConfigurationSection reward, int seconds, boolean claimed, boolean available) {
        ItemStack item = ItemBuilder.fromConfig(reward);
        ItemMeta meta = item.getItemMeta();

        if (claimed) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            appendLore(meta, "<green>Recompensa ja coletada hoje!");
        } else if (available) {
            appendLore(meta, "<yellow>Clique para coletar!");
        } else {
            item.setType(Material.GRAY_DYE);
            meta = item.getItemMeta();
            meta.displayName(reward.contains("name")
                    ? MM.deserialize(reward.getString("name")).decoration(TextDecoration.ITALIC, false)
                    : Component.text("Bloqueado"));
            appendLore(meta, "<red>Faltam " + TimeFormatter.format(seconds - timeManager.getTodaySecondsSync(player.getUniqueId())));
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

    private ItemStack glass() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.empty());
        glass.setItemMeta(meta);
        return glass;
    }
}
