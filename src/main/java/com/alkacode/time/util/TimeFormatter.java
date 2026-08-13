package com.alkacode.time.util;

/** Formata segundos em texto legivel, indo ate anos - mesma ideia do "ate anos" do yTempoOnline. */
public final class TimeFormatter {

    private static final long MINUTE = 60;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long YEAR = 365 * DAY;

    private TimeFormatter() {
    }

    /** Formato longo: "1 ano, 3 dias, 4h 12m 5s" (omite unidades zeradas, exceto segundos quando o total e menor que 1 minuto). */
    public static String format(long totalSeconds) {
        long remaining = Math.max(0, totalSeconds);

        long years = remaining / YEAR;
        remaining %= YEAR;
        long days = remaining / DAY;
        remaining %= DAY;
        long hours = remaining / HOUR;
        remaining %= HOUR;
        long minutes = remaining / MINUTE;
        long seconds = remaining % MINUTE;

        StringBuilder sb = new StringBuilder();
        if (years > 0) sb.append(years).append(years == 1 ? " ano, " : " anos, ");
        if (days > 0) sb.append(days).append(days == 1 ? " dia, " : " dias, ");
        sb.append(hours).append("h ").append(minutes).append("m ").append(seconds).append("s");
        return sb.toString();
    }

    /** Total em horas com uma casa decimal - usado no placeholder %alkatime_horas%. */
    public static String formatHours(long totalSeconds) {
        double hours = totalSeconds / 3600.0;
        return String.format(java.util.Locale.US, "%.1f", hours);
    }
}
