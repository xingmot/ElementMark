package com.xingmot.elementmark.client;

import com.xingmot.elementmark.ConfigLoader;
import com.xingmot.elementmark.ConfigLoader.Corner;

import net.minecraft.client.gui.Font;

/**
 * 角标定位的唯一权威实现。
 *
 * <p><b>存在意义</b>：角标的绘制入口只有一条——{@code ItemRenderer.render} 末尾的 mixin，
 * 物品栏、容器、以及配置界面的样例预览全都走它。所以"预览与实机一致"不靠两处对齐参数，
 * 而是靠<b>根本不写第二份绘制代码</b>。本类只负责回答"画在哪、画多大"，
 * 由 {@code ElementMarkClient} 负责把它映射进 3D 矩阵空间。
 *
 * <p><b>坐标约定</b>：一律用"字形单位"（glyph unit，即 {@code font.width()} 所用的单位），
 * 原点取<b>物品图标的左上角</b>，x 向右、y 向下，取值范围 {@code [0, ICON_SIZE]}。
 * 之所以锚在图标左上角，是因为它同时是 3D 矩阵空间（由 {@code ElementMarkClient}
 * 用 {@code translate} 把矩阵原点搬到这里）与 GUI 空间（直接加图标屏幕坐标）的公共参照点。
 *
 * <p><b>字号如何决定</b>：最终字号 = {@link #computeFitScale 自动适配} × 用户的
 * {@code font_scale}。两者<b>必须一起进布局</b>——靠右/靠下对齐的锚点依赖最终渲染宽度，
 * 只乘在渲染字号上而漏进位置计算，文字就会围绕错误的点缩放。
 */
public final class BadgePlacement {

    /**
     * 图标边长（字形单位）。
     *
     * <p>物品在 GUI 里恒占 16×16 屏幕像素（外层 {@code GuiGraphics.renderItem} 施加
     * {@code scale(16,16,16)}）；本模组在矩阵里再乘 {@code SCALE = 0.05}，
     * 于是 1 字形单位 = 16 × 0.05 = 0.8 屏幕像素，16 / 0.8 = 20 字形单位。
     */
    public static final float ICON_SIZE = 20.0F;

    /**
     * 缩写相对图标边缘的内边距（字形单位）。
     * 这是"看起来贴边但不压边"的经验值。
     */
    public static final float PAD = 1.0F;

    /**
     * 字形竖直度量：{@code ASCENT} = 字形框顶边到基线，{@code DESCENT} = 基线到字形框底边，
     * 二者之和即 {@code lineHeight = 9}。原版默认字体对数字、字母、CJK 一视同仁。
     *
     * <p><b>y 是字形框顶边，不是基线。</b>依据：{@code Font.drawInBatch} 的 y 经
     * {@code drawInternal → renderText → Font$StringRenderOutput.accept} 一路原样传递
     * （只额外加阴影偏移），最终进入 {@code Font.renderChar}。同一段字节码里可交叉验证——
     * 删除线画在 {@code y + 4.5}（= lineHeight 的一半，正是字形竖直中部）、
     * 下划线画在 {@code y + 9.0}（= lineHeight）。把 y 当基线则这两个数字都解释不通。
     *
     * <p>于是字形实际占据 {@code [y, y + lineHeight]}，基线落在 {@code y + ASCENT}；
     * 缩放 t 倍后占据 {@code [y, y + lineHeight * t]}。
     */
    public static final float ASCENT = 7.0F;
    public static final float DESCENT = 2.0F;

    /** 字形框总高（{@code ASCENT + DESCENT} = 9）。滚动行的高度取它 */
    public static final float LINE_HEIGHT = ASCENT + DESCENT;

    /**
     * 一个 CJK / 全角字符的步进宽度（字形单位）。
     * 原版默认字体里恰好等于行高 {@code 9}，因为字形是 9×9 的方格。
     */
    public static final float CJK_ADVANCE = LINE_HEIGHT;

    /**
     * "文字最小能到什么程度"的度量基准：<b>几个全角字</b>。
     *
     * <p>度量依据：字号小到「{@code 0.9} 倍下的三个全角字」就是可读的底线，
     * 再小就只是勉强辨认了。这个数字直接决定 {@link #MIN_FIT_SCALE} 与
     * {@link ConfigLoader#FONT_SCALE_MIN} 两者相乘后的绝对下限。
     */
    public static final int READABLE_GLYPHS = 3;

