package com.xingmot.elementmark.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.xingmot.elementmark.BadgeResolver;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

/**
 * GUI 物品左上角元素符号绘制。
 * -
 * 坐标数学：ItemRenderer.render 的 TAIL 处，外层 GUI 矩阵为
 * translate(槽位中心) * scale(16, -16, 16)，原点即物品中心、y 已翻转（determinant &lt; 0）。
 * 内层再 scale(SCALE, -SCALE, SCALE)：二次翻转让文字正立，
 * 坐标单位变为"文字像素"（1px = SCALE 物品单位 = 0.8 屏幕像素），
 * 物品因此覆盖 ±(0.5/SCALE) = ±10 文字像素。
 */
public final class ElementMarkClient {

    /** 文字像素 -&gt; 物品单位换算；1 字符像素 = 0.8 屏幕像素，字高约 6.4 屏幕像素 */
    private static final float SCALE = 0.05F;
    /** 物品半边长（文字像素）= 0.5 / SCALE */
    private static final float HALF = 0.5F / SCALE;
    /** 符号相对物品左上角的内边距（文字像素） */
    private static final float PAD = 1.0F;
    /** z 抬升（文字像素）= 1.5 物品单位。flat 模型朝前表面在 z=+0.5 单位，方块模型 GUI 旋转后顶点最多 ~0.87，取 1.5 稳定在前 */
    private static final float Z_LIFT = 30.0F;
    private static final int COLOR_FILL = 0xFFFFFF;
    private static final int COLOR_EDGE = 0x333333;

    private ElementMarkClient() {}

    public static void drawBadge(ItemStack stack, PoseStack pose, MultiBufferSource buffer, int light) {
        String symbol = BadgeResolver.resolve(stack);
        if (symbol == null) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        pose.pushPose();
        pose.scale(SCALE, -SCALE, SCALE);
        pose.translate(0.0F, 0.0F, Z_LIFT); // z 只能经矩阵抬升，drawInBatch 本身无 z 参数
        float x = -HALF + PAD;
        float y = -HALF + PAD;
        Matrix4f matrix = pose.last().pose();
        // 沿 chemlib 的双层画法：深灰偏移垫底当描边，白色主体
        font.drawInBatch(symbol, x + 1.0F, y + 1.0F, COLOR_EDGE, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);
        font.drawInBatch(symbol, x, y, COLOR_FILL, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}
