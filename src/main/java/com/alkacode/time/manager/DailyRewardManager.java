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
 * Recompensas DIARIAS de tempo online (daily_rewards.yml) - resetam todo dia, ao
 * contrario do RewardManager (marcos vitalicios, nunca reseta). Mesma forma de
 * ClaimResult/ClaimOutcome do RewardManager de proposito (TimeMenu/GUIs tratam os
 * dois do mesmo jeito), mas le tempo/claimed de hoje via
 * {@link TimeRepository#getDailyState}, nao do total vitalicio.
 */
public final class DailyRewardManager {

    private final JavaPlugin plugin;
    private final TimeRepository repository;
    private final AlkaScheduler scheduler;
    private final PlayerTimeManager timeManager;
    private final TimeEconomyService economyService;
    private final String defaultCurrency;

    private File rewardsFile;
    private FileConfiguration rewardsConfig;

    private final Map<UUID, Set<Integer>> claimedCache = new ConcurrentHashMap<>();

    public DailyRewardManager(JavaPlugin plugin, TimeRepository repository, AlkaScheduler scheduler,
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
        rewardsFile = new File(plugin.getDataFolder(), "daily_rewards.yml");
        if (!rewardsFile.exists()) {
            try (var in = plugin.getResource("daily_rewards.yml")) {
                if (in != null) {
                    Files.copy(in, rewardsFile.toPath());
                }
            } catch (IOException e) {
                plugin.getLogger().warning("Nao foi possivel criar daily_rewards.yml: " + e.getMessage());
            }
        }
        rewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile);
    }

    public ConfigurationSection getMissoes() {
        return rewardsConfig.getConfigurationSection("missoes");
    }

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
                plugin.getLogger().warning("Chave invalida em daily_rewards.yml: " + key);
            }
        }
        seconds.sort(Comparator.naturalOrder());
        return seconds;
    }

    public ConfigurationSection getReward(int seconds) {
        ConfigurationSection missoes = getMissoes();
        return missoes == null ? null : missoes.getConfigurationSection(String.valueOf(seconds));
    }

    /** Recarrega o cache do dia direto do banco - self-heals se o dia mudou (ver TimeRepository#getDailyState). */
    public void onJoin(UUID uuid) {
        scheduler.runAsync(() -> {
            TimeRepository.DailyState state = repository.getDailyState(uuid, PlayerTimeManager.today());
            claimedCache.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).addAll(state.claimed());
        });
    }

    public void onQuit(UUID uuid) {
        claimedCache.remove(uuid);
    }

    /** Chamado pela varredura de virada de dia do plugin pra jogadores que ficaram online passando da meia-noite. */
    public void resetCache(UUID uuid) {
        claimedCache.put(uuid, ConcurrentHashMap.newKeySet());
    }

    public boolean isClaimed(UUID uuid, int seconds) {
        return claimedCache.getOrDefault(uuid, Set.of()).contains(seconds);
    }

    public RewardManager.ClaimOutcome claim(Player player, int seconds) {
        ConfigurationSection reward = getReward(seconds);
        if (reward == null) {
            return new RewardManager.ClaimOutcome(RewardManager.ClaimResult.INVALID_REWARD, null, 0);
        }
        UUID uuid = player.getUniqueId();
        if (isClaimed(uuid, seconds)) {
            return new RewardManager.ClaimOutcome(RewardManager.ClaimResult.ALREADY_CLAIMED, null, 0);
        }
        if (timeManager.getTodaySecondsSync(uuid) < seconds) {
            return new RewardManager.ClaimOutcome(RewardManager.ClaimResult.NOT_ENOUGH_TIME, null, 0);
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
        scheduler.runAsync(() -> repository.addDailyClaimed(uuid, seconds, PlayerTimeManager.today()));

        return new RewardManager.ClaimOutcome(RewardManager.ClaimResult.SUCCESS, currencyId, amount);
    }
}
