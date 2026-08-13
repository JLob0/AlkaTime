package com.alkacode.time.api;

import com.alkacode.time.manager.PlayerTimeManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AlkaTimeAPIProvider implements AlkaTimeAPI {

    private final PlayerTimeManager timeManager;

    public AlkaTimeAPIProvider(PlayerTimeManager timeManager) {
        this.timeManager = timeManager;
    }

    @Override
    public CompletableFuture<Long> getOnlineSeconds(UUID uuid) {
        return timeManager.getOnlineSecondsAsync(uuid);
    }
}
