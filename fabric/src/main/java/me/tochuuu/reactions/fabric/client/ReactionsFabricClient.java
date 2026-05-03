package me.tochuuu.reactions.fabric.client;

import me.tochuuu.reactions.Reactions;
import me.tochuuu.reactions.client.ReactionsClient;
import net.fabricmc.api.ClientModInitializer;

public final class ReactionsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Reactions.init();
        ReactionsClient.init();
    }
}
