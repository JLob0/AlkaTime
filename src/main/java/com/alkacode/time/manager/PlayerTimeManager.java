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

    public PlayerTimeManager(TimeRepository repository, AlkaScheduler scheduler, java.util.logging.Logger logger) {
        this.repository = repository;
        this.scheduler = scheduler;
        this.logger = logger;
    }

    public void onJoin(Player player) {
        UUID uuid = player.getUniqueId();
        scheduler.runAsync(() -> {
            long dbTotal = repository.getTotalSeconds(uuid);
            baseline.put(uuid, dbTotal);
            sessionStart.put(uuid, System.currentTimeMillis());
        });
    }

    public void onQuit(Player player) {
        UUID uuid = player.getUniqueId();
        Long start = sessionStart.remove(uuid);
        if (start == null) {
            // load do join nunca terminou (desconexao quase instantanea) - nada a persistir.
            return;
        }
        long elapsed = (System.currentTimeMillis() - start) / 1000L;
        long newTotal = baseline.getOrDefault(uuid, 0L) + elapsed;
        baseline.remove(uuid);
        String name = player.getName();
        scheduler.runAsync(() -> repository.saveTotalSeconds(uuid, name, newTotal));
    }

    /**
     * Roda periodicamente (ja em thread assincrona, ver AlkaTimePlugin) - protege contra
     * crash/kill do processo sem salvar via quit. Reseta o segmento de sessao de cada
     * jogador pra "agora" ao mesmo tempo que persiste, entao a proxima chamada nunca
     * reconta o intervalo ja salvo.
     */
    public void autosaveAll() {
        long now = System.currentTimeMillis();
        for (UUID uuid : sessionStart.keySet()) {
            Long start = sessionStart.get(uuid);
            if (start == null) {
                continue;
            }
            long elapsed = (now - start) / 1000L;
            if (elapsed <= 0) {
                continue;
            }
            long newTotal = baseline.getOrDefault(uuid, 0L) + elapsed;
            baseline.put(uuid, newTotal);
            sessionStart.put(uuid, now);

            Player player = Bukkit.getPlayer(uuid);
            String name = player != null ? player.getName() : null;
            try {
                repository.saveTotalSeconds(uuid, name, newTotal);
            } catch (Exception e) {
                logger.log(java.util.logging.Level.SEVERE, "Erro no autosave de tempo online de " + uuid, e);
            }
        }
    }

    /** Chamado no onPluginDisable - sincrono e bloqueante de proposito, precisa terminar antes do AlkaCore fechar o pool de conexoes. */
    public void flushAllOnDisable() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : sessionStart.entrySet()) {
            UUID uuid = entry.getKey();
            long elapsed = (now - entry.getValue()) / 1000L;
            long newTotal = baseline.getOrDefault(uuid, 0L) + elapsed;
            OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
            repository.saveTotalSeconds(uuid, player.getName(), newTotal);
        }
        sessionStart.clear();
        baseline.clear();
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
