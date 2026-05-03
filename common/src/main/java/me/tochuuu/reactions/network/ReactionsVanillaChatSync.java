package me.tochuuu.reactions.network;

import dev.architectury.event.CompoundEventResult;
import dev.architectury.event.events.client.ClientChatEvent;
import me.tochuuu.reactions.client.RemoteEyeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;

import java.util.Optional;

public final class ReactionsVanillaChatSync {
    private static final String MARKER = "[RXS1 ";
    private static boolean initialized;

    private ReactionsVanillaChatSync() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ClientChatEvent.RECEIVED.register((bound, message) -> {
            if (tryReadSync(bound.name().getString(), message.getString())) {
                return CompoundEventResult.interruptFalse(message);
            }
            return CompoundEventResult.pass();
        });
    }

    public static void requestSync() {
        // Vanilla servers cannot relay hidden custom client data. Intentionally no visible chat fallback.
    }

    private static boolean tryReadSync(String senderName, String message) {
        if (!message.startsWith(MARKER) || !message.endsWith("]")) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || senderName.equals(minecraft.player.getName().getString())) {
            return true;
        }

        Optional<AbstractClientPlayer> sender = minecraft.level.players().stream()
            .filter(player -> senderName.equals(player.getName().getString()))
            .findFirst();
        if (sender.isEmpty()) {
            return true;
        }

        String[] parts = message.substring(MARKER.length(), message.length() - 1).split(",");
        if (parts.length != 8) {
            return true;
        }

        try {
            ReactionsNetworking.applyRemoteConfig(new RemoteEyeConfig(
                sender.get().getUUID(),
                sender.get().getId(),
                decode(parts[0]),
                decode(parts[1]),
                decode(parts[2]),
                decode(parts[3]),
                decode(parts[4]),
                decode(parts[5]),
                decode(parts[6]),
                decode(parts[7])
            ));
        } catch (NumberFormatException ignored) {
        }
        return true;
    }

    private static int decode(String value) {
        return Integer.parseInt(value, 36);
    }
}
