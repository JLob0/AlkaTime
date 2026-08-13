package com.alkacode.time.manager;

import com.alkacode.economy.CurrencyDefinition;
import com.alkacode.economy.EconomyManager;

import java.util.Optional;
import java.util.UUID;

/**
 * Fina camada sobre o {@link EconomyManager} da AlkaEconomy (R4 - dependencia direta
 * pra moedas). Diferente do EnderChest (uma unica moeda fixa), cada missao do AlkaTime
 * pode premiar uma moeda diferente (config-driven, ver feedback-currency-id-pattern),
 * entao aqui os metodos sempre recebem o currencyId explicito.
 */
public final class TimeEconomyService {

    private final EconomyManager economyManager;

    public TimeEconomyService(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    public boolean isValidCurrency(String currencyId) {
        return currencyId != null && economyManager.isValidCurrency(currencyId);
    }

    public void addBalance(UUID uuid, String currencyId, double amount) {
        economyManager.addBalance(uuid, currencyId, amount);
    }

    public String getCurrencyDisplayName(String currencyId) {
        Optional<CurrencyDefinition> def = economyManager.getCurrencies().stream()
                .filter(c -> c.id().equalsIgnoreCase(currencyId))
                .findFirst();
        return def.map(CurrencyDefinition::name).orElse(currencyId);
    }

    public String formatAmount(double amount) {
        return EconomyManager.formatValue(amount);
    }
}
