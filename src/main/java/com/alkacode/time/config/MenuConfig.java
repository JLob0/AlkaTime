package com.alkacode.time.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Centraliza titulos/nomes/lores de TODAS as GUIs do AlkaTime no menus.yml -
 * tudo editavel sem recompilar. Itens sao definidos por menus.yml.<caminho>
 * com material/name/lore; placeholders passados como {chave} sao substituidos na hora.
 */
public final class MenuConfig {

    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration config;

    public MenuConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "menus.yml");
        reload();
    }

    public void reload() {
        if (!file.exists()) {
            try {
                plugin.saveResource("menus.yml", false);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "menus.yml nao encontrado no jar - usando vazio.", e);
                config = new YamlConfiguration();
                return;
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        mergeMissingDefaults();
    }

    /** Adiciona chaves novas do menus.yml do jar ao arquivo salvo (migracao de versao). */
    private void mergeMissingDefaults() {
        try (InputStream in = plugin.getResource("menus.yml")) {
            if (in == null) {
                return;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : defaults.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defaults.get(key));
                    changed = true;
                }
            }
            if (changed) {
                config.save(file);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Falha ao migrar menus.yml", e);
        }
    }

    /** Titulo (menus.yml.<path>.title) com placeholders - path e a chave da GUI (ex: "alkatime-hub"). */
    public String title(String path, Map<String, String> placeholders) {
        return apply(config.getString(path + ".title", ""), placeholders);
    }

    /** Le uma string solta (nao um item material/name/lore) de menus.yml.<path> (path ja
     * completo, sem sufixo automatico) com placeholders - usado por linhas de lore
     * reaproveitadas fora de um icone (ex: um estado dinamico coletado/disponivel/bloqueado
     * escolhido em runtime pelo Java). */
    public String text(String path, Map<String, String> placeholders) {
        return apply(config.getString(path, ""), placeholders);
    }

    /** Constroi o ItemStack a partir de menus.yml.<path> (material/name/lore) com placeholders. */
    public ItemStack item(String path, Map<String, String> placeholders) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return new ItemStack(Material.STONE);
        }
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String name = name(path, placeholders);
        if (name != null && !name.isEmpty()) {
            meta.displayName(parse(name));
        }
        List<Component> lore = lore(path, placeholders);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    /** Nome (string MiniMessage) de menus.yml.<path> com placeholders aplicados. */
    public String name(String path, Map<String, String> placeholders) {
        return apply(config.getString(path + ".name", ""), placeholders);
    }

    /** Lore (lista de Component) de menus.yml.<path> com placeholders aplicados. */
    public List<Component> lore(String path, Map<String, String> placeholders) {
        List<Component> loreList = new ArrayList<>();
        for (String line : rawLore(path, placeholders)) {
            loreList.add(parse(line));
        }
        return loreList;
    }

    /** Lore como MiniMessage cru (sem virar Component). */
    public List<String> rawLore(String path, Map<String, String> placeholders) {
        List<String> loreList = new ArrayList<>();
        for (String line : config.getStringList(path + ".lore")) {
            loreList.add(apply(line, placeholders));
        }
        return loreList;
    }

    private static Component parse(String text) {
        return MiniMessage.miniMessage().deserialize("<!i>" + text);
    }

    private static String apply(String text, Map<String, String> placeholders) {
        if (text == null || placeholders == null || placeholders.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }
}
