package com.alkacode.time.gui;

import com.alkacode.core.gui.BaseGui;
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

/**
 * Marcos VITALICIOS (nunca reseta), paginados - 100+ entradas nao cabem numa GUI so
 * (ver [[project-alkarankup]]'s RankListMenu, mesmo problema). 5 linhas uteis (slots
 * 10-16/19-25/28-34/37-43 = 28 slots, sobra a borda/linha de baixo pra nav+voltar).
 */
public final class MilestonesMenu extends BaseGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private final PlayerTimeManager timeManager;
    private final RewardManager milestoneManager;
    private final TimeEconomyService economyService;
    private final Messages messages;
    private final Consumer<Player> back;
    private final List<Integer> orderedSeconds;
    private final int page;
    private final int totalPages;

    public MilestonesMenu(JavaPlugin plugin, Player player,
                           PlayerTimeManager timeManager, RewardManager milestoneManager,
                           TimeEconomyService economyService, Messages messages, Consumer<Player> back, int page) {
        super(plugin, player, computeTitle(messages, milestoneManager, page), 6, "alkatime_milestones");
        this.timeManager = timeManager;
        this.milestoneManager = milestoneManager;
        this.economyService = economyService;
        this.messages = messages;
        this.back = back;
        this.orderedSeconds = milestoneManager.getOrderedSeconds();
        this.totalPages = Math.max(1, (int) Math.ceil(orderedSeconds.size() / (double) CONTENT_SLOTS.length));
        this.page = Math.max(0, Math.min(page, totalPages - 1));
    }

    private static String computeTitle(Messages messages, RewardManager milestoneManager, int page) {
        int size = milestoneManager.getOrderedSeconds().size();
        int totalPages = Math.max(1, (int) Math.ceil(size / (double) CONTENT_SLOTS.length));
        int clamped = Math.max(0, Math.min(page, totalPages - 1));
        return messages.raw("menu.marcos-title")
                .replace("<pagina>", String.valueOf(clamped + 1))
                .replace("<total>", String.valueOf(totalPages));
    }

    @Override
    public void render() {
        fillBorder(glass()); // primeiro - senao sobrescreve o botao Voltar/nav (fillBorder e incondicional, ao contrario de fill())

        long total = timeManager.getOnlineSecondsSync(player.getUniqueId());

        int start = page * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, orderedSeconds.size());
        for (int i = start; i < end; i++) {
            int seconds = orderedSeconds.get(i);
            ConfigurationSection milestone = milestoneManager.getReward(seconds);
            if (milestone == null) {
                continue;
            }
            int slot = CONTENT_SLOTS[i - start];
            boolean claimed = milestoneManager.isClaimed(player.getUniqueId(), seconds);
            boolean available = total >= seconds;
            setItem(slot, buildMilestoneItem(milestone, seconds, claimed, available),
                    (!claimed && available) ? e -> attemptClaim(seconds) : null);
        }

        setItem(49, buildBackButton(), e -> back.accept(player));
        if (page > 0) {
            setItem(45, buildNavButton("<yellow>« Página anterior"),
                    e -> new MilestonesMenu(plugin, player, timeManager, milestoneManager,
                            economyService, messages, back, page - 1).open());
        }
        if (page < totalPages - 1) {
            setItem(53, buildNavButton("<yellow>Próxima página »"),
                    e -> new MilestonesMenu(plugin, player, timeManager, milestoneManager,
                            economyService, messages, back, page + 1).open());
        }
    }

    private void attemptClaim(int seconds) {
        RewardManager.ClaimOutcome outcome = milestoneManager.claim(player, seconds);
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

    private ItemStack buildMilestoneItem(ConfigurationSection milestone, int seconds, boolean claimed, boolean available) {
        ItemStack item = ItemBuilder.fromConfig(milestone);
        ItemMeta meta = item.getItemMeta();

        if (claimed) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            appendLore(meta, "<green>Marco já coletado!");
        } else if (available) {
            appendLore(meta, "<yellow>Clique para coletar!");
        } else {
            item.setType(Material.GRAY_DYE);
            meta = item.getItemMeta();
            meta.displayName(milestone.contains("name")
                    ? MM.deserialize(milestone.getString("name")).decoration(TextDecoration.ITALIC, false)
                    : Component.text("Bloqueado"));
            long total = timeManager.getOnlineSecondsSync(player.getUniqueId());
            appendLore(meta, "<red>Faltam " + TimeFormatter.format(seconds - total));
        }

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

    private ItemStack buildNavButton(String text) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(text).decoration(TextDecoration.ITALIC, false));
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
