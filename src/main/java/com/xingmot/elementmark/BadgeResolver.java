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
 * 物品 -&gt; 元素符号解析器。
 *-
 * 数据源：物品注册 holder 上的矿辞 tag。只认 c: / forge: 命名空间、
 * 路径形如 "&lt;form&gt;/&lt;material&gt;" 的 tag（如 c:plates/zinc），取 "/" 后段查 118 元素表。
 * 合金、虚构材料等无命中 -&gt; 缓存空串，不显示角标。
 *-
 * tag 集合在 datapack 加载后不再变化，结果按 Item 永久缓存，无需失效逻辑。
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

    /** @return 元素符号；非元素材料返回 null */
    public static String resolve(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        String symbol = CACHE.computeIfAbsent(stack.getItem(), BadgeResolver::compute);
        return symbol.isEmpty() ? null : symbol;
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
                .map(ELEMENTS::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("");
    }
}
