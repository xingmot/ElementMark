package com.xingmot.elementmark.mixin;

import net.minecraft.client.resources.model.BakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xingmot.elementmark.client.ElementMarkClient;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 在 ItemRenderer.render 末尾追加元素角标绘制。
 * 只处理 GUI 展示语境；手持 / 掉落 / 实体穿戴等 3D 语境一律跳过。
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Inject(
            method = "render",
            at = @At("TAIL")
    )
    private void elementmark$drawElementBadge(ItemStack stack, ItemDisplayContext context, boolean leftHand,
                                          PoseStack poseStack, MultiBufferSource buffer,
                                          int combinedLight, int combinedOverlay, BakedModel bakedModel, CallbackInfo ci) {
        if (context != ItemDisplayContext.GUI) {
            return;
        }
        ElementMarkClient.drawBadge(stack, poseStack, buffer, combinedLight);
    }
}
