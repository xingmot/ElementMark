package com.xingmot.elementmark;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 物品 -&gt; 缩写解析器。
 *
 * 数据源优先级：
 * <ol>
 *   <li>config/elementmark.txt 的用户配置（key = 矿辞 tag 的 material 段）</li>
 *   <li>内置 118 元素表（含英式/美式拼写别名）</li>
 * </ol>
 * 物品 tag 只认 c: / forge: 命名空间、路径形如 "&lt;form&gt;/&lt;material&gt;" 的 tag
 * （如 c:plates/zinc），取 "/" 后段作为 material 查询。
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
    }

    // 1.20.1 中 builtInRegistryHolder() 即 Item -> Holder 的唯一途径（1.21 才换成 getItemHolder()），
    // 弃用标记无法规避，局部压制；1.20.1 起 Holder.tags() 返回 Stream<TagKey<T>>，烧成一条流式管道
    @SuppressWarnings("deprecation")
    private static String compute(Item item) {
        return item.builtInRegistryHolder().tags()
                .map(TagKey::location)
                .filter(tag -> tag.getNamespace().equals("c") || tag.getNamespace().equals("forge"))
                .map(ResourceLocation::getPath)
                // 只认 <form>/<material> 形态，跳过 c:ingots 这类集合 tag
                .filter(path -> path.indexOf('/') >= 0)
                .map(path -> path.substring(path.lastIndexOf('/') + 1))
                // 配置优先，其次内置元素表
                .map(material -> {
                    String fromConfig = ConfigLoader.lookup(material);
                    return fromConfig != null ? fromConfig : ELEMENTS.get(material);
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
    }
}
