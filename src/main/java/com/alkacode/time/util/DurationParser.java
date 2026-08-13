package com.alkacode.time.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Aceita tanto um numero puro de segundos ("3600") quanto um formato composto ("1h30m", "2d", "45s"). */
public final class DurationParser {

    private static final Pattern UNIT_PATTERN = Pattern.compile("(\\d+)([dhms])");

    private DurationParser() {
    }

    /** Retorna -1 se a string nao puder ser interpretada como duracao. */
    public static long parseSeconds(String input) {
        if (input == null || input.isBlank()) {
            return -1;
        }
        String trimmed = input.trim();
        if (trimmed.chars().allMatch(Character::isDigit)) {
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException e) {
                return -1;
            }
        }

        Matcher matcher = UNIT_PATTERN.matcher(trimmed.toLowerCase());
        long total = 0;
        boolean matchedAny = false;
        while (matcher.find()) {
            matchedAny = true;
            long value = Long.parseLong(matcher.group(1));
            total += switch (matcher.group(2)) {
                case "d" -> value * 86400L;
                case "h" -> value * 3600L;
                case "m" -> value * 60L;
                default -> value;
            };
        }
        return matchedAny ? total : -1;
    }
}
