# 共用组件约定

新页面先查本清单；已有组件直接复用，确需新增的共用组件放入 core/design，不在 feature 中复制同类实现。页面业务状态、数据读取和领域专属弹层仍由 feature 管理。

| 用途 | 共用组件 | 约定 |
| --- | --- | --- |
| 统计数字 | StatCell / StatRow / StatData | 标签弱、数值强、单位小字；每行最多三格，超过自动换行；日期等文本用 isText |
| 搜索 | SearchField | 44dp，内嵌占位、搜索图标、非空时清除；保留调用方查询长度与异步行为 |
| 单选筛选 | FilterChips | 32dp，横向滚动，选中青绿描边与淡底；使用可访问选中语义 |
| 普通表单 | AppTextField / AppChoice | 标签、底色与间距按共用 FormControls |
| 短表单/信息 | WritingSheet(compact=true) | 随内容收起，最大 92%，不做两段吸附；内容必须去掉 fillMaxSize / 填满的 weight，使用有限行数与滚动；footer 固定保存动作 |
| 长内容弹层 | WritingSheet（默认） | 原 70/92% 两段拖拽和键盘行为 |
| 空状态 | EmptyState | 居中主文案、副文案，可选图标/文字动作；避免同时显示两条同义空状态 |
| 页面导航 | ToolRow / ListRow | 图标、标题和副行；不要用整宽按钮充当页面导航 |
| 提交动作 | Button / AppSecondaryButton | 保存、导入、快照、清理确认等真实操作可使用按钮 |
| 分组 | PanelGroup | 沿用主题与间距刻度 |

所有共用组件使用 MaterialTheme.colorScheme，兼容深浅主题；不在组件里硬编码应用的深色或青绿值。新增图标沿用 WritingIcons 描边体系。历史布局保留 CenterMessage 供加载提示使用，新空状态优先 EmptyState。