    /**
     * 自动适配（{@link #computeFitScale}）的下限，也是"文字最小能到什么程度"的那把尺。
     *
     * <p>值 = {@code 可用宽度 18 / 三个全角字 27 ≈ 0.667}：字号缩到这一步时，
     * 三个 CJK 字恰好填满图标可用宽度。比这更长的文本
     * <b>不再继续缩小</b>，改为 {@link Placement#scrolling 滚动显示}——
     * 缩成看不清的一团不如让它动起来。所以"贴角落"与"滚动"的分界线就是
     * <b>三个全角字</b>：三个及以内贴角落，四个以上搬到上/下一行滚动。
     * 具体怎么动（左右摆动 / 单向循环）由配置项 {@code scroll} 决定，
     * 本类只回答"要不要动、动在哪一行"。
     *
     * <p>它与配置项下限 {@link ConfigLoader#FONT_SCALE_MIN}（0.9）是<b>两个独立的量</b>：
     * 前者管"自动适配能压多扁"，后者管"用户允许把字调多小"，两者相乘才是屏幕上真实的字号。
     * 三个全角字在两个字都取最小值时 = {@code 0.667 × 0.9 ≈ 0.6}，那就是本模组文字的
     * 绝对可读性下限。
     */
    public static final float MIN_FIT_SCALE = (ICON_SIZE - PAD * 2.0F) / (CJK_ADVANCE * READABLE_GLYPHS);

    /**
     * 滚动显示的横向窗口宽度（字形单位）。
     *
     * <p>取图标<b>框内</b>的可用宽度 {@code ICON_SIZE - PAD * 2}，与
     * {@link #computeFitScale} 的 {@code available} 是同一个值：这样"刚好贴进角落的
     * 三个全角字"与"滚动行"占的是同一段横向区间，由静态切成滚动时横向不会跳一下。
     *
     * <p>窗口不能再宽：再宽就会压到相邻槽位上的物品（图标在 GUI 里恒占 16 屏幕像素，
     * 槽位之间只留约 2 像素空隙）。
     */
    public static final float SCROLL_WINDOW = ICON_SIZE - PAD * 2.0F;

    /**
     * 滚动时每侧额外允许露出的宽度（字形单位）——裁剪范围比绘制窗口宽出来的量。
     *
     * <p>取 {@link #PAD}，于是 {@code PAD + SCROLL_WINDOW + PAD = ICON_SIZE}：<b>裁剪范围恰好
     * 等于整块图标</b> {@code [0, ICON_SIZE]}。这不是凑出来的数字，而是
     * {@link #SCROLL_WINDOW} 的反面定义——窗口本就是"框内可用宽度"，把两侧那么宽的边距加回去
     * 就是整框，也正是"文字不越出自己那块格子"的天然边界。
     *
     * <p><b>为什么需要放宽</b>：绘制窗口 {@link #SCROLL_WINDOW} 只有 18 字形单位，而滚动行的字号
     * 被钉在 {@link #MIN_FIT_SCALE}（0.667，再缩就看不清），于是 0.9 档下画面里只装得下约
     * {@code 18 / 5.4 ≈ 3.3} 个字形位置（其中完整 3 个）。想多看点字只能让窗口更宽，而窗口已经
     * 贴到框内边界，唯一能借的就是"允许压出框外一点"，框外恰好还留着 {@link #PAD} 那么宽的边距。
     *
     * <p><b>为什么不能再宽</b>：图标在 GUI 里恒占 16 屏幕像素、槽位间距 18 像素，相邻槽位之间
     * 只剩约 2 屏幕像素空隙。而裁剪判据是"字形<b>中心</b>落在裁剪范围内"
     * （见 {@code ElementMarkClient#drawGlyphs}），字形还会再探出半格——折算到屏幕上是
     * 0.9/1.0/1.1 三档各约 2.2/2.4/2.6 像素。取 {@link #PAD} 时裁剪右缘正好压在图标右缘上，
     * 那半格越过约 2 像素的空隙后只剩 0.2/0.4/0.6 像素，属<b>亚像素级</b>（且只在某个字中心
     * 恰好压线的瞬间），看不出压边；再往外放，露出来的就是相邻槽位上的物品了。
     *
     * <p>这 0.2 ~ 0.6 像素无法再压掉：要归零就得把裁剪范围收得比图标还窄
     * （{@code CLIP_SLACK < 0.2}），那等于放弃这次放宽的收益。{@link #PAD} 是"裁剪范围不越过图标"
     * 这条线上最宽的值。
     *
     * <p><b>只放宽裁剪，不放宽绘制窗口</b>：文字移动的起止仍按 {@link #SCROLL_WINDOW} 算，
     * 于是"开头/末尾对齐窗口边缘"的语义不变，只是边缘那几个字能多露出一点。
     */
    public static final float CLIP_SLACK = PAD;

