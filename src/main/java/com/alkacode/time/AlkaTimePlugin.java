package com.alkacode.time;

import com.alkacode.core.api.AlkaAPI;
import com.alkacode.core.plugin.AlkaPlugin;
import com.alkacode.economy.AlkaEconomyPlugin;
import com.alkacode.time.api.AlkaTimeAPI;
import com.alkacode.time.api.AlkaTimeAPIProvider;
import com.alkacode.time.command.CommandAlkaTime;
import com.alkacode.time.command.CommandTempo;
import com.alkacode.time.database.TimeRepository;
import com.alkacode.time.hook.CitizensHook;
import com.alkacode.time.hook.DecentHologramsHook;
import com.alkacode.time.listener.PlayerTimeListener;
import com.alkacode.time.manager.PlayerTimeManager;
import com.alkacode.time.manager.RewardManager;
import com.alkacode.time.manager.TimeEconomyService;
import com.alkacode.time.npc.TimeNpcManager;
import com.alkacode.time.placeholder.AlkaTimeExpansion;
import com.alkacode.time.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;

/**
 * Contagem de tempo online, missoes de recompensa e ranking (ver rewards.yml). Sobre
 * o AlkaCore (banco/HikariCP, GUI) e a AlkaEconomy (moeda "ticks", reservada pra este
 * plugin - ver reference-alka-economy-currencies). Expoe {@link AlkaTimeAPI} via
 * ServicesManager pro AlkaRankUp consumir (hook de reflexao ja escrito la, esperando
 * exatamente essa classe/pacote/metodo).
 */
public final class AlkaTimePlugin extends AlkaPlugin {

    private PlayerTimeManager timeManager;
    private RewardManager rewardManager;
    private TimeNpcManager npcManager;
    private AlkaTimeExpansion papiExpansion;
    private TimeRepository repository;

    @Override
    protected void onPluginEnable() {
        AlkaAPI api = getAlkaAPI();
        Messages messages = new Messages(this);

        if (!(getServer().getPluginManager().getPlugin("AlkaEconomy") instanceof AlkaEconomyPlugin alkaEconomy)) {
            getLogger().severe("AlkaEconomy e obrigatorio e nao foi encontrado. Desativando.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        repository = new TimeRepository(api.getDatabase(), getLogger());
        timeManager = new PlayerTimeManager(repository, api.getScheduler(), getLogger());
        TimeEconomyService economyService = new TimeEconomyService(alkaEconomy.getEconomyManager());
        String defaultCurrency = getConfig().getString("default-currency", "ticks");
        rewardManager = new RewardManager(this, repository, api.getScheduler(), timeManager, economyService, defaultCurrency);

        getServer().getPluginManager().registerEvents(new PlayerTimeListener(timeManager, rewardManager), this);

        CommandTempo commandTempo = new CommandTempo(this, timeManager, rewardManager, repository, api.getScheduler(), economyService, messages);
        getCommand("tempo").setExecutor(commandTempo);
        getCommand("tempo").setTabCompleter(commandTempo);

        CitizensHook citizensHook = new CitizensHook(this);
        DecentHologramsHook hologramHook = new DecentHologramsHook(this);
        npcManager = new TimeNpcManager(this, citizensHook, hologramHook, commandTempo::openTimeMenu);
        getServer().getPluginManager().registerEvents(npcManager, this);

        CommandAlkaTime commandAlkaTime = new CommandAlkaTime(this, timeManager, rewardManager, npcManager, messages);
        getCommand("alkatime").setExecutor(commandAlkaTime);
        getCommand("alkatime").setTabCompleter(commandAlkaTime);

        getServer().getServicesManager().register(AlkaTimeAPI.class, new AlkaTimeAPIProvider(timeManager), this, ServicePriority.Normal);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiExpansion = new AlkaTimeExpansion(timeManager);
            papiExpansion.register();
        }

        // cobre o caso de /reload do servidor - sem isso, jogadores ja online nunca teriam
        // sessao iniciada ate o proximo join/quit (ver feedback-reload-must-verify-propagation).
        for (Player player : getServer().getOnlinePlayers()) {
            rewardManager.onJoin(player.getUniqueId());
            if (!player.hasPermission("alkatime.bypass")) {
                timeManager.onJoin(player);
            }
        }

        int intervalMinutes = Math.max(1, getConfig().getInt("autosave-interval-minutes", 5));
        long periodTicks = intervalMinutes * 60L * 20L;
        api.getScheduler().runAsyncRepeating(this::periodicTask, periodTicks, periodTicks);

        getLogger().info("AlkaTime habilitado (moeda padrao das recompensas: " + defaultCurrency + ").");
    }

    private void periodicTask() {
        timeManager.autosaveAll();
        int limit = Math.max(getConfig().getInt("menu.top-limit", 50), 10);
        var top = repository.getTopEntries(limit);
        Bukkit.getScheduler().runTask(this, () -> {
            npcManager.refreshHologram(top);
            if (papiExpansion != null) {
                papiExpansion.updateTopCache(top);
            }
        });
    }

    @Override
    protected void onPluginDisable() {
        if (timeManager != null) {
            timeManager.flushAllOnDisable();
        }
        // conexao/pool e do AlkaCore - fechado pelo proprio Core no seu onDisable, nunca aqui.
    }
}
