package com.alkacode.time.manager;

import com.alkacode.core.scheduler.AlkaScheduler;
import com.alkacode.time.database.TimeRepository;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Missoes de tempo online (rewards.yml) - "quantas o admin quiser", uma secao por
 * quantidade de segundos. Moeda vem da AlkaEconomy via {@link TimeEconomyService}
 * (R4); comandos extras (opcional) rodam pelo console, mesma ideia do KTempo original
 * mas sem ser o unico mecanismo de recompensa.
 */
public final class RewardManager {

    private final JavaPlugin plugin;
    private final TimeRepository repository;
    private final AlkaScheduler scheduler;
    private final PlayerTimeManager timeManager;
    private final TimeEconomyService economyService;
    private final String defaultCurrency;

    private File rewardsFile;
    private FileConfiguration rewardsConfig;

    private final Map<UUID, Set<Integer>> claimedCache = new ConcurrentHashMap<>();

    public RewardManager(JavaPlugin plugin, TimeRepository repository, AlkaScheduler scheduler,
                          PlayerTimeManager timeManager, TimeEconomyService economyService, String defaultCurrency) {
        this.plugin = plugin;
        this.repository = repository;
        this.scheduler = scheduler;
        this.timeManager = timeManager;
        this.economyService = economyService;
        this.defaultCurrency = defaultCurrency;
        load();
    }

    public void load() {
        rewardsFile = new File(plugin.getDataFolder(), "rewards.yml");
        if (!rewardsFile.exists()) {
            try (var in = plugin.getResource("rewards.yml")) {
                if (in != null) {
                    Files.copy(in, rewardsFile.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Nao foi possivel criar rewards.yml: " + e.getMessage());
            }
        }
        rewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile);
    }

    public ConfigurationSection getMissoes() {
        return rewardsConfig.getConfigurationSection("missoes");
    }

    /** Chaves (segundos) de todas as missoes configuradas, ordenadas crescente. */
    public List<Integer> getOrderedSeconds() {
        ConfigurationSection missoes = getMissoes();
        if (missoes == null) {
            return List.of();
        }
        List<Integer> seconds = new ArrayList<>();
        for (String key : missoes.getKeys(false)) {
            try {
                seconds.add(Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
                plugin.getLogger().warning("Chave invalida em rewards.yml (nao e um numero de segundos): " + key);
            }
        }
        seconds.sort(Comparator.naturalOrder());
        return seconds;
    }

    public ConfigurationSection getReward(int seconds) {
        ConfigurationSection missoes = getMissoes();
        return missoes == null ? null : missoes.getConfigurationSection(String.valueOf(seconds));
    }

    public void onJoin(UUID uuid) {
        // computeIfAbsent + addAll (nunca put/overwrite) - se um clique de claim() chegar
        // antes desse load assincrono terminar, a entrada que ele criou sobrevive em vez
        // de ser sobrescrita por este load mais lento (mesma logica de PlayerTimeManager#onJoin).
        scheduler.runAsync(() -> {
            Set<Integer> claimed = repository.getClaimedRewards(uuid);
            claimedCache.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).addAll(claimed);
        });
    }

    public void onQuit(UUID uuid) {
        claimedCache.remove(uuid);
    }

    public boolean isClaimed(UUID uuid, int seconds) {
        return claimedCache.getOrDefault(uuid, Set.of()).contains(seconds);
    }

    public enum ClaimResult {
        SUCCESS, INVALID_REWARD, NOT_ENOUGH_TIME, ALREADY_CLAIMED
    }

    public record ClaimOutcome(ClaimResult result, String currencyId, double amount) {
    }

    /** Sincrono na parte de checagem (usa o cache/PlayerTimeManager, ambos ja em memoria) - so a escrita final no banco e assincrona. */
    public ClaimOutcome claim(Player player, int seconds) {
        ConfigurationSection reward = getReward(seconds);
        if (reward == null) {
            return new ClaimOutcome(ClaimResult.INVALID_REWARD, null, 0);
        }
        UUID uuid = player.getUniqueId();
        if (isClaimed(uuid, seconds)) {
            return new ClaimOutcome(ClaimResult.ALREADY_CLAIMED, null, 0);
        }
        if (timeManager.getOnlineSecondsSync(uuid) < seconds) {
            return new ClaimOutcome(ClaimResult.NOT_ENOUGH_TIME, null, 0);
        }

        String currencyId = reward.getString("currency", defaultCurrency);
        double amount = reward.getDouble("amount", 0);
        if (amount > 0 && economyService.isValidCurrency(currencyId)) {
            economyService.addBalance(uuid, currencyId, amount);
        }
        for (String cmd : reward.getStringList("comandos")) {
            String parsed = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }

        claimedCache.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(seconds);
        scheduler.runAsync(() -> repository.addClaimedReward(uuid, seconds));

        return new ClaimOutcome(ClaimResult.SUCCESS, currencyId, amount);
    }
}