    /**
     * 需要滚动的<b>最小溢出量</b>（字形单位）。溢出量 = 文本渲染宽 − {@link #SCROLL_WINDOW}，
     * 小于它时就<b>不动</b>，改为静态居中、把内容整个摆在那里。
     *
     * <p>取一个全角字的步进宽度 {@link #CJK_ADVANCE}（9），判据是
     * <b>"溢出不到一个字宽"</b>：此时文字左右各只压出半格以内，那半格正好落在图标与相邻
     * 槽位之间的空隙里（PAD 之外仍有约 2 屏幕像素），四个字基本完整可读——
     * 滚动的收益（让某一个字完整露出来）抵不过代价（整行一直在动、反而总有一个字在外）。
     *
     * <p>两种方式（{@code scroll:sway} 与 {@code scroll:loop}）共用这条判据——"要不要动"
     * 只取决于放不放得下，与"怎么动"无关，否则同一条目会因为用户换个方式就忽然开始动。
     *
     * <p><b>不动的这条路径不套窗口裁剪</b>（见 {@code ElementMarkClient#drawGlyphs}）：
     * 静态居中的语义就是"内容基本装得下、两侧各压出不到半格"，那半格是有意留在窗外的；
     * 若也裁，首尾两字的中心会落在窗口外而被整块吃掉。
     *
     * <p>这条判据直接影响的就是"四个字"这一档：{@code font_scale} 在 0.9 ~ 1.1 时，
     * 四个全角字的溢出量只有 3.6 ~ 6.6 字形单位，全都小于本值 9，因此<b>静止居中不动</b>。
     * 到五个字才有 9 字形单位以上的溢出，那时才真正需要动起来。
     */
    public static final float SCROLL_MIN_SWING = CJK_ADVANCE;

    /**
     * 一次定位的完整结果。全部为<b>字形单位</b>口径，相对图标左上角。
     *
     * @param drawX     文字左缘的 x；滚动模式下这是<b>窗口的左缘</b>（窗口内文字的位置由时间决定）
     * @param drawY     字形框顶边的 y（{@code drawInBatch} 的 y 参数即顶边，不是基线）
     * @param textScale 最终字号系数，<b>已含</b>用户的 {@code font_scale}；
     *                  调用方直接用它缩放矩阵即可，不要再乘一次
     * @param scrolling 是否改用滚动显示。为 {@code true} 时 {@code drawX/drawY} 给出的是
     *                  "框内那条横向独占行"的窗口左缘与字形框顶边，
     *                  文字在 {@link #SCROLL_WINDOW} 宽的窗口里移动——左右往返摆动或
     *                  单向循环，由配置项 {@code scroll} 决定
     */
    public record Placement(float drawX, float drawY, float textScale, boolean scrolling) {}

    private BadgePlacement() {}

