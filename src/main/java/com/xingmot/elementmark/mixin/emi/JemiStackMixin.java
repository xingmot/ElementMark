package com.xingmot.elementmark.mixin.emi;

import com.xingmot.elementmark.client.ElementMarkClient;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.jemi.JemiStack;

import mezz.jei.api.ingredients.IIngredientRenderer;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JEI 桥接栈（JEMI）的角标注入——覆盖"只有 JEI 集成、没有 EMI 插件"的模组
 * （如 ExtendedAE）在 EMI 配方界面里的流体。
 *
 * <p>EMI 显示 JEI 注册的配方时，配料被包成 {@code JemiStack}；它的
 * {@code render} 委托给 JEI 的 {@code IIngredientRenderer} 画图。委托对象未必是
 * JEI 的 {@code FluidTankRenderer}（第三方可能自带渲染器），挂在 JEI 渲染器上的
 * 注入可能覆盖不到；而 {@code JemiStack.render} 是这类流体<b>必经</b>的入口，
 * TAIL 处贴图已画完（pose 尚未 pop），按 renderer 的实际尺寸偏移叠画角标即可。
 *
 * <p>与 JEI 渲染器上的注入可能在同一帧先后触发：两者落在同一位置，重复绘制
 * 同一文本在视觉上无差异。
 */
@Mixin(value = JemiStack.class, remap = false)
public class JemiStackMixin {

    @Shadow(remap = false)
    private Object ingredient;

    @Shadow(remap = false)
    private IIngredientRenderer<?> renderer;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIFI)V",
            at = @At("TAIL"), remap = false, require = 0)
    private void elementmark$drawJemiFluidBadge(GuiGraphics guiGraphics, int x, int y, float delta, int flags,
                                                CallbackInfo ci) {
        if ((flags & EmiIngredient.RENDER_ICON) == 0) {
            return;
        }
        if (!(this.ingredient instanceof FluidStack stack) || stack.isEmpty()) {
            return;
        }
        // JemiStack.render 内部把 pose 平移 (x + dx, y + dy) 后再交给 renderer；
        // TAIL 时 pose 已弹回，因此这里按同样的偏移补出 renderer 的绘制原点。
        int dx = (16 - renderer.getWidth()) / 2;
        int dy = (16 - renderer.getHeight()) / 2;
        ElementMarkClient.drawEmiFluidBadge(guiGraphics, stack.getFluid(), x + dx, y + dy);
    }
}
