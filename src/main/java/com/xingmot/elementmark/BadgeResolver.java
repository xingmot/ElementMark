package com.xingmot.elementmark;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 物品 -&gt; 缩写解析器。
 *
 * 数据源优先级：
 * <ol>
 *   <li>config/elementmark.txt 的用户配置（key = 矿辞 tag 的 material 段）</li>
 *   <li>内置 118 元素表（含英式/美式拼写别名）</li>
 * </ol>
 * 解析途径（按序，先命中先用）：
 * <ol>
 *   <li><b>物品 tag</b>：只认 c: / forge: 命名空间、路径形如 "&lt;form&gt;/&lt;material&gt;"
 *       的 tag（如 c:plates/zinc），取 "/" 后段作为材料名；</li>
 *   <li><b>流体容器</b>：桶这类物品不带材料类 tag，改用桶内流体的注册 ID path
 *       （如 gtceu:soldering_alloy_bucket 装的流体 gtceu:soldering_alloy → "soldering_alloy"）。
 *       流体本身不是物品、不渲染角标，能挂角标的容器物品就是桶。</li>
 * </ol>
 * 材料名查表时除原名外还尝试<b>形态变体</b>（见 {@link #lookupWithVariants}）：
 * 粗矿块的 tag 段是 raw_x、熔融/液化流体是 molten_x / liquid_x、等离子体是 x_plasma，
 * 剥掉形态标记后仍是同一个材料。
 *
 * 合金、虚构材料等两处都无命中 -&gt; 缓存空串，不显示角标。
 *
 * tag 集合在 datapack 加载后不再变化；但用户配置可变，
 * 故 {@link #invalidate()} 供配置重载时清空缓存。
 */
public final class BadgeResolver {

    /** 118 元素 + 三组英式/美式拼写别名（aluminium/aluminum、sulfur/sulphur、caesium/cesium） */
    private static final String RAW = """
            hydrogen:H,helium:He,lithium:Li,beryllium:Be,boron:B,carbon:C,nitrogen:N,oxygen:O,fluorine:F,neon:Ne,
            sodium:Na,magnesium:Mg,aluminium:Al,aluminum:Al,silicon:Si,phosphorus:P,sulfur:S,sulphur:S,chlorine:Cl,argon:Ar,
            potassium:K,calcium:Ca,scandium:Sc,titanium:Ti,vanadium:V,chromium:Cr,manganese:Mn,iron:Fe,cobalt:Co,nickel:Ni,
            copper:Cu,zinc:Zn,gallium:Ga,germanium:Ge,arsenic:As,selenium:Se,bromine:Br,krypton:Kr,rubidium:Rb,strontium:Sr,
            yttrium:Y,zirconium:Zr,niobium:Nb,molybdenum:Mo,technetium:Tc,ruthenium:Ru,rhodium:Rh,palladium:Pd,silver:Ag,cadmium:Cd,
            indium:In,tin:Sn,antimony:Sb,tellurium:Te,iodine:I,xenon:Xe,caesium:Cs,cesium:Cs,barium:Ba,lanthanum:La,
            cerium:Ce,praseodymium:Pr,neodymium:Nd,promethium:Pm,samarium:Sm,europium:Eu,gadolinium:Gd,terbium:Tb,dysprosium:Dy,holmium:Ho,
            erbium:Er,thulium:Tm,ytterbium:Yb,lutetium:Lu,hafnium:Hf,tantalum:Ta,tungsten:W,rhenium:Re,osmium:Os,iridium:Ir,
            platinum:Pt,gold:Au,mercury:Hg,thallium:Tl,lead:Pb,bismuth:Bi,polonium:Po,astatine:At,radon:Rn,francium:Fr,
            radium:Ra,actinium:Ac,thorium:Th,protactinium:Pa,uranium:U,neptunium:Np,plutonium:Pu,americium:Am,curium:Cm,berkelium:Bk,
            californium:Cf,einsteinium:Es,fermium:Fm,mendelevium:Md,nobelium:No,lawrencium:Lr,rutherfordium:Rf,dubnium:Db,seaborgium:Sg,bohrium:Bh,
            hassium:Hs,meitnerium:Mt,darmstadtium:Ds,roentgenium:Rg,copernicium:Cn,nihonium:Nh,flerovium:Fl,moscovium:Mc,livermorium:Lv,tennessine:Ts,
            oganesson:Og
            """;

    private static final Map<String, String> ELEMENTS = new HashMap<>();
    /** value 为空串表示"已知无符号"；ConcurrentHashMap 不允许 null value，用空串避免重复解析 */
    private static final Map<Item, String> CACHE = new ConcurrentHashMap<>();
    /** 流体注册 ID 的解析缓存，键为 Fluid 实例（JEI 流体角标与桶共用） */
    private static final Map<Fluid, String> FLUID_CACHE = new ConcurrentHashMap<>();

    /**
     * 材料名里可剥掉的<b>形态前缀</b>：粗矿块的 tag 段（{@code raw_lead}）、
     * 熔融与液化流体的注册 ID（{@code molten_steel} / {@code liquid_oxygen}）。
     * 只在原名未命中时才尝试剥——{@code black_bronze}、{@code red_steel} 这类
     * "前缀是材料名一部分"的真材料必须按全名命中，不能被误剥。
     */
    private static final String[] STRIPPABLE_PREFIXES = {"raw_", "molten_", "liquid_"};

    /** GT 等离子体流体的形态后缀（{@code americium_plasma}），本身不是材料 */
    private static final String SUFFIX_PLASMA = "_plasma";

    static {
        for (String pair : RAW.split(",")) {
            String[] kv = pair.trim().split(":");
            if (kv.length == 2) {
                ELEMENTS.put(kv[0], kv[1]);
            }
        }
    }

    private BadgeResolver() {}

    /** @return 缩写；无配置也无内置元素命中时返回 null */
    public static String resolve(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String symbol = CACHE.computeIfAbsent(stack.getItem(), BadgeResolver::compute);
        return symbol.isEmpty() ? null : symbol;
    }

    /** 清空按物品的解析缓存。配置内容变化后必须调用，否则旧结果不会刷新 */
    public static void invalidate() {
        CACHE.clear();
        FLUID_CACHE.clear();
    }

    /**
     * 按流体注册 ID 解析缩写（配置里同一批材料键）。
     * 供两条途径共用：桶（桶内流体）与 JEI 的流体条目。
     *
     * @return 缩写；未命中返回 null
     */
    public static String resolveFluid(Fluid fluid) {
        if (fluid == null || fluid.isSame(Fluids.EMPTY)) {
            return null;
        }
        String symbol = FLUID_CACHE.computeIfAbsent(fluid, f -> {
            ResourceLocation id = ForgeRegistries.FLUIDS.getKey(f);
            if (id == null) {
                return "";
            }
            String hit = lookupWithVariants(id.getPath());
            return hit == null ? "" : hit;
        });
        return symbol.isEmpty() ? null : symbol;
    }

    // 1.20.1 中 builtInRegistryHolder() 即 Item -> Holder 的唯一途径（1.21 才换成 getItemHolder()），
    // 弃用标记无法规避，局部压制；1.20.1 起 Holder.tags() 返回 Stream<TagKey<T>>，烧成一条流式管道
    @SuppressWarnings("deprecation")
    private static String compute(Item item) {
        // 途径一：材料类 item tag，取 <form>/<material> 的尾段
        String byTag = item.builtInRegistryHolder().tags()
                .map(TagKey::location)
                .filter(tag -> tag.getNamespace().equals("c") || tag.getNamespace().equals("forge"))
                .map(ResourceLocation::getPath)
                // 只认 <form>/<material> 形态，跳过 c:ingots 这类集合 tag
                .filter(path -> path.indexOf('/') >= 0)
                .map(path -> path.substring(path.lastIndexOf('/') + 1))
                .map(BadgeResolver::lookupWithVariants)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (byTag != null) {
            return byTag;
        }

        // 途径二：流体容器。桶没有材料类 tag，用桶内流体的注册 ID path 反查；
        // 流体 ID 取的是 still 版（桶里装的不会是 flowing 版）
        if (item instanceof BucketItem bucket) {
            String hit = resolveFluid(bucket.getFluid());
            if (hit != null) {
                return hit;
            }
        }
        return "";
    }

    /**
     * 按候选序列查第一个命中：原名 → 剥 {@link #STRIPPABLE_PREFIXES} 前缀 →
     * 剥 {@link #SUFFIX_PLASMA} 后缀。全名优先，见前缀常量上的说明。
     *
     * @return 缩写；所有候选都未命中返回 null
     */
    private static String lookupWithVariants(String name) {
        String hit = lookupName(name);
        if (hit != null) {
            return hit;
        }
        for (String prefix : STRIPPABLE_PREFIXES) {
            if (name.startsWith(prefix) && name.length() > prefix.length()) {
                hit = lookupName(name.substring(prefix.length()));
                if (hit != null) {
                    return hit;
                }
            }
        }
        if (name.endsWith(SUFFIX_PLASMA) && name.length() > SUFFIX_PLASMA.length()) {
            return lookupName(name.substring(0, name.length() - SUFFIX_PLASMA.length()));
        }
        return null;
    }

    /** 单个名字的查表：配置优先，其次内置元素表 */
    private static String lookupName(String name) {
        String fromConfig = ConfigLoader.lookup(name);
        return fromConfig != null ? fromConfig : ELEMENTS.get(name);
    }
}
