package com.alkacode.time.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * API publica do AlkaTime, registrada no {@link org.bukkit.plugin.ServicesManager}. A
 * assinatura de {@link #getOnlineSeconds(UUID)} e fixa de proposito - o AlkaRankUp ja
 * tem um hook por reflexao ({@code com.alkacode.rankup.hook.TimeHook}) escrito ANTES
 * deste plugin existir, esperando exatamente esta classe/pacote/metodo pra desbloquear
 * o requisito "online_time" nos ranks. Mudar essa assinatura quebra essa integracao.
 */
public interface AlkaTimeAPI {

    /** Segundos online (lifetime, incluindo a sessao atual se o jogador estiver online agora). */
    CompletableFuture<Long> getOnlineSeconds(UUID uuid);
}
