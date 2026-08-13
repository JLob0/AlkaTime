package com.alkacode.time.hook;

import eu.decentsoftware.holograms.api.DHAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Holograma do NPC de tempo online via DecentHolograms (DHAPI) - mesmo padrao ja
 * adotado pelo AlkaMines na rede (nao HolographicDisplays, que o exemplo do
 * yTempoOnline citava so como referencia externa). No-op silencioso se o
 * DecentHolograms nao estiver instalado.
 */
public final class DecentHologramsHook {

    private static final String HOLOGRAM_ID = "alkatime_npc_holo";

    private final boolean enabled;

    public DecentHologramsHook(JavaPlugin plugin) {
        this.enabled = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
        if (!enabled) {
            plugin.getLogger().warning("DecentHolograms nao encontrado - holograma do NPC de tempo desativado.");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void createOrUpdate(Location location, List<String> lines) {
        if (!enabled) {
            return;
        }
        if (DHAPI.getHologram(HOLOGRAM_ID) != null) {
            DHAPI.removeHologram(HOLOGRAM_ID);
        }
        DHAPI.createHologram(HOLOGRAM_ID, location, lines);
    }

    public void updateLines(List<String> lines) {
        if (!enabled) {
            return;
        }
        var hologram = DHAPI.getHologram(HOLOGRAM_ID);
        if (hologram != null) {
            DHAPI.setHologramLines(hologram, lines);
        }
    }

    public void delete() {
        if (!enabled) {
            return;
        }
        DHAPI.removeHologram(HOLOGRAM_ID);
    }
}
