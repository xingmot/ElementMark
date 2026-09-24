package com.xingmot.elementmark.client;

import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

/**
 * 从 EMI 配料里取流体本体。
 *
 * <p>各条路径喂进来的栈类型并不统一，逐个判定比"赌某一种"稳：
 * <ul>
 *   <li>{@code FluidEmiStack}（EMI 原生、以及 GT 在 EMI 分支下用
 *       {@code ForgeEmiStack.of(fluidStack)} 造的）：{@code getKey()} 就是 {@link Fluid}
 *       （注意该类<b>没有</b> {@code getFluid()}，只能走 {@code getKey()}）；</li>
 *   <li>{@code JemiStack}（JEMI 把 JEI 配料包一层）：其字段 {@code base} 由
 *       {@code IIngredientTypeWithSubtypes.getBase(ingredient)} 得来，对
 *       {@code ForgeTypes.FLUID_STACK} 而言正是 {@link Fluid}，而 {@code getKey()}
 *       返回的就是 {@code base}——所以对 JemiStack 直接命中上面的 {@code instanceof Fluid} 分支；</li>
 *   <li>个别模组把流体直接以 Forge {@link FluidStack} 作为 key 塞进来。</li>
 * </ul>
 *
 * <p>配料可能是多项（轮换配料），取第一个能解出流体的栈即可——与 EMI 自己
 * {@code TankWidget.drawStack} 遍历 {@code getEmiStacks()} 的语义一致。
 */
public final class EmiFluidFind {

    private EmiFluidFind() {}

    public static Fluid fromIngredient(EmiIngredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) {
            return null;
        }
        for (EmiStack stack : ingredient.getEmiStacks()) {
            Object key = stack.getKey();
            if (key instanceof Fluid fluid) {
                return fluid;
            }
            if (key instanceof FluidStack fluidStack && !fluidStack.isEmpty()) {
                return fluidStack.getFluid();
            }
        }
        return null;
    }
}
