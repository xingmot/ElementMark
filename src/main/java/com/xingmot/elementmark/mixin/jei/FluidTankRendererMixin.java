package com.xingmot.elementmark.mixin.jei;

import com.xingmot.elementmark.client.ElementMarkClient;

import mezz.jei.library.render.FluidTankRenderer;
import net.minecraft.client.gui.GuiGraphics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * JEI 流体条目的角标注入。
 *
 * <p>JEI 的流体图标由 {@code mezz.jei.library.render.FluidTankRenderer} 直接绘制流体贴图，
 * 完全不经过 {@code ItemRenderer.render}（物品角标的那条注入点管不到）。
 * 本 mixin 挂在该类的 4 参 {@code render(GuiGraphics, T, int, int)} 上——它是两条重载的
 * 公共根（2 参重载委托给它，无 pose 平移，x/y 是显式参数），TAIL 处流体贴图已画完，
 * 在同一坐标上叠一层角标文字即可。
 *
 * <p>目标类是 JEI 的内部包（{@code library}），以 {@code compileOnly} 依赖引入
 * （见 {@code gradle/scripts/dependencies.gradle}）且 {@code remap = false}——类名与方法名
 * 都是 JEI 自己的命名，不参与 MC 映射。泛型 {@code T} 擦除为 Object，
 * 由 {@code ElementMarkClient#drawJeiFluidBadge} 自行判定 FluidStack。
 *
 * <p>JEI 未安装时目标类永远不会加载，本 mixin 自然不生效；{@code require = 0} 保证
 * 其他 JEI 版本若改了方法签名也只是静默失效，不崩游戏。
 */
@Mixin(value = FluidTankRenderer.class, remap = false)
public class FluidTankRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/Object;II)V",
            at = @At("TAIL"), remap = false, require = 0)
    private void elementmark$drawFluidBadge(GuiGraphics guiGraphics, Object ingredient, int x, int y,
                                            CallbackInfo ci) {
        ElementMarkClient.drawJeiFluidBadge(guiGraphics, ingredient, x, y);
    }
}
