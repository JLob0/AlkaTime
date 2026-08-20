package com.alkacode.time.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Gera os 100 marcos de milestones.yml a partir da secao "fases" (formula), na
 * primeira vez que o arquivo nao tem "missoes" ainda - depois disso o arquivo em
 * disco vira a fonte da verdade normal (RewardManager so le "missoes", igual
 * sempre leu). Espacamento de horas dentro de cada fase e LINEAR (mesma ideia do
 * esboco original: "a cada X horas"); os valores de moeda crescem EXPONENCIAL
 * dentro da fase (regra de progressao do ecossistema - nunca linear em economia).
 */
final class MilestoneGenerator {

    private MilestoneGenerator() {
    }

    /** Retorna true se gerou (secao "missoes" estava ausente/vazia e foi preenchida). */
    static boolean generateIfMissing(FileConfiguration config) {
        ConfigurationSection existing = config.getConfigurationSection("missoes");
        if (existing != null && !existing.getKeys(false).isEmpty()) {
            return false;
        }
        ConfigurationSection fases = config.getConfigurationSection("fases");
        if (fases == null) {
            return false;
        }
        ConfigurationSection missoes = config.createSection("missoes");
        List<String> phaseKeys = new ArrayList<>(fases.getKeys(false));
        phaseKeys.sort(null); // fase_1, fase_2, ... ordem lexica = ordem numerica aqui
        for (String phaseKey : phaseKeys) {
            ConfigurationSection fase = fases.getConfigurationSection(phaseKey);
            if (fase != null) {
                generatePhase(missoes, fase, phaseKey.equals(phaseKeys.get(phaseKeys.size() - 1)));
            }
        }
        return true;
    }

    private static void generatePhase(ConfigurationSection missoes, ConfigurationSection fase, boolean isLastPhase) {
        String nome = fase.getString("nome", "Fase");
        int marcoInicial = fase.getInt("marco_inicial", 1);
        int quantidade = Math.max(1, fase.getInt("quantidade", 1));
        double horasInicio = fase.getDouble("horas_inicio");
        double horasFim = fase.getDouble("horas_fim");
        String material = fase.getString("material", "STONE");
        String corNome = fase.getString("cor_nome", "<white>");
        double ticksInicio = fase.getDouble("ticks_inicio", 10);
        double ticksFim = fase.getDouble("ticks_fim", ticksInicio);
        double coinsInicio = fase.getDouble("gold_inicio", 500);
        double coinsFim = fase.getDouble("gold_fim", coinsInicio);
        String marcoFinalMaterial = fase.getString("marco_final_material");
        List<String> marcoFinalComandos = fase.getStringList("marco_final_comandos");

        for (int i = 0; i < quantidade; i++) {
            int marcoNumero = marcoInicial + i;
            double progress = quantidade == 1 ? 1.0 : (double) i / (quantidade - 1);

            double horas = horasInicio + (horasFim - horasInicio) * progress; // linear
            long seconds = Math.round(horas * 3600.0);

            long ticks = Math.round(exponential(ticksInicio, ticksFim, progress));
            long coins = Math.round(exponential(coinsInicio, coinsFim, progress));

            boolean isFinalMarco = isLastPhase && i == quantidade - 1;
            String itemMaterial = isFinalMarco && marcoFinalMaterial != null ? marcoFinalMaterial : material;

            ConfigurationSection marco = missoes.createSection(String.valueOf(seconds));
            marco.set("material", itemMaterial);
            marco.set("name", corNome + "Marco #" + marcoNumero + " <gray>- " + formatHoras(horas));
            marco.set("lore", List.of(
                    "<gray>" + nome,
                    "<gray>Acumule " + formatHoras(horas) + " de tempo online vitalício."));
            marco.set("currency", "ticks");
            marco.set("amount", ticks);

            List<String> comandos = new ArrayList<>();
            comandos.add("eco give %player% " + coins);
            if (isFinalMarco) {
                comandos.addAll(marcoFinalComandos);
            }
            marco.set("comandos", comandos);
        }
    }

    /** Interpolacao exponencial entre start e end (progress 0..1) - crescimento proporcional, nao aditivo. */
    private static double exponential(double start, double end, double progress) {
        if (start <= 0) {
            start = 1;
        }
        return start * Math.pow(end / start, progress);
    }

    private static String formatHoras(double horas) {
        if (horas < 24) {
            return Math.round(horas) + "h";
        }
        double dias = horas / 24.0;
        return (Math.round(dias * 10) / 10.0) + " dias";
    }
}
