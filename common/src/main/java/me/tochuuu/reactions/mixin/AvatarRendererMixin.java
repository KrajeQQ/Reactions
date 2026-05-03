package me.tochuuu.reactions.mixin;

import me.tochuuu.reactions.client.PlayerEyeRenderLayer;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void reactions$addEyeLayer(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this;
        LivingEntityRendererAccessor<AvatarRenderState, PlayerModel> accessor = (LivingEntityRendererAccessor<AvatarRenderState, PlayerModel>) (Object) this;
        accessor.reactions$addLayer(new PlayerEyeRenderLayer(parent));
    }
}
