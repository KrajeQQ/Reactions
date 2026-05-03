package me.tochuuu.reactions.fabric.client.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.tochuuu.reactions.client.ReactionsConfigScreen;

public final class ReactionsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ReactionsConfigScreen::new;
    }
}
