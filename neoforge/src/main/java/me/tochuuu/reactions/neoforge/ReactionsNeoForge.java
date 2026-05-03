package me.tochuuu.reactions.neoforge;

import me.tochuuu.reactions.Reactions;
import me.tochuuu.reactions.client.ReactionsClient;
import me.tochuuu.reactions.client.ReactionsConfigScreen;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(Reactions.MOD_ID)
public final class ReactionsNeoForge {
    public ReactionsNeoForge() {
        // Run our common setup.
        Reactions.init();
        if (FMLEnvironment.getDist().isClient()) {
            ReactionsClient.init();
            ModLoadingContext.get().registerExtensionPoint(IConfigScreenFactory.class, () -> (container, parent) -> new ReactionsConfigScreen(parent));
        }
    }
}
