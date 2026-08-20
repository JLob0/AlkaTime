package com.alkacode.time.database;

import com.alkacode.core.api.DatabaseProvider;
import com.alkacode.core.database.AbstractRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unico ponto de acesso ao banco do AlkaTime, sobre o {@link DatabaseProvider} do
 * AlkaCore (R2 - zero HikariConfig/JDBC proprio). Todas as chamadas aqui sao
 * bloqueantes de proposito (mesmo padrao de EnderChestRepository/EconomyManager) -
 * quem chama e responsavel por rodar fora da main thread (R7), ver
 * {@link com.alkacode.time.manager.PlayerTimeManager} e o AlkaScheduler do Core.
 */
public final class TimeRepository extends AbstractRepository {

    private final Logger logger;

    public TimeRepository(DatabaseProvider db, Logger logger) {
        super(db);
        this.logger = logger;
        createTable();
        addDailyColumnsIfMissing();
    }

    private void createTable() {
        try (Connection conn = db.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS alka_time_data (
                        player_uuid VARCHAR(36) PRIMARY KEY,
                        player_name VARCHAR(16),
                        total_seconds BIGINT DEFAULT 0,
                        claimed_rewards TEXT
                    )
                    """);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao criar tabela alka_time_data", e);
        }
    }

    /** Colunas novas das recompensas DIARIAS - instalacao existente nao tem elas ainda, ADD COLUMN falha
     * silenciosamente (coluna ja existe) em qualquer boot depois do primeiro, mesmo padrao ja usado em
     * outros repositorios Alka* (ex: ClanShopItemRepository/CrateLocationRepository). */
    private void addDailyColumnsIfMissing() {
        tryAddColumn("ALTER TABLE alka_time_data ADD COLUMN daily_seconds BIGINT DEFAULT 0");
        tryAddColumn("ALTER TABLE alka_time_data ADD COLUMN daily_reset_date VARCHAR(10) DEFAULT ''");
        tryAddColumn("ALTER TABLE alka_time_data ADD COLUMN daily_claimed TEXT");
    }

    private void tryAddColumn(String sql) {
        try (Connection conn = db.getConnection(); var stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException ignored) {
            // coluna ja existe - normal em qualquer boot depois do primeiro.
        }
    }

    // -------------------------------------------------------------- tempo total

    public long getTotalSeconds(UUID uuid) {
        String sql = "SELECT total_seconds FROM alka_time_data WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("total_seconds") : 0L;
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao ler tempo total de " + uuid, e);
            return 0L;
        }
    }

    /** Upsert do total de segundos - tambem atualiza player_name pra telas de TOP/admin nao depender de resolver OfflinePlayer toda hora. */
    public void saveTotalSeconds(UUID uuid, String playerName, long totalSeconds) {
        String sql = upsert("alka_time_data",
                new String[]{"player_uuid", "player_name", "total_seconds"},
                new String[]{"player_uuid"});
        try {
            execute(sql, ps -> {
                ps.setString(1, uuid.toString());
                ps.setString(2, playerName);
                ps.setLong(3, totalSeconds);
            });
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao salvar tempo total de " + uuid, e);
        }
    }

    // ------------------------------------------------------------- recompensas

    public Set<Integer> getClaimedRewards(UUID uuid) {
        String sql = "SELECT claimed_rewards FROM alka_time_data WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new HashSet<>();
                }
                String raw = rs.getString("claimed_rewards");
                return parseClaimed(raw);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao ler recompensas coletadas de " + uuid, e);
            return new HashSet<>();
        }
    }

    public void addClaimedReward(UUID uuid, int seconds) {
        Set<Integer> claimed = getClaimedRewards(uuid);
        claimed.add(seconds);
        String csv = joinClaimed(claimed);
        ensureRow(uuid);
        String sql = "UPDATE alka_time_data SET claimed_rewards = ? WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, csv);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao salvar recompensa coletada de " + uuid, e);
        }
    }

    private void ensureRow(UUID uuid) {
        String sql = "SELECT 1 FROM alka_time_data WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao verificar linha de " + uuid, e);
            return;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO alka_time_data (player_uuid) VALUES (?)")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao criar linha de " + uuid, e);
        }
    }

    private Set<Integer> parseClaimed(String raw) {
        Set<Integer> result = new HashSet<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                result.add(Integer.parseInt(part.trim()));
            }
        }
        return result;
    }

    private String joinClaimed(Set<Integer> claimed) {
        List<Integer> sorted = new ArrayList<>(claimed);
        sorted.sort(null);
        return String.join(",", sorted.stream().map(String::valueOf).toList());
    }

    // --------------------------------------------------------------- diario

    public record DailyState(long seconds, Set<Integer> claimed) {
    }

    /** Le segundos/claimed de HOJE - se a data salva for de outro dia, reseta no banco
     * na hora e retorna ja zerado (self-healing, nenhum chamador precisa saber que o
     * dia virou primeiro). */
    public DailyState getDailyState(UUID uuid, String today) {
        ensureRow(uuid);
        String sql = "SELECT daily_seconds, daily_reset_date, daily_claimed FROM alka_time_data WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new DailyState(0L, new HashSet<>());
                }
                String resetDate = rs.getString("daily_reset_date");
                if (!today.equals(resetDate)) {
                    resetDaily(uuid, today);
                    return new DailyState(0L, new HashSet<>());
                }
                return new DailyState(rs.getLong("daily_seconds"), parseClaimed(rs.getString("daily_claimed")));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao ler estado diario de " + uuid, e);
            return new DailyState(0L, new HashSet<>());
        }
    }

    public void resetDaily(UUID uuid, String today) {
        ensureRow(uuid);
        String sql = "UPDATE alka_time_data SET daily_seconds = 0, daily_claimed = '', daily_reset_date = ? WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, today);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao resetar diario de " + uuid, e);
        }
    }

    public void saveDailySeconds(UUID uuid, long dailySeconds, String today) {
        ensureRow(uuid);
        String sql = "UPDATE alka_time_data SET daily_seconds = ?, daily_reset_date = ? WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, dailySeconds);
            ps.setString(2, today);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao salvar tempo diario de " + uuid, e);
        }
    }

    public void addDailyClaimed(UUID uuid, int seconds, String today) {
        DailyState state = getDailyState(uuid, today); // ja se autocura se o dia mudou desde a ultima leitura
        Set<Integer> claimed = new HashSet<>(state.claimed());
        claimed.add(seconds);
        String csv = joinClaimed(claimed);
        String sql = "UPDATE alka_time_data SET daily_claimed = ?, daily_reset_date = ? WHERE player_uuid = ?";
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, csv);
            ps.setString(2, today);
            ps.setString(3, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao salvar recompensa diaria coletada de " + uuid, e);
        }
    }

    // -------------------------------------------------------------------- top

    public record TopEntry(UUID uuid, String name, long totalSeconds) {
    }

    /** Le direto do banco (nao do cache em memoria) - reflete tambem jogadores offline. Chamada bloqueante de proposito, mesmo padrao de EconomyManager#getTopBalances. */
    public List<TopEntry> getTopEntries(int limit) {
        String sql = "SELECT player_uuid, player_name, total_seconds FROM alka_time_data ORDER BY total_seconds DESC LIMIT ?";
        List<TopEntry> result = new ArrayList<>();
        try (Connection conn = db.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("player_name");
                    result.add(new TopEntry(
                            UUID.fromString(rs.getString("player_uuid")),
                            name != null ? name : "???",
                            rs.getLong("total_seconds")));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Erro ao ler TOP de tempo online", e);
        }
        return result;
    }
}