    /**
     * 计算角标应画在哪里、多大。
     *
     * <p><b>关键：缩放锚点</b>。绘制矩阵是在"平移后的原点"上做缩放的，所以：
     * <ul>
     *   <li>靠左对齐：锚点 = 文字左缘目标位置，缩放后文字向右伸展，天然贴左；</li>
     *   <li>靠右对齐：锚点必须是<b>缩放后</b>的右缘位置，即
     *       {@code 右边界 - width * textScale}（用未缩放的 {@code width} 会让文字整段偏左，
     *       长文本触发自动缩放时甚至越出图标左边）。</li>
     * </ul>
     * 纵向同理，且这里有个最易搞反的点——{@code drawInBatch} 的 y 是<b>字形框顶边</b>：
     * <ul>
     *   <li>靠上对齐：字形框顶边落在 {@code top}，故 {@code y = top}；</li>
     *   <li>靠下对齐：字形框底边之下还留着 {@code DESCENT} 的空槽（只有 g/j/p/q/y
     *       这类下伸字母才用得到），而拉丁字母与 CJK 的<b>可见底沿就是基线</b>，
     *       所以要让基线落在 {@code bottom}，即 {@code y = bottom - ASCENT * textScale}
     *       （写成 {@code bottom - DESCENT * textScale} 会整体偏低
     *       {@code ASCENT - DESCENT = 5} 个字形单位）。</li>
     * </ul>
     *
     * <p>由于 {@code textScale} 含有 {@code font_scale}，用户改字号时这四个锚点会
     * 同步跟随，文字始终贴住所选角落，而不是围绕左上角"长大"。
     *
     * <p><b>放不下时改滚动，而不是继续缩小。</b>缩小是有下限的
     * （{@link #MIN_FIT_SCALE}，即三个全角字刚好填满可用宽度）：文字比这更长时不动字号，
     * 而是<b>脱离角落对齐、改为在图标框内横向独占一行地滚动</b>。行落在框内的
     * 上半还是下半由角落的纵向分量决定（上排角落 → 上半部分，下排角落 → 下半部分），
     * 横向则一律占满框内可用宽度（{@link #SCROLL_WINDOW}）。"怎么动"（左右摆动 / 单向循环）
     * 不在这里决定——本类只回答"要不要动、动在哪一行"，动作方式见配置项 {@code scroll}。
     *
     * @param font   用于取文本宽度（字体度量本就是字形单位）
     * @param text   缩写文本
     * @param corner 目标角落
     * @return 以"图标左上角为原点、单位字形单位、y 向下"的定位结果
     */
    public static Placement compute(Font font, String text, Corner corner) {
        float width = font.width(text);
        float available = ICON_SIZE - PAD * 2.0F;

        // 即便把自动适配压到下限，这段文字仍贴不进角落 → 字数太多，再缩只会缩成看不清的一团，
        // 此时字号就地停在下限，改由滚动把完整内容展示出来。
        boolean scrolling = width * MIN_FIT_SCALE > available;

        // 最终字号 = 自动适配 × 用户系数。必须在这里就合成，
        // 否则下面的对齐锚点用的还是旧尺寸，见上面对"缩放锚点"的说明。
        float textScale = (scrolling ? MIN_FIT_SCALE : computeFitScale(width)) * ConfigLoader.fontScale();
        float textWidth = width * textScale;

        if (scrolling) {
            // 在<b>图标框内</b>横向独占一行：上排角落走框内上半部分，下排角落走下半部分。
            // 横向窗口从 PAD 取到 ICON_SIZE - PAD（= SCROLL_WINDOW），起点即 drawX = PAD。
            // 窗口内文字的实际位置由时间决定，由 ElementMarkClient 在绘制时逐帧计算。
            float y = corner.vertical == 1
                    ? ICON_SIZE - PAD - LINE_HEIGHT * textScale   // 下：字形框底边贴框内下缘
                    : PAD;                                        // 上：字形框顶边贴框内上缘
            return new Placement(PAD, y, textScale, true);
        }

        // 图标内可用的绘制区域（相对图标左上角，字形单位）
        float left = PAD;
        float right = ICON_SIZE - PAD;

        // 横向：靠左贴 left；靠右则让【缩放后】的文字右缘落在 right
        float x = corner.horizontal == 1 ? right - textWidth : left;
        // 纵向：上对齐让字形框顶边落在 top；下对齐让可见底沿（= 基线）落在 bottom
        float y = corner.vertical == 1 ? ICON_SIZE - PAD - ASCENT * textScale : PAD;

        return new Placement(x, y, textScale, false);
    }

    /**
     * 自动适配系数：使文本宽度不超出图标可用宽度。
     *
     * <p>只约束宽度——原版行高 9 字形单位远小于可用高度 18，且缩放为等比，高度约束恒成立。
     * 若文本刚好撑满可用宽度，靠左与靠右会得到同一个 x，这是几何必然。
     *
     * <p>入参收的是<b>已经量好的宽度</b>而不是文本本身：调用方 {@link #compute} 手上本就有它，
     * 让本方法自己再 {@code font.width()} 一次等于每帧白算一遍。
     *
     * @return 1.0 表示不需缩放；否则返回 &lt; 1 的等比系数，下限 {@link #MIN_FIT_SCALE}
     */
    public static float computeFitScale(float width) {
        float available = ICON_SIZE - PAD * 2.0F;
        if (width <= available) {
            return 1.0F;
        }
        return Math.max(MIN_FIT_SCALE, available / width);
    }
}
