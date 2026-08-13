package com.alkacode.time.npc;

import com.alkacode.time.database.TimeRepository;
import com.alkacode.time.hook.CitizensHook;
import com.alkacode.time.hook.DecentHologramsHook;
import com.alkacode.time.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * NPC configuravel de tempo online (Citizens, softdepend) com holograma opcional
 * (DecentHolograms, softdepend) mostrando o TOP 1 - inspirado no NPC de spawn do
 * yTempoOnline. Persiste so localizacao/id em npc-data.yml; o proprio Citizens
 * mantem o NPC entre restarts, este arquivo so serve pra reencontrar o id salvo
 * (delnpc, listener de clique) e recriar o holograma (que o DecentHolograms nao
 * repersiste sozinho, mesmo racional do HologramManager do AlkaMines).
 */
public final class TimeNpcManager implements Listener {

    private final JavaPlugin plugin;
    private final CitizensHook citizensHook;
    private final DecentHologramsHook hologramHook;
    private final Consumer<Player> openMenu;
    private final File dataFile;

    private Integer npcId;
    private Location location;

    public TimeNpcManager(JavaPlugin plugin, CitizensHook citizensHook, DecentHologramsHook hologramHook,
                           Consumer<Player> openMenu) {
        this.plugin = plugin;
        this.citizensHook = citizensHook;
        this.hologramHook = hologramHook;
        this.openMenu = openMenu;
        this.dataFile = new File(plugin.getDataFolder(), "npc-data.yml");
        load();
    }

    private void load() {
        if (!dataFile.exists()) {
            return;
        }
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        if (!data.contains("npc-id") || !data.contains("world")) {
            return;
        }
        World world = Bukkit.getWorld(data.getString("world", ""));
        if (world == null) {
            plugin.getLogger().warning("Mundo do NPC de tempo online nao encontrado ao carregar npc-data.yml.");
            return;
        }
        npcId = data.getInt("npc-id");
        location = new Location(world, data.getDouble("x"), data.getDouble("y"), data.getDouble("z"),
                (float) data.getDouble("yaw"), (float) data.getDouble("pitch"));

        if (hologramHook.isEnabled()) {
            hologramHook.createOrUpdate(location.clone().add(0, 2.0, 0), currentLines(null));
        }
    }

    public boolean hasNpc() {
        return npcId != null;
    }

    public boolean setNpc(Player player) {
        if (!citizensHook.isAvailable()) {
            return false;
        }
        // se ja existe um NPC anterior, remove antes de criar o novo - so um NPC de tempo por vez.
        if (npcId != null) {
            citizensHook.removeNPC(npcId);
        }

        String name = plugin.getConfig().getString("npc.name", "<yellow>Tempo Online");
        String skin = plugin.getConfig().getString("npc.skin", "");
        Location loc = player.getLocation();

        int id = citizensHook.createNPC(ChatUtil.toLegacy(name), loc, skin);
        if (id == -1) {
            return false;
        }
        npcId = id;
        location = loc;
        save();

        if (hologramHook.isEnabled()) {
            hologramHook.createOrUpdate(loc.clone().add(0, 2.0, 0), currentLines(null));
        }
        return true;
    }

    public void delNpc() {
        if (npcId != null) {
            citizensHook.removeNPC(npcId);
        }
        hologramHook.delete();
        npcId = null;
        location = null;
        if (dataFile.exists() && !dataFile.delete()) {
            plugin.getLogger().warning("Nao foi possivel apagar npc-data.yml.");
        }
    }

    /** Atualiza as linhas do holograma com o TOP 1 atual - chamado periodicamente pelo autosave (ja fora da main thread para a leitura de banco, ver AlkaTimePlugin). */
    public void refreshHologram(List<TimeRepository.TopEntry> topEntries) {
        if (!hologramHook.isEnabled() || location == null) {
            return;
        }
        hologramHook.updateLines(currentLines(topEntries));
    }

    private List<String> currentLines(List<TimeRepository.TopEntry> topEntries) {
        List<String> template = plugin.getConfig().getStringList("hologram.lines");
        String topPlayer = "-";
        String topValue = "-";
        if (topEntries != null && !topEntries.isEmpty()) {
            topPlayer = topEntries.get(0).name();
            topValue = com.alkacode.time.util.TimeFormatter.format(topEntries.get(0).totalSeconds());
        }
        List<String> lines = new ArrayList<>();
        for (String line : template) {
            lines.add(line.replace("%top_1_player%", topPlayer).replace("%top_1_value%", topValue));
        }
        return lines;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (npcId == null) {
            return;
        }
        Entity clicked = event.getRightClicked();
        if (citizensHook.isMatchingNpc(clicked, npcId)) {
            event.setCancelled(true);
            openMenu.accept(event.getPlayer());
        }
    }

    private void save() {
        FileConfiguration data = new YamlConfiguration();
        data.set("npc-id", npcId);
        data.set("world", location.getWorld().getName());
        data.set("x", location.getX());
        data.set("y", location.getY());
        data.set("z", location.getZ());
        data.set("yaw", (double) location.getYaw());
        data.set("pitch", (double) location.getPitch());
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Erro ao salvar npc-data.yml", e);
        }
    }
}
