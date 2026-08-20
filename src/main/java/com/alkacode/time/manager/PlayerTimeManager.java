package com.alkacode.time.manager;

import com.alkacode.core.scheduler.AlkaScheduler;
import com.alkacode.time.database.TimeRepository;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Contador de tempo online 100% assincrono (R7) - nenhuma query roda na main thread.
 * {@code baseline} guarda o total persistido ANTES da sessao atual; o tempo "ao vivo"
 * de um jogador online e sempre {@code baseline + (agora - sessionStart)}, nunca lido
 * do banco de novo em cada chamada.
 *
 * <p>Ordem critica em {@link #onJoin}: {@code sessionStart} so e preenchido DEPOIS que
 * o baseline termina de carregar (os dois writes acontecem na mesma task assincrona,
 * em sequencia). Isso elimina qualquer corrida entre o load inicial e um autosave/quit
 * que tentasse escrever um total incompleto - o preco e uma janela de poucos
 * milissegundos em que um join muito rapido seguido de quit imediato simplesmente nao
 * conta (aceitavel, mesma ordem de grandeza do carregamento original do KTempo).
 */
public final class PlayerTimeManager {

    private final TimeRepository repository;
    private final AlkaScheduler scheduler;
    private final java.util.logging.Logger logger;

    private final Map<UUID, Long> sessionStart = new ConcurrentHashMap<>();
    private final Map<UUID, Long> baseline = new ConcurrentHashMap<>();

    // Tempo de HOJE (reseta a meia-noite, ver RewardsManager diario) - anchor proprio
    // (nao reaproveita sessionStart) pra permitir "cortar" o dia no meio de uma sessao
    // continua sem afetar a contagem vitalicia (ver AlkaTimePlugin#periodicTask, que
    // detecta a virada de dia e chama forceDailyReset pra quem esta online).
    private final Map<UUID, Long> dailyAnchor = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dailyBaseline = new ConcurrentHashMap<>();

    public PlayerTimeManager(TimeRepository repository, AlkaScheduler scheduler, java.util.logging.Logger logger) {
        this.repository = repository;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    /** Data de hoje no formato usado pra comparar com o daily_reset_date salvo no banco. */
    public static String today() {
        return java.time.LocalDate.now().toString();
    }

    public void onJoin(Player player) {
        UUID uuid = player.getUniqueId();
        scheduler.runAsync(() -> {
            long dbTotal = repository.getTotalSeconds(uuid);
            TimeRepository.DailyState daily = repository.getDailyState(uuid, today());
            baseline.put(uuid, dbTotal);
            dailyBaseline.put(uuid, daily.seconds());
            long now = System.currentTimeMillis();
            sessionStart.put(uuid, now);
            dailyAnchor.put(uuid, now);
        });
    }

    public void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        Long start = sessionStart.remove(uuid);
        Long dayStart = dailyAnchor.remove(uuid);
        if (start == null) {
            // load do join nunca terminou (desconexao quase instantanea) - nada a persistir.
            return;
        }
        long now = System.currentTimeMillis();
        long elapsed = (now - start) / 1000L;
        long newTotal = baseline.getOrDefault(uuid, 0L) + elapsed;
        baseline.remove(uuid);

        long dailyElapsed = dayStart == null ? 0L : (now - dayStart) / 1000L;
        long newDailyTotal = dailyBaseline.getOrDefault(uuid, 0L) + dailyElapsed;
        dailyBaseline.remove(uuid);

        String name = player.getName();
        String today = today();
        scheduler.runAsync(() -> {
            repository.saveTotalSeconds(uuid, name, newTotal);
            repository.saveDailySeconds(uuid, newDailyTotal, today);
        });
    }

    /**
     * Roda periodicamente (ja em thread assincrona, ver AlkaTimePlugin) - protege contra
     * crash/kill do processo sem salvar via quit. Reseta o segmento de sessao de cada
     * jogador pra "agora" ao mesmo tempo que persiste, entao a proxima chamada nunca
     * reconta o intervalo ja salvo.
     */
    public void autosaveAll() {
        long now = System.currentTimeMillis();
        String today = today();
        for (UUID uuid : sessionStart.keySet()) {
            Long start = sessionStart.get(uuid);
            if (start == null) {
                continue;
            }
            long elapsed = (now - start) / 1000L;
            long newTotal = baseline.getOrDefault(uuid, 0L) + elapsed;
            if (elapsed > 0) {
                baseline.put(uuid, newTotal);
                sessionStart.put(uuid, now);
            }

            Long dayStart = dailyAnchor.get(uuid);
            long dailyElapsed = dayStart == null ? 0L : (now - dayStart) / 1000L;
            long newDailyTotal = dailyBaseline.getOrDefault(uuid, 0L) + dailyElapsed;
            if (dailyElapsed > 0) {
                dailyBaseline.put(uuid, newDailyTotal);
                dailyAnchor.put(uuid, now);
            }

            if (elapsed <= 0 && dailyElapsed <= 0) {
                continue;
            }

            Player player = Bukkit.getPlayer(uuid);
            String name = player != null ? player.getName() : null;
            try {
                repository.saveTotalSeconds(uuid, name, newTotal);
                repository.saveDailySeconds(uuid, newDailyTotal, today);
            } catch (Exception e) {
                logger.log(java.util.logging.Level.SEVERE, "Erro no autosave de tempo online de " + uuid, e);
            }
        }
    }

    /** Chamado no onPluginDisable - sincrono e bloqueante de proposito, precisa terminar antes do AlkaCore fechar o pool de conexoes. */
    public void flushAllOnDisable() {
        long now = System.currentTimeMillis();
        String today = today();
        for (Map.Entry<UUID, Long> entry : sessionStart.entrySet()) {
            UUID uuid = entry.getKey();
            long elapsed = (now - entry.getValue()) / 1000L;
            long newTotal = baseline.getOrDefault(uuid, 0L) + elapsed;
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            repository.saveTotalSeconds(uuid, player.getName(), newTotal);

            Long dayStart = dailyAnchor.get(uuid);
            long dailyElapsed = dayStart == null ? 0L : (now - dayStart) / 1000L;
            long newDailyTotal = dailyBaseline.getOrDefault(uuid, 0L) + dailyElapsed;
            repository.saveDailySeconds(uuid, newDailyTotal, today);
        }
        sessionStart.clear();
        baseline.clear();
        dailyAnchor.clear();
        dailyBaseline.clear();
    }

    /** Chamado pela varredura de virada de dia do plugin (AlkaTimePlugin#periodicTask) pra
     * jogadores que continuam online quando a data muda - reseta o contador diario em
     * memoria E no banco (segundos e claimed) sem afetar o total vitalicio. */
    public void forceDailyReset(UUID uuid, String today) {
        dailyBaseline.put(uuid, 0L);
        dailyAnchor.put(uuid, System.currentTimeMillis());
        repository.resetDaily(uuid, today);
    }

    public boolean isTracking(UUID uuid) {
        return sessionStart.containsKey(uuid);
    }

    /** Leitura "ao vivo" sincrona - so valida pra jogador online cujo baseline ja carregou. Retorna 0 caso contrario (ver {@link #getOnlineSecondsAsync} pra qualquer uuid). */
    public long getOnlineSecondsSync(UUID uuid) {
        Long start = sessionStart.get(uuid);
        if (start == null) {
            return 0L;
        }
        long elapsed = (System.currentTimeMillis() - start) / 1000L;
        return baseline.getOrDefault(uuid, 0L) + elapsed;
    }

    /** Leitura "ao vivo" sincrona do tempo online de HOJE - so valida pra jogador online. */
    public long getTodaySecondsSync(UUID uuid) {
        Long start = dailyAnchor.get(uuid);
        if (start == null) {
            return 0L;
        }
        long elapsed = (System.currentTimeMillis() - start) / 1000L;
        return dailyBaseline.getOrDefault(uuid, 0L) + elapsed;
    }

    /** Funciona pra qualquer uuid (online ou offline) - usado pela AlkaTimeAPI e pelo menu de TOP/admin. */
    public CompletableFuture<Long> getOnlineSecondsAsync(UUID uuid) {
        if (isTracking(uuid)) {
            return CompletableFuture.completedFuture(getOnlineSecondsSync(uuid));
        }
        CompletableFuture<Long> future = new CompletableFuture<>();
        scheduler.runAsync(() -> future.complete(repository.getTotalSeconds(uuid)));
        return future;
    }

    /** Define o tempo total de um jogador (comando admin). Se online, reajusta a sessao ativa em vez de deixar o proximo autosave sobrescrever. */
    public CompletableFuture<Long> setTotalSeconds(UUID uuid, String name, long seconds) {
        long clamped = Math.max(0L, seconds);
        if (isTracking(uuid)) {
            baseline.put(uuid, clamped);
            sessionStart.put(uuid, System.currentTimeMillis());
        }
        CompletableFuture<Long> future = new CompletableFuture<>();
        scheduler.runAsync(() -> {
            repository.saveTotalSeconds(uuid, name, clamped);
            future.complete(clamped);
        });
        return future;
    }

    public CompletableFuture<Long> addTotalSeconds(UUID uuid, String name, long delta) {
        return getOnlineSecondsAsync(uuid).thenCompose(current -> setTotalSeconds(uuid, name, current + delta));
    }

    public CompletableFuture<Long> removeTotalSeconds(UUID uuid, String name, long delta) {
        return getOnlineSecondsAsync(uuid).thenCompose(current -> setTotalSeconds(uuid, name, current - delta));
    }
}
