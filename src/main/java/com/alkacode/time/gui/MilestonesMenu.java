package com.alkacode.time.gui;

import com.alkacode.core.gui.BaseGui;
import com.alkacode.time.AlkaTimePlugin;
import com.alkacode.time.config.MenuConfig;
import com.alkacode.time.gui.layout.GuiLayoutLoader;
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
 * Marcos VITALICIOS (nunca reseta), paginados - 100+ entradas nao cabem numa GUI so
 * (ver [[project-alkarankup]]'s RankListMenu, mesmo problema). Slots de conteudo (char
 * '0' em gui-layouts.yml) e icone/texto estatico vem de menus.yml/gui-layouts.yml (R8,
 * chave "alkatime-milestones"); os marcos em si continuam dinamicos via milestones.yml.
 */
public final class MilestonesMenu extends BaseGui {

    private static final String KEY = "alkatime-milestones";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final PlayerTimeManager timeManager;
    private final RewardManager milestoneManager;
    private final TimeEconomyService economyService;
    private final Messages messages;
    private final Consumer<Player> back;
    private final List<Integer> orderedSeconds;
    private final List<Integer> contentSlots;
    private final int page;
    private final int totalPages;

    public MilestonesMenu(JavaPlugin plugin, Player player,
                           PlayerTimeManager timeManager, RewardManager milestoneManager,
                           TimeEconomyService economyService, Messages messages, Consumer<Player> back, int page) {
        super(plugin, player, computeTitle(plugin, milestoneManager, page), layout(plugin).rows(), KEY);
        this.timeManager = timeManager;
        this.milestoneManager = milestoneManager;
        this.economyService = economyService;
        this.messages = messages;
        this.back = back;
        this.orderedSeconds = milestoneManager.getOrderedSeconds();
        this.contentSlots = layout(plugin).findSlots('0');
        this.totalPages = Math.max(1, (int) Math.ceil(orderedSeconds.size() / (double) contentSlots.size()));
        this.page = Math.max(0, Math.min(page, totalPages - 1));
    }

    private static MenuConfig menu(JavaPlugin plugin) {
        return ((AlkaTimePlugin) plugin).getMenuConfig();
    }

    private static GuiLayoutLoader.GuiLayout layout(JavaPlugin plugin) {
        return ((AlkaTimePlugin) plugin).getGuiLayoutLoader().getLayout(KEY);
    }

    private static String computeTitle(JavaPlugin plugin, RewardManager milestoneManager, int page) {
        int contentSlotCount = layout(plugin).findSlots('0').size();
        int size = milestoneManager.getOrderedSeconds().size();
        int totalPages = Math.max(1, (int) Math.ceil(size / (double) contentSlotCount));
        int clamped = Math.max(0, Math.min(page, totalPages - 1));
        return menu(plugin).title(KEY, Map.of(
                "pagina", String.valueOf(clamped + 1),
                "total", String.valueOf(totalPages)));
    }

    @Override
    public void render() {
        GuiLayoutLoader.GuiLayout layout = layout(plugin);
        MenuConfig menu = menu(plugin);

        fill(menu.item("common.border", null)); // primeiro - senao sobrescreve o botao Voltar/nav

        long total = timeManager.getOnlineSecondsSync(player.getUniqueId());

        int start = page * contentSlots.size();
        int end = Math.min(start + contentSlots.size(), orderedSeconds.size());
        for (int i = start; i < end; i++) {
            int seconds = orderedSeconds.get(i);
            ConfigurationSection milestone = milestoneManager.getReward(seconds);
            if (milestone == null) {
                continue;
            }
            int slot = contentSlots.get(i - start);
            boolean claimed = milestoneManager.isClaimed(player.getUniqueId(), seconds);
            boolean available = total >= seconds;
            setItem(slot, buildMilestoneItem(menu, milestone, seconds, claimed, available),
                    (!claimed && available) ? e -> attemptClaim(seconds) : null);
        }

        setItem(layout.firstSlot('V'), menu.item("common.voltar", null), e -> back.accept(player));
        if (page > 0) {
            setItem(layout.firstSlot('A'), menu.item(KEY + ".pagina-anterior", null),
                    e -> new MilestonesMenu(plugin, player, timeManager, milestoneManager,
                            economyService, messages, back, page - 1).open());
        }
        if (page < totalPages - 1) {
            setItem(layout.firstSlot('N'), menu.item(KEY + ".proxima-pagina", null),
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

    private ItemStack buildMilestoneItem(MenuConfig menu, ConfigurationSection milestone, int seconds, boolean claimed, boolean available) {
        ItemStack item = ItemBuilder.fromConfig(milestone);
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
            meta.displayName(milestone.contains("name")
                    ? MM.deserialize(milestone.getString("name")).decoration(TextDecoration.ITALIC, false)
                    : MM.deserialize(menu.text(KEY + ".bloqueado-nome-padrao", null)).decoration(TextDecoration.ITALIC, false));
            long total = timeManager.getOnlineSecondsSync(player.getUniqueId());
            appendLore(meta, menu.text(KEY + ".status-bloqueado", Map.of("tempo", TimeFormatter.format(seconds - total))));
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
