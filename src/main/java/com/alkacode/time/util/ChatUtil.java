package com.alkacode.time.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Conversao MiniMessage -> legado (codigo real "§") so pra consumidores que nao entendem
 * MiniMessage (nome de NPC do Citizens, DHAPI). character(SECTION_CHAR) (nao
 * legacyAmpersand()) + useUnusualXRepeatedCharacterHexFormat(): esses consumidores so
 * entendem "§" de verdade, nunca texto "&" cru - mesmo bug ja corrigido no AlkaVips
 * (v1.0.14 -> v1.0.15) e no AlkaMines (v1.0.83), ver memoria do projeto. */
public final class ChatUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character(LegacyComponentSerializer.SECTION_CHAR)
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    private ChatUtil() {
    }

    public static String toLegacy(String text) {
        if (text == null) {
            return "";
        }
        return LEGACY.serialize(MM.deserialize(text));
    }
}
