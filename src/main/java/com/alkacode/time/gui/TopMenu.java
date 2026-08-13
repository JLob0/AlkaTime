package com.alkacode.time.gui;

import com.alkacode.core.gui.BaseGui;
import com.alkacode.time.database.TimeRepository;
import com.alkacode.time.util.Messages;
import com.alkacode.time.util.TimeFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.function.Consumer;

/**
 * Ranking de tempo online (BaseGui do AlkaCore, R3) - a lista de {@link TimeRepository.TopEntry}
 * ja vem resolvida do banco (leitura assincrona feita pelo chamador, ver CommandTempo);
 * este menu so renderiza, nunca consulta o banco.
 */
public final class TopMenu extends BaseGui {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final List<TimeRepository.TopEntry> entries;
    private final Messages messages;
    private final Consumer<Player> openBack;

    public TopMenu(JavaPlugin plugin, Player player, String title, int rows,
                    List<TimeRepository.TopEntry> entries, Messages messages, Consumer<Player> openBack) {
        super(plugin, player, title, rows, "alkatime_top");
        this.entries = entries;
        this.messages = messages;
        this.openBack = openBack;
    }

    @Override
    public void render() {
        int lastRow = (inventory.getSize() / 9) - 1;
        int backSlot = lastRow * 9 + 4;
        setItem(backSlot, buildBackButton(), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            openBack.accept(player);
        });

        if (entries.isEmpty()) {
            setItem(inventory.getSize() / 2, buildEmptyItem());
            fill(glass());
            return;
        }

        int maxSlots = lastRow * 9;
        for (int i = 0; i < entries.size() && i < maxSlots; i++) {
            TimeRepository.TopEntry entry = entries.get(i);
            setItem(i, buildEntryItem(entry, i + 1));
        }

        fill(glass());
    }

    private ItemStack buildEntryItem(TimeRepository.TopEntry entry, int position) {
        String name = messages.raw("menu.top-item-nome")
                .replace("<posicao>", String.valueOf(position))
                .replace("<jogador>", entry.name());
        List<String> loreLines = messages.rawList("menu.top-item-lore").stream()
                .map(line -> line.replace("<tempo>", TimeFormatter.format(entry.totalSeconds())))
                .toList();
        return head(entry.name(), name, loreLines.toArray(new String[0]));
    }

    private ItemStack buildEmptyItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(MM.deserialize(messages.raw("menu.top-vazio")).decoration(TextDecoration.ITALIC, false));
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

    private ItemStack glass() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.empty());
        glass.setItemMeta(meta);
        return glass;
    }
}
