package com.alkacode.time.util;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/** Conversao MiniMessage -> legado (&) so pra consumidores que nao entendem MiniMessage (nome de NPC do Citizens, DHAPI). */
public final class ChatUtil {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private ChatUtil() {
    }

    public static String toLegacy(String text) {
        if (text == null) {
            return "";
        }
        return LEGACY.serialize(MM.deserialize(text));
    }
}
