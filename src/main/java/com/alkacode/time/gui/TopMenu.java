package com.alkacode.time.gui;

import com.alkacode.core.gui.BaseGui;
import com.alkacode.time.AlkaTimePlugin;
import com.alkacode.time.config.MenuConfig;
import com.alkacode.time.database.TimeRepository;
import com.alkacode.time.gui.layout.GuiLayoutLoader;
import com.alkacode.time.util.Messages;
import com.alkacode.time.util.TimeFormatter;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ranking de tempo online (BaseGui do AlkaCore, R3) - a lista de {@link TimeRepository.TopEntry}
 * ja vem resolvida do banco (leitura assincrona feita pelo chamador, ver CommandTempo);
 * este menu so renderiza, nunca consulta o banco. Icone/texto/layout vem de
 * menus.yml/gui-layouts.yml (R8, chave "alkatime-top").
 */
public final class TopMenu extends BaseGui {

    private static final String KEY = "alkatime-top";

    private final List<TimeRepository.TopEntry> entries;
    private final Messages messages;
    private final Consumer<Player> openBack;

    public TopMenu(JavaPlugin plugin, Player player,
                    List<TimeRepository.TopEntry> entries, Messages messages, Consumer<Player> openBack) {
        super(plugin, player, menu(plugin).title(KEY, null), layout(plugin).rows(), KEY);
        this.entries = entries;
        this.messages = messages;
        this.openBack = openBack;
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

        setItem(layout.firstSlot('V'), menu.item("common.voltar", null), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            openBack.accept(player);
        });

        List<Integer> contentSlots = layout.findSlots('0');

        if (entries.isEmpty()) {
            setItem(contentSlots.get(contentSlots.size() / 2), menu.item(KEY + ".vazio", null));
            fill(menu.item("common.border", null));
            return;
        }

        for (int i = 0; i < entries.size() && i < contentSlots.size(); i++) {
            TimeRepository.TopEntry entry = entries.get(i);
            setItem(contentSlots.get(i), buildEntryItem(menu, entry, i + 1));
        }

        fill(menu.item("common.border", null));
    }

    private ItemStack buildEntryItem(MenuConfig menu, TimeRepository.TopEntry entry, int position) {
        String name = menu.name(KEY + ".item", Map.of(
                "posicao", String.valueOf(position),
                "jogador", entry.name()));
        List<String> loreLines = menu.rawLore(KEY + ".item", Map.of(
                "tempo", TimeFormatter.format(entry.totalSeconds())));
        return head(entry.name(), name, loreLines.toArray(new String[0]));
    }
}
