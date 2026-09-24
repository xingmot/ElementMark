package com.xingmot.elementmark.mixin.emi;

import com.xingmot.elementmark.client.ElementMarkClient;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.FluidEmiStack;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.material.Fluid;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EMI 流体条目的角标注入。
 *
 * <p>EMI 的流体图标不走 {@code ItemRenderer.render}，也不走 JEI 的
 * {@code FluidTankRenderer}——由 {@code FluidEmiStack.render(GuiGraphics, x, y, delta, flags)}
 * 直接绘制（内部经 {@code EmiAgnos.renderFluid}），索引页、配方槽、侧栏全部经过它，
 * 是流体贴图的唯一每帧入口。TAIL 处贴图已画完，在同一 {@code (x, y)} 上叠画角标即可。
 *
 * <p>只在 {@code flags} 含 {@link EmiIngredient#RENDER_ICON}（仅画图标的那次调用）时绘制，
 * 其余 flags 组合（如只画数量的省略渲染）不重复叠加。
 *
 * <p><b>不必担心批烘焙</b>：EMI 侧栏的 {@code StackBatcher} 只烘焙实现了
 * {@code StackBatcher$Batchable} 的栈（物品栈是，流体栈不是），批处理分支会因
 * {@code instanceof Batchable} 不成立而回落到每帧直绘，本注入点因此对所有
 * EMI 界面按帧生效。
 *
 * <p>取流体不走 {@code @Shadow}（目标类的 {@code getKey} 实现来自父类，
 * 抽象 Shadow 在具体类上无法通过 javac），而是把 {@code this} 自转型为目标类——
 * 运行时 mixin 就是宿主实例，该转型恒成立。
 *
 * <p>类与方法名都是 EMI 自己的命名（不参与 MC 映射），{@code remap = false}；
 * {@code require = 0} 保证其它 EMI 版本若改了方法签名也只是静默失效，不崩游戏。
 * 是否加载由 {@code MixinConditions} 按"运行环境是否存在 EMI"决定（见 mixins.json 的 plugin）。
 */
@Mixin(value = FluidEmiStack.class, remap = false)
public class FluidEmiStackMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIFI)V",
            at = @At("TAIL"), remap = false, require = 0)
    private void elementmark$drawFluidBadge(GuiGraphics guiGraphics, int x, int y, float delta, int flags,
                                            CallbackInfo ci) {
        if ((flags & EmiIngredient.RENDER_ICON) == 0) {
            return;
        }
        FluidEmiStack self = (FluidEmiStack) (Object) this;
        if (self.isEmpty()) {
            return;
        }
        Object key = self.getKey();
        if (key instanceof Fluid fluid) {
            ElementMarkClient.drawEmiFluidBadge(guiGraphics, fluid, x, y);
        }
    }
}
