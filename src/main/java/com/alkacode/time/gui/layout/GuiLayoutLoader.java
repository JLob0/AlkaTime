package com.alkacode.time.gui.layout;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Carrega gui-layouts.yml e expoe os layouts (posicao dos slots) de cada GUI do AlkaTime. */
public final class GuiLayoutLoader {

    private final Map<String, GuiLayout> layouts = new HashMap<>();

    public GuiLayoutLoader(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "gui-layouts.yml");
        if (!file.exists()) {
            plugin.saveResource("gui-layouts.yml", false);
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        for (String key : cfg.getKeys(false)) {
            int rows = cfg.getInt(key + ".rows", 3);
            List<String> lines = cfg.getStringList(key + ".layout");
            String[] layout = lines.toArray(new String[0]);
            layouts.put(key, new GuiLayout(rows, layout));
        }
    }

    public GuiLayout getLayout(String name) {
        GuiLayout layout = layouts.get(name);
        if (layout == null) {
            throw new IllegalStateException("Layout '" + name + "' nao encontrado em gui-layouts.yml");
        }
        return layout;
    }

    public boolean hasLayout(String name) {
        return layouts.containsKey(name);
    }

    public record GuiLayout(int rows, String[] layout) {
        public List<Integer> findSlots(char c) {
            List<Integer> slots = new ArrayList<>();
            for (int row = 0; row < layout.length; row++) {
                String line = layout[row];
                for (int col = 0; col < line.length() && col < 9; col++) {
                    if (line.charAt(col) == c) {
                        slots.add(row * 9 + col);
                    }
                }
            }
            return slots;
        }

        public int firstSlot(char c) {
            List<Integer> slots = findSlots(c);
            return slots.isEmpty() ? -1 : slots.get(0);
        }
    }
}
