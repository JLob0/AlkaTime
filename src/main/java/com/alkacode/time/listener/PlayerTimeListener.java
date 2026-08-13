package com.alkacode.time.listener;

import com.alkacode.time.manager.PlayerTimeManager;
import com.alkacode.time.manager.RewardManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerTimeListener implements Listener {

    private final PlayerTimeManager timeManager;
    private final RewardManager rewardManager;

    public PlayerTimeListener(PlayerTimeManager timeManager, RewardManager rewardManager) {
        this.timeManager = timeManager;
        this.rewardManager = rewardManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        rewardManager.onJoin(event.getPlayer().getUniqueId());
        if (event.getPlayer().hasPermission("alkatime.bypass")) {
            return;
        }
        timeManager.onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        timeManager.onQuit(event.getPlayer());
        rewardManager.onQuit(event.getPlayer().getUniqueId());
    }
}
