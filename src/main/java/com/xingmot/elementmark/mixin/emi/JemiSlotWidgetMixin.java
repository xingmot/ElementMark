package com.xingmot.elementmark.mixin.emi;

import com.xingmot.elementmark.client.EmiFluidFind;
import com.xingmot.elementmark.client.ElementMarkClient;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.jemi.widget.JemiSlotWidget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.material.Fluid;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JEMI 槽位的角标注入——覆盖"只有 JEI 集成"的模组在 EMI 里显示的配方。
 *
 * <p>这类配方由 JEMI 把 JEI 的 {@code IRecipeSlotView} 包成 EMI widget：
 * {@code JemiSlotWidget}（以及把绘制转交给它的 {@code JemiTankWidget}）。它
 * <b>覆写</b>了 {@code drawStack}，不复用 {@code SlotWidget.drawStack} 里那条
 * {@code EmiIngredient.render} 调用，而是直接取 JEI 的 {@code IIngredientRenderer}
 * 来画——所以 {@code FluidEmiStack.render} 与 {@code SlotWidget.drawStack} 两条注入
 * 都碰不到它，必须单独挂在这个覆写方法上。
 *
 * <p>取流体走 {@link EmiFluidFind}：JEMI 的 {@code JemiStack.getKey()} 返回其
 * {@code base} 字段，而 {@code base} 正是
 * {@code ForgeTypes.FLUID_STACK.getBase(fluidStack)} 的结果，即 {@code Fluid} 本体——
 * 与 EMI 原生流体栈的判定同一条分支。
 *
 * <p>坐标与 EMI 自己的槽位绘制一致：图标画在槽位内居中的 16×16 区，
 * 即 {@code bounds + (size - 16) / 2}。
 *
 * <p><b>注入点必须是 {@code RETURN} 而不能是 {@code TAIL}</b>：该覆写方法同样有<b>两个</b>
 * return（字节码实证，EMI 1.1.24 —— {@code 140: return} 是"renderer 存在、已交给 JEI 渲染器
 * 画完"的提前返回，{@code 154: return} 是"renderer 为空、回落 {@code super.drawStack}"的
 * 收尾返回）。{@code TAIL} 只命中最后一个，于是真正画了流体的那一支被漏掉；{@code RETURN}
 * 命中全部 return，两条支路都覆盖。这与 {@code emi.TankWidgetMixin} 是同一类坑，原因同源。
 *
 * <p>{@code remap = false} + {@code require = 0}，加载条件由 {@code MixinConditions}
 * 按"运行环境是否存在 EMI"决定。
 */
@Mixin(value = JemiSlotWidget.class, remap = false)
public class JemiSlotWidgetMixin {

    @Inject(method = "drawStack(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("RETURN"), remap = false, require = 0)
    private void elementmark$drawFluidBadge(GuiGraphics guiGraphics, int x, int y, float delta,
                                            CallbackInfo ci) {
        SlotWidget self = (SlotWidget) (Object) this;
        EmiIngredient ingredient = self.getStack();
        Bounds bounds = self.getBounds();
        Fluid fluid = EmiFluidFind.fromIngredient(ingredient);
        if (fluid == null) {
            return;
        }
        ElementMarkClient.drawEmiFluidBadge(guiGraphics, fluid,
                bounds.x() + (bounds.width() - 16) / 2,
                bounds.y() + (bounds.height() - 16) / 2);
    }
}
