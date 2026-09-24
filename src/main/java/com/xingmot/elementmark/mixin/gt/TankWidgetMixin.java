package com.xingmot.elementmark.mixin.gt;

import com.gregtechceu.gtceu.api.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import com.xingmot.elementmark.client.ElementMarkClient;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * GT 流体槽 widget 的角标注入——GT 配方界面流体的<b>真实绘制点</b>。
 *
 * <p>GT 配方界面（EMI/JEI 配方查看器里）由 lowdraglib 的 {@code ModularWrapper}
 * 把 GT 自己的 Widget 树整体画上去，流体槽是 GT 的
 * {@code com.gregtechceu.gtceu.api.gui.widget.TankWidget}（<b>直接继承</b>
 * {@code lowdraglib.gui.widget.Widget}，与 EMI 的 TankWidget 同名但毫无关系），
 * 流体贴图在它的 {@code drawInBackground} 里经 {@code DrawerHelper.drawFluidForGui}
 * 画出。EMI 原生的 {@code FluidEmiStack.render} / EMI {@code TankWidget.drawStack}
 * 两条注入对它都不生效（后者即便触发也会被 wrapper 后画的 GT GUI 盖掉），
 * 故必须挂在真正画流体的方法尾部。
 *
 * <p>坐标：流体画在 {@code position + 1}（内缩 1px 的槽内区），角标取
 * 槽位内居中的 16×16 区左上角，与流体同一 pose 空间，TAIL 时叠加必然在贴图之上。
 * 只在当前屏幕是配方查看器（EMI/JEI）时绘制，避免 GT 机器 GUI 里的储罐也挂角标，
 * 见 {@link ElementMarkClient#drawGuiFluidBadge}。
 *
 * <p><b>取流体的来源必须与 {@code drawInBackground} 画的来源一致</b>：GT 的
 * {@code drawInBackground} 画的是字段 {@code lastFluidInTank}，而同类的公开方法
 * {@code getFluid()} 只在 {@code isClientSideWidget} 或 {@code isRemote()} 为真时才返回
 * 该字段，否则回落到 {@code fluidTank.getFluidInTank(tank)}——配方界面里 GT 会把
 * 流体槽的 handler 换成 {@code EmptyFluidHandler}（见 {@code GTEmiRecipe.addWidgets}，
 * 避免与 EMI 原生槽位双重绘制），于是 {@code getFluid()} 返回空而流体照样被画出来：
 * 表现为"贴图有、角标没有"。故这里以 {@link #lastFluidInTank} 为主、{@code getFluid()} 兜底。
 *
 * <p>两个来源<b>各自都可能为空</b>，而且不是同一个原因（实测同一槽位既有"字段有值、
 * 方法返回空"的帧，也有"字段为 null、方法有值"的帧——后者发生在 GT 把 handler 换成
 * Empty 之后又走了一次 {@code setClientSideWidget} 的时机差里）。所以是"取第一个
 * 非空者"，而不是在两者之间二选一。
 *
 * <p>{@code remap = false} + {@code require = 0}；是否加载由 {@code MixinConditions}
 * 按"运行环境是否存在 GTCEu"决定。
 */
@Mixin(value = TankWidget.class, remap = false)
public class TankWidgetMixin {

    /** GT 绘制流体时真正读取的字段，见 {@code TankWidget#drawInBackground}。 */
    @Shadow(remap = false)
    protected FluidStack lastFluidInTank;

    @Inject(method = "drawInBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("TAIL"), remap = false, require = 0)
    private void elementmark$drawTankFluidBadge(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta,
                                                CallbackInfo ci) {
        TankWidget self = (TankWidget) (Object) this;
        FluidStack fluid = lastFluidInTank;
        if (fluid == null || fluid.isEmpty()) {
            // 机器 GUI 等场景下 handler 才是活的来源
            fluid = self.getFluid();
        }
        if (fluid == null || fluid.isEmpty()) {
            return;
        }
        Position pos = self.getPosition();
        Size size = self.getSize();
        ElementMarkClient.drawGuiFluidBadge(guiGraphics, fluid.getFluid(),
                pos.x + (size.width - 16) / 2, pos.y + (size.height - 16) / 2);
    }
}
