package com.alkacode.time.hook;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Soft-dependency para o Citizens via reflection (net.citizensnpcs.api.CitizensAPI) -
 * mesma abordagem do AlkaVips/hook/CitizensHook, adaptada pro NPC unico de tempo
 * online do AlkaTime.
 */
public final class CitizensHook {

    private static final String CITIZENS_API_CLASS = "net.citizensnpcs.api.CitizensAPI";
    private static final String SKIN_TRAIT_CLASS = "net.citizensnpcs.trait.SkinTrait";

    private final JavaPlugin plugin;

    public CitizensHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isAvailable() {
        Plugin citizens = Bukkit.getPluginManager().getPlugin("Citizens");
        return citizens != null && citizens.isEnabled();
    }

    private Object registry() {
        return HookReflection.invokeStatic(plugin.getLogger(), CITIZENS_API_CLASS, "getNPCRegistry", new Class<?>[0]);
    }

    /** Retorna o id do NPC criado, ou -1 se o Citizens nao estiver disponivel/a chamada falhar. */
    public int createNPC(String name, Location loc, String skin) {
        if (!isAvailable() || name == null || loc == null) {
            return -1;
        }
        Object registry = registry();
        Object npc = HookReflection.invokeInstance(plugin.getLogger(), registry, "createNPC",
                new Class<?>[]{EntityType.class, String.class}, EntityType.PLAYER, name);
        if (npc == null) {
            return -1;
        }
        HookReflection.invokeInstance(plugin.getLogger(), npc, "spawn", new Class<?>[]{Location.class}, loc);
        Object id = HookReflection.invokeInstance(plugin.getLogger(), npc, "getId", new Class<?>[0]);
        if (skin != null && !skin.isBlank()) {
            applySkin(npc, skin);
        }
        return id instanceof Integer i ? i : -1;
    }

    private void applySkin(Object npc, String skin) {
        Class<?> skinTraitClass;
        try {
            skinTraitClass = Class.forName(SKIN_TRAIT_CLASS);
        } catch (Throwable t) {
            return;
        }
        Object trait = HookReflection.invokeInstance(plugin.getLogger(), npc, "getOrAddTrait",
                new Class<?>[]{Class.class}, skinTraitClass);
        HookReflection.invokeInstance(plugin.getLogger(), trait, "setSkinName", new Class<?>[]{String.class}, skin);
    }

    public void removeNPC(int npcId) {
        Object npc = npcById(npcId);
        if (npc != null) {
            HookReflection.invokeInstance(plugin.getLogger(), npc, "destroy", new Class<?>[0]);
        }
    }

    private Object npcById(int npcId) {
        if (!isAvailable()) {
            return null;
        }
        return HookReflection.invokeInstance(plugin.getLogger(), registry(), "getById", new Class<?>[]{int.class}, npcId);
    }

    /** True se a entidade clicada e um NPC do Citizens com exatamente esse id - usado pelo listener de clique. */
    public boolean isMatchingNpc(Entity entity, int npcId) {
        if (!isAvailable()) {
            return false;
        }
        Object registry = registry();
        Object isNpc = HookReflection.invokeInstance(plugin.getLogger(), registry, "isNPC", new Class<?>[]{Entity.class}, entity);
        if (!(isNpc instanceof Boolean bool) || !bool) {
            return false;
        }
        Object npc = HookReflection.invokeInstance(plugin.getLogger(), registry, "getNPC", new Class<?>[]{Entity.class}, entity);
        Object id = HookReflection.invokeInstance(plugin.getLogger(), npc, "getId", new Class<?>[0]);
        return id instanceof Integer i && i == npcId;
    }
}
