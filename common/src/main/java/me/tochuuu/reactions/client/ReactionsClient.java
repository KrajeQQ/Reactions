package me.tochuuu.reactions.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import me.tochuuu.reactions.Reactions;
import me.tochuuu.reactions.network.ReactionsVanillaChatSync;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class ReactionsClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Reactions.MOD_ID, "key"));
    private static final KeyMapping OPEN_CONFIG = new KeyMapping("key.reactions.open_config", InputConstants.Type.KEYSYM, InputConstants.KEY_R, CATEGORY);
    private static boolean initialized;

    private ReactionsClient() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        KeyMappingRegistry.register(OPEN_CONFIG);
        ReactionsVanillaChatSync.init();

        ClientTickEvent.CLIENT_POST.register(client -> {
            while (OPEN_CONFIG.consumeClick()) {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.screen == null) {
                    minecraft.setScreen(new ReactionsConfigScreen(null));
                }
            }
        });
    }
}
