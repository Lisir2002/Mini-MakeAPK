package com.make.apk.ui.components

/**
 * 组件注册表——全站自绘组件的**单一事实来源**（§优化·第三点）。
 *
 * ## 动机
 * 用户诉求："每一个组件在新增时都需要自动注册好一个位置接口，如果后续有需要同样的组件接入直接用就行，
 * 就怕同样类型的组件在一个软件里好多种样式，影响用户体验。"
 *
 * 核心约束：**每种组件类型只有一套规范样式、一个注册槽位（"位置接口"）**。新增组件必须在此登记，
 * 后续同类组件直接引用同一登记项，杜绝复制出多套样式（碎片化）。
 *
 * ## 形态
 * 编译期固定的 `entries` 列表——每新增一款自绘组件，必须在此追加一条 [Entry]，
 * 并复用既有 [Slot]（同类共用）或显式登记新槽位。测试 [MmaComponentRegistryTest]
 * 断言"每类型仅一条登记、每条均有规范样式引用"，作为防回潮门禁。
 *
 * 样式一律以 [ControlShapes] / [com.make.apk.ui.theme.MmaDesign] 令牌引用，**不内联裸值**
 * （与全站"禁止硬编码 UI"铁律一致）。
 */
object MmaComponentRegistry {

    /** 已登记的自绘组件类型。 */
    enum class Kind {
        /** 下拉字段 + 分段式菜单。 */
        Dropdown,

        /** 弹窗。 */
        Dialog,

        /** 开关。 */
        Switch,

        /** 单选 / 分段选择。 */
        Choice,

        /** 芯片（筛选 / 预置）。 */
        Chip,

        /** 图标动作按钮（行内编辑 / 删除等）。 */
        IconAction,

        /** 数字步进器。 */
        Stepper,

        /** 顶 / 底 Tab 行。 */
        Tabs,

        /** 应用内通知气泡。 */
        Notice,

        /** 页面骨架（三段式槽位）。 */
        Screen,

        /** 顶栏。 */
        TopBar,

        /** 底栏。 */
        BottomBar,

        /** 文字按钮。 */
        TextButton,
    }

    /**
     * 注册槽位（"位置接口"）：组件在页面中允许挂载的标准位置。
     * 同类组件必须占用同一槽位，禁止在槽位外另起私有样式。
     */
    enum class Slot {
        /** 表单字段（下拉 / 输入框 / 步进器）。 */
        Field,

        /** 浮层（弹窗 / 通知气泡）。 */
        Overlay,

        /** 开关 / 单选。 */
        Toggle,

        /** 图标动作按钮。 */
        Action,

        /** 顶栏 / 底栏 / Tab 导航。 */
        Navigation,

        /** 文字按钮。 */
        Button,

        /** 页面骨架表面。 */
        Surface,
    }

    /**
     * 单条登记项：类型 + 规范槽位 + 规范样式令牌引用。
     *
     * @param kind 组件类型（每类型唯一）。
     * @param slot 挂载槽位（同类复用）。
     * @param styleRef 规范样式说明（指向 [ControlShapes] / [com.make.apk.ui.theme.MmaDesign]
     *   令牌，不内联裸值；非规范样式不得出现在此清单之外）。
     */
    data class Entry(
        val kind: Kind,
        val slot: Slot,
        val styleRef: String,
    )

    /** 全量登记（编译期固定，新增组件必须在此追加一条）。 */
    val entries: List<Entry> = listOf(
        Entry(
            Kind.Dropdown,
            Slot.Field,
            "ControlShapes.fieldClosed(12)/fieldOpen(16) + menuItemHeight(48) + menuItemIcon(20) + 选中 tertiaryContainer",
        ),
        Entry(
            Kind.Dialog,
            Slot.Overlay,
            "ControlShapes.dialog(28) + elevation2(6dp) + dialogMaxWidth(320)",
        ),
        Entry(
            Kind.Switch,
            Slot.Toggle,
            "ControlShapes.pill + switchTrackWidth(52)/Height(32)/Thumb(24)",
        ),
        Entry(
            Kind.Choice,
            Slot.Toggle,
            "ControlShapes.segmentPiece(12) + radioSize(20)/radioStroke(2)",
        ),
        Entry(
            Kind.Chip,
            Slot.Action,
            "ControlShapes.segmentPiece(12) + chipHeight(36)",
        ),
        Entry(
            Kind.IconAction,
            Slot.Action,
            "ControlShapes.segmentPiece(12) + iconButtonSize(40) + menuItemIcon(20)",
        ),
        Entry(
            Kind.Stepper,
            Slot.Field,
            "ControlShapes.segmentPiece(12) + iconButtonSize(40)",
        ),
        Entry(
            Kind.Tabs,
            Slot.Navigation,
            "ControlShapes + tabIndicatorHeight(3)",
        ),
        Entry(
            Kind.Notice,
            Slot.Overlay,
            "ControlShapes.group(16) + elevation2(6dp) + labelLarge + 3s 自动消失 / 点击即隐藏",
        ),
        Entry(
            Kind.Screen,
            Slot.Surface,
            "MmaScreen Scaffold + chrome.topBarHeight(52)/bottomBarHeight(56)",
        ),
        Entry(
            Kind.TopBar,
            Slot.Navigation,
            "MmaTopBar + chrome.topBarHeight(52)",
        ),
        Entry(
            Kind.BottomBar,
            Slot.Navigation,
            "MmaBottomBar + chrome.bottomBarHeight(56)",
        ),
        Entry(
            Kind.TextButton,
            Slot.Button,
            "ControlShapes + buttonHeight(40) + labelLarge",
        ),
    )

    /** 按类型查登记项（每类型唯一）。 */
    fun entryOf(kind: Kind): Entry = entries.first { it.kind == kind }
}
