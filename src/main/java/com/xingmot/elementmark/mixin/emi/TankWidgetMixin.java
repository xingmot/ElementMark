package com.xingmot.elementmark.mixin.emi;

import com.xingmot.elementmark.client.EmiFluidFind;
import com.xingmot.elementmark.client.ElementMarkClient;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.api.widget.TankWidget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.material.Fluid;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EMI 流体槽 widget 的角标注入——补 {@link FluidEmiStackMixin} 覆盖不到的路径。
 *
 * <p>带容量/液位的流体槽（GT 配方界面的流体槽、ExtendedAE 电路切片器等第三方配方大量使用）
 * 由 {@code TankWidget} 渲染。它<b>整个重写</b>了 {@code drawStack}：不调用
 * {@code stack.render(...)}，而是自建临时 {@code FluidEmiStack} 后按 16px 瓦片循环、
 * 逐行画液位（内部直接调 {@code EmiAgnos.renderFluidAgnos}）——
 * {@code FluidEmiStackMixin} 挂在 {@code FluidEmiStack.render} 上的注入因此永远不会触发，
 * 表现为"索引/侧栏有角标、配方界面没有"。
 *
 * <p>注入 {@code drawStack}：流体已画完，取槽位边界内居中的 16×16 图标区左上角
 * （与 {@code SlotWidget} 默认 {@code (w - 16) / 2} 的居中公式一致）叠画角标。
 * 液位高低不影响角标位置——它只随槽位走，不随液体表面走。
 *
 * <p><b>注入点必须是 {@code RETURN} 而不能是 {@code TAIL}</b>——这是本 mixin 唯一容易被
 * 写错的地方，字节码实证（EMI 1.1.24 {@code dev.emi.emi.api.widget.TankWidget.drawStack}）：
 * 该方法有 <b>两个</b> return 指令，{@code 307: return} 在循环体内，是"取到流体、画完液位
 * 后"的<b>提前返回</b>；{@code 311: return} 才是循环走完（配料里没有流体键）的收尾返回。
 * Mixin 的 {@code TAIL} 只选中<b>最后一个</b> return（javadoc：{@code TAIL - Selects the
 * last RETURN insn}），于是"真的画了流体"的那一支永远不触发注入，只有"配料里没有流体键"
 * 的那一支会触发——外观上就是"带液位的流体槽始终没有角标，而注入看起来已经生效"。
 * {@code RETURN} 选中<b>全部</b> return 指令（{@code RETURN - Selects RETURN insns}），
 * 两条支路都覆盖；每次调用只会走到其中一个 return，不会重复绘制。
 *
 * <p>准入不要求"配料恰好只有一项"：GT 这类模组在 JEI 共存时会把流体包成
 * {@code ClickableIngredient} 之类的包装对象交给 EMI（见
 * {@code TankWidget$JEICallWrapper.getJEIFluidClickable}），轮换配料也可能有多项。
 * 这里按 EMI 自己 {@code drawStack} 的语义遍历 {@code getEmiStacks()}，
 * 取第一个 key 为 {@code Fluid} 的栈。
 *
 * <p>不会双重绘制：TankWidget 的绘制路径不经过 {@code FluidEmiStack.render}，
 * 两个 mixin 各管一条路、互不重叠。
 *
 * <p>{@code remap = false} + {@code require = 0}，加载条件由 {@code MixinConditions}
 * 按"运行环境是否存在 EMI"决定。
 */
@Mixin(value = TankWidget.class, remap = false)
public class TankWidgetMixin {

    @Inject(method = "drawStack(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("RETURN"), remap = false, require = 0)
    private void elementmark$drawTankFluidBadge(GuiGraphics guiGraphics, int x, int y, float delta,
                                                CallbackInfo ci) {
        TankWidget self = (TankWidget) (Object) this;
        EmiIngredient ingredient = self.getStack();
        if (ingredient == null || ingredient.isEmpty()) {
            return;
        }
        // 与 EMI 自己的 drawStack 同语义：它遍历 getEmiStacks() 逐个画流体。
        // 各类包装栈（EMI 原生 / GT 造的 ForgeEmiStack / JEMI 的 JemiStack）由
        // EmiFluidFind 统一解出流体，不假设"恰好一项"。
        Fluid fluid = EmiFluidFind.fromIngredient(ingredient);
        if (fluid == null) {
            return;
        }
        Bounds bounds = self.getBounds();
        ElementMarkClient.drawEmiFluidBadge(guiGraphics, fluid,
                bounds.x() + (bounds.width() - 16) / 2,
                bounds.y() + (bounds.height() - 16) / 2);
    }
}
