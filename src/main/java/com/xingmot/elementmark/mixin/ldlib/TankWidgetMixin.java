package com.xingmot.elementmark.mixin.ldlib;

import com.lowdragmc.lowdraglib.gui.widget.TankWidget;
import com.lowdragmc.lowdraglib.side.fluid.FluidStack;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;
import com.xingmot.elementmark.client.ElementMarkClient;

import net.minecraft.client.gui.GuiGraphics;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * lowdraglib 流体槽 widget 的角标注入（覆盖使用 LDLib 原生 TankWidget 的模组）。
 *
 * <p>LDLib 的 {@code gui.widget.TankWidget}（注意与 GT 自己的同名类无关，GT 的那个
 * 由 {@code mixin.gt.TankWidgetMixin} 负责）在 {@code drawInBackground} 里经
 * {@code DrawerHelper.drawFluidForGui} 画流体贴图，TAIL 叠画角标即可。
 *
 * <p>同 GT 侧：坐标取槽位内居中的 16×16 区，仅在配方查看器屏幕绘制，
 * 见 {@link ElementMarkClient#drawGuiFluidBadge}。
 */
@Mixin(value = TankWidget.class, remap = false)
public class TankWidgetMixin {

    @Shadow(remap = false)
    protected FluidStack lastFluidInTank;

    @Inject(method = "drawInBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("TAIL"), remap = false, require = 0)
    private void elementmark$drawTankFluidBadge(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta,
                                                CallbackInfo ci) {
        TankWidget self = (TankWidget) (Object) this;
        // 与 LDLib 自己画流体时的取法保持一致：优先 lastFluidInTank，缺失时回落 handler
        FluidStack fluid = lastFluidInTank;
        if (fluid == null || fluid.isEmpty()) {
            fluid = self.getFluid();
        }
        Position pos = self.getPosition();
        Size size = self.getSize();
        if (fluid == null || fluid.isEmpty()) {
            return;
        }
        ElementMarkClient.drawGuiFluidBadge(guiGraphics, fluid.getFluid(),
                pos.x + (size.width - 16) / 2, pos.y + (size.height - 16) / 2);
    }
}
