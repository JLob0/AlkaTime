package com.alkacode.time.placeholder;

import com.alkacode.time.database.TimeRepository;
import com.alkacode.time.manager.PlayerTimeManager;
import com.alkacode.time.util.TimeFormatter;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * %alkatime_tempo%, %alkatime_tempo_raw%, %alkatime_horas%, %alkatime_top_player_N%,
 * %alkatime_top_value_N% (N comecando em 1) - identificador "alkatime" (grep
 * confirmou nao colidir com nenhuma expansion ja existente na rede).
 *
 * <p>Os placeholders de TOP leem de um cache atualizado periodicamente por
 * {@link #updateTopCache(List)} (mesmo ciclo do autosave/holograma do NPC) - nunca
 * consultam o banco direto na hora do request, ja que PAPI pode chamar isso na main
 * thread de qualquer plugin (scoreboard, tab, chat) e R7 proibe query sincrona ali.
 */
public final class AlkaTimeExpansion extends PlaceholderExpansion {

    private final PlayerTimeManager timeManager;
    private final AtomicReference<List<TimeRepository.TopEntry>> topCache = new AtomicReference<>(List.of());

    public AlkaTimeExpansion(PlayerTimeManager timeManager) {
        this.timeManager = timeManager;
    }

    public void updateTopCache(List<TimeRepository.TopEntry> entries) {
        topCache.set(entries);
    }

    @Override
    public @NotNull String getIdentifier() {
        return "alkatime";
    }

    @Override
    public @NotNull String getAuthor() {
        return "MestreDEV";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (params == null) {
            return "";
        }
        String lower = params.toLowerCase();

        if (lower.startsWith("top_player_")) {
            return topEntry(lower.substring("top_player_".length()), TimeRepository.TopEntry::name);
        }
        if (lower.startsWith("top_value_")) {
            return topEntry(lower.substring("top_value_".length()), e -> TimeFormatter.format(e.totalSeconds()));
        }

        if (player == null) {
            return "";
        }
        long seconds = timeManager.getOnlineSecondsSync(player.getUniqueId());
        return switch (lower) {
            case "tempo" -> TimeFormatter.format(seconds);
            case "tempo_raw" -> String.valueOf(seconds);
            case "horas" -> TimeFormatter.formatHours(seconds);
            default -> null;
        };
    }

    private String topEntry(String indexStr, java.util.function.Function<TimeRepository.TopEntry, String> mapper) {
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            return null;
        }
        List<TimeRepository.TopEntry> entries = topCache.get();
        if (index < 1 || index > entries.size()) {
            return "-";
        }
        return mapper.apply(entries.get(index - 1));
    }
}
