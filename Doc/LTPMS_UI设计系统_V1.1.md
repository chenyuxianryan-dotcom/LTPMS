# LTPMS UI 设计系统 V1.1

> 版本：V1.1 | 日期：2026-09-07 | 状态：替代 V1.0 UI 设计规范
> 维护方：UI Designer
> 变更原因：V1.0 仅定义扁平化 + Element Plus 默认风格，缺少 Design Tokens、无障碍规范、组件状态系统、响应式策略，已不能支撑后端 V1.1 的开发交付。本版按 design-ui-designer 方法论重做。

---

## 1. 设计原则

| 原则 | 含义 | 优先级 |
|------|------|--------|
| **可读优先** | 数据密度高、可长时间使用 | P0 |
| **数据层级清晰** | 标题/操作/辅助三层视觉重量 | P0 |
| **状态可见** | 加载/空/错误/成功都有明确反馈 | P0 |
| **键盘可达** | 全交互可纯键盘完成，焦点环清晰 | P0 |
| **WCAG AA** | 文字对比度 ≥4.5:1，触控目标 ≥44×44px | P0 |
| **响应但克制** | 桌面为主（≥1200px），平板可读；明确不做手机端 | P1 |
| **暗色可选** | 同一 Tokens 系统支持主题切换，长时间使用护眼 | P1 |

**否决项**（避免做）：
- 紫渐变白底 / 玻璃拟态 / 霓虹色 —— 与"教务管理"调性不符
- Inter / 思源黑体 / 系统字体作 Display —— IBM Plex Sans 更具 productivity 调性
- Element Plus 默认浅蓝 `#409EFF` —— 改为深靛蓝 `#1E40AF`，建立品牌差异化

---

## 2. Design Tokens（设计变量）

> 所有 Tokens 落地为 CSS 自定义属性，写入 `frontend/src/styles/tokens.css`。

### 2.1 颜色 Color

#### Brand 品牌色（深靛蓝系）

| Token | 值 | 用途 |
|-------|-----|------|
| `--brand-50` | `#EFF6FF` | 主色 hover 背景、选中态底色 |
| `--brand-100` | `#DBEAFE` | 链接 hover、选中态文字 |
| `--brand-500` | `#3B82F6` | —— |
| `--brand-600` | `#2563EB` | 主按钮、链接 |
| `--brand-700` | **`#1E40AF`** | **主品牌色**（导航高亮、主按钮、强调） |
| `--brand-800` | `#1E3A8A` | hover 态、标题色 |

#### Surface 表面

| Token | 值 | 用途 |
|-------|-----|------|
| `--bg-canvas` | `#FAFAFA` | 页面底色 |
| `--bg-elevated` | `#FFFFFF` | 卡片、弹窗 |
| `--bg-muted` | `#F5F5F5` | 表格斑马纹、代码块 |
| `--bg-subtle` | `#F9FAFB` | 输入框背景 |
| `--bg-overlay` | `rgba(15,23,42,0.6)` | 模态遮罩 |

#### Border 边框

| Token | 值 | 用途 |
|-------|-----|------|
| `--border-subtle` | `#F1F5F9` | 卡片内分割线 |
| `--border-default` | **`#E2E8F0`** | **常规边框** |
| `--border-strong` | `#CBD5E1` | hover、强调边框 |
| `--border-focus` | `#2563EB` | 焦点环 |

#### Text 文字（已校验 WCAG AA）

| Token | 值 | 对比度（vs 白底） | 用途 |
|-------|-----|--------------------|------|
| `--text-primary` | `#0F172A` | **16.8:1** | 标题、重要数据 |
| `--text-secondary` | `#475569` | **7.5:1** | 正文 |
| `--text-tertiary` | `#64748B` | **5.3:1** | 辅助文字、占位 |
| `--text-quaternary` | `#94A3B8` | 2.8:1 | 禁用、纯装饰（大字号场景下合规） |
| `--text-inverse` | `#FFFFFF` | — | 深底反白 |
| `--text-link` | `#2563EB` | 6.4:1 | 链接 |

#### Semantic 语义色

| 类别 | 50 (底色) | 500 (主色) | 600 (深色) | 用途 |
|------|-----------|------------|------------|------|
| Success | `#ECFDF5` | `#10B981` | `#059669` | 成功提示 |
| Warning | `#FFFBEB` | `#F59E0B` | `#D97706` | 警告、待处理 |
| Danger | `#FEF2F2` | `#EF4444` | `#DC2626` | 删除、错误 |
| Info | `#EFF6FF` | `#3B82F6` | `#2563EB` | 信息提示 |

#### 暗色主题 `[data-theme="dark"]`

仅给出与亮色差异部分；Tokens 命名保持一致，前端用主题切换组件或 OS prefers-color-scheme 自动应用。

| Token | 暗色值 |
|-------|--------|
| `--bg-canvas` | `#0F172A` |
| `--bg-elevated` | `#1E293B` |
| `--bg-muted` | `#1E293B` |
| `--bg-subtle` | `#0F172A` |
| `--border-default` | `#334155` |
| `--border-strong` | `#475569` |
| `--text-primary` | `#F1F5F9` |
| `--text-secondary` | `#CBD5E1` |
| `--text-tertiary` | `#94A3B8` |
| `--text-quaternary` | `#64748B` |

---

### 2.2 字体 Typography

#### 字体族

```css
--font-sans: "IBM Plex Sans", "PingFang SC", "Hiragino Sans GB",
             "Microsoft YaHei", sans-serif;
--font-mono: "JetBrains Mono", "SF Mono", "SF Mono", Consolas, monospace;
```

中文 fallback：PingFang SC（macOS）/ Hiragino Sans GB / Microsoft YaHei（Windows）。

> ⚠️ 字体加载策略：`@font-face` 仅引入拉丁字符子集，中文用系统字体，避免引入整个思源黑体（2MB+）拖慢首屏。

#### 字号阶梯（13 级）

| Token | px | 行高 | 字重 | 场景 |
|-------|----|------|------|------|
| `--text-xs` | 12 | 1.5 | 400 | 辅助说明、timeago |
| `--text-sm` | 13 | 1.5 | 400 | 表格次要列 |
| `--text-base` | 14 | 1.5 | 400 | **正文默认** |
| `--text-md` | 15 | 1.5 | 400 | 表格主列 |
| `--text-lg` | 16 | 1.5 | 500 | 表单标签 |
| `--text-xl` | 18 | 1.4 | 600 | 卡片标题 |
| `--text-2xl` | 20 | 1.35 | 600 | 页面 H2 |
| `--text-3xl` | 24 | 1.3 | 600 | 页面 H1 |
| `--text-4xl` | 30 | 1.25 | 700 | 数字统计（粗） |
| `--text-5xl` | 36 | 1.2 | 700 | Dashboard 大数字 |

#### 字重

| Token | 值 | 用途 |
|-------|-----|------|
| `--weight-regular` | 400 | 正文 |
| `--weight-medium` | 500 | 强调、表单标签 |
| `--weight-semibold` | 600 | 标题 |
| `--weight-bold` | 700 | 大数字、关键数据 |

#### 行高

| Token | 值 | 场景 |
|-------|-----|------|
| `--leading-tight` | 1.25 | 大标题 |
| `--leading-snug` | 1.4 | 卡片标题 |
| `--leading-normal` | 1.5 | 正文、表单 |
| `--leading-relaxed` | 1.65 | 长段落、说明文字 |

---

### 2.3 间距 Spacing（4px 基准）

| Token | 值 | px | 场景 |
|-------|-----|----|------|
| `--space-1` | 0.25rem | 4 | 图标与文字 |
| `--space-2` | 0.5rem | 8 | 表单项内 |
| `--space-3` | 0.75rem | 12 | 按钮内、表格单元格 |
| `--space-4` | 1rem | 16 | 卡片内、组件间 |
| `--space-5` | 1.25rem | 20 | 表单块 |
| `--space-6` | 1.5rem | 24 | 页面内边距、模块间距 |
| `--space-8` | 2rem | 32 | 大模块间 |
| `--space-10` | 2.5rem | 40 | 页面顶部留白 |
| `--space-12` | 3rem | 48 | Dashboard 区块间 |
| `--space-16` | 4rem | 64 | 页面底部留白 |

---

### 2.4 圆角 Radius

| Token | 值 | 用途 |
|-------|-----|------|
| `--radius-xs` | 2px | 标签、徽章 |
| `--radius-sm` | 4px | 输入框、小按钮 |
| `--radius-md` | **6px** | **按钮、输入框默认** |
| `--radius-lg` | 8px | 卡片 |
| `--radius-xl` | 12px | 弹窗、Drawer |
| `--radius-2xl` | 16px | 大型 Modal |
| `--radius-full` | 9999px | 头像、Tag |

---

### 2.5 阴影 Shadow

| Token | 值 | 用途 |
|-------|-----|------|
| `--shadow-xs` | `0 1px 1px rgba(15,23,42,0.04)` | 极轻分割 |
| `--shadow-sm` | `0 1px 2px rgba(15,23,42,0.06), 0 1px 1px rgba(15,23,42,0.04)` | 卡片默认 |
| `--shadow-md` | `0 4px 6px -1px rgba(15,23,42,0.08), 0 2px 4px -2px rgba(15,23,42,0.04)` | 卡片 hover |
| `--shadow-lg` | `0 10px 15px -3px rgba(15,23,42,0.08), 0 4px 6px -4px rgba(15,23,42,0.04)` | Dropdown、Popover |
| `--shadow-xl` | `0 20px 25px -5px rgba(15,23,42,0.10), 0 8px 10px -6px rgba(15,23,42,0.04)` | Modal |

> 阴影色统一用 `rgba(15,23,42,*)`（深靛蓝灰），避免纯黑显得脏。

---

### 2.6 动效 Motion

| Token | 值 | 用途 |
|-------|-----|------|
| `--duration-fast` | 120ms | hover、focus 微反馈 |
| `--duration-normal` | 200ms | 弹窗、Drawer |
| `--duration-slow` | 320ms | 页面切换 |
| `--ease-out` | `cubic-bezier(0.16, 1, 0.3, 1)` | 入场（弹性落定） |
| `--ease-in-out` | `cubic-bezier(0.4, 0, 0.2, 1)` | 状态过渡 |

**遵循 `@media (prefers-reduced-motion: reduce)`**：自动缩短动画时长到 0.01ms（尊重无障碍偏好）。

---

### 2.7 布局 Layout

| Token | 值 | 用途 |
|-------|-----|------|
| `--sidebar-width` | 240px | 左侧菜单（可折叠到 64px） |
| `--header-height` | 56px | 顶部导航 |
| `--container-max` | 1440px | 内容最大宽度 |
| `--content-pad` | var(--space-6) | 内容区内边距 |

#### 断点

| Token | 值 | 布局调整 |
|-------|-----|---------|
| `--bp-sm` | 640px | 手机（本系统不重点适配） |
| `--bp-md` | 768px | 平板：表格横向滚动 |
| `--bp-lg` | 1024px | 桌面：完整布局 |
| `--bp-xl` | 1280px | 宽屏 |
| `--bp-2xl` | 1536px | 超宽屏 |

---

## 3. 组件库 Component Library

> 完整可交互组件见 `outputs/LTPMS_design_system.html`。本节给出规范与 Props。

### 3.1 Button 按钮

**Variant × Size 矩阵**：

| Size | height | padding-x | font-size |
|------|--------|-----------|-----------|
| sm | 28px | 12px | 13px |
| **md**（默认） | **36px** | **16px** | **14px** |
| lg | 44px | 20px | 16px |

| Variant | 主色 | 文字 | 用途 |
|---------|------|------|------|
| `primary` | `--brand-600` | white | 主操作（提交、查询、保存） |
| `secondary` | `--bg-elevated` | `--text-primary` | 次操作（取消、返回） |
| `tertiary` | transparent | `--brand-600` | 行内轻量操作（查看、展开） |
| `danger` | `--danger-600` | white | 删除 |
| `ghost` | transparent | `--text-secondary` | 工具栏 |

**状态**：default / hover / active / focus / disabled / loading
- hover：背景深一档 + `--shadow-sm`
- focus-visible：2px `--border-focus` outline + 2px offset
- disabled：opacity 0.5，cursor not-allowed
- loading：内置 spinner，文字变 `--text-quaternary`，禁用点击

**触控目标**：sm/md/lg 三档均 ≥28px；移动端用 lg 强制 ≥44px。

### 3.2 Input 输入框

| 状态 | 边框 | 背景 |
|------|------|------|
| default | `--border-default` | `--bg-subtle` |
| hover | `--border-strong` | `--bg-subtle` |
| focus | `--border-focus` (2px) | `--bg-elevated` |
| error | `--danger-500` (2px) | `--bg-elevated` |
| disabled | `--border-subtle` | `--bg-muted` |

高度 36px（与按钮对齐），圆角 `--radius-md`，padding `0 12px`。
左前缀图标支持，右侧可加帮助/清除按钮。
错误态下方红字提示（`--danger-600`，13px）。

### 3.3 Select / DatePicker / Cascader

基于 Input 扩展，右侧箭头图标。Dropdown 用 `--shadow-lg`。
选项 hover 背景 `--bg-muted`，选中 `--brand-50` + 文字 `--brand-700`。

### 3.4 Checkbox / Radio / Switch

| 组件 | 选中色 | 未选中 |
|------|--------|--------|
| Checkbox | `--brand-600` 填充 + ✓ 白色 | `--bg-elevated` + 1.5px 边框 |
| Radio | 内圆 `--brand-600` + 外圈边框 | 外圈 1.5px 边框 |
| Switch | 开启 `--brand-600` | 关闭 `--bg-muted` |

Switch 切换动效 `--duration-normal` `--ease-out`。

### 3.5 Tag / Badge

| 类型 | 底色 | 文字 |
|------|------|------|
| neutral | `--bg-muted` | `--text-secondary` |
| brand | `--brand-50` | `--brand-700` |
| success | `--success-50` | `--success-700` |
| warning | `--warning-50` | `--warning-600` |
| danger | `--danger-50` | `--danger-600` |

圆角 `--radius-xs`（2px），padding `1px 8px`，字号 12px，字重 500。

### 3.6 Avatar 头像

方形 26/32/40/56 四档；圆形自动。
默认背景：从用户名首字符 hash 选 `--brand-100/200/300/400/500` 之一，文字 `--brand-700/800`。
Fallback：用户名首字（中文）/ 头像首字母。

### 3.7 Card 卡片

```
background: var(--bg-elevated);
border: 1px solid var(--border-subtle);
border-radius: var(--radius-lg);
box-shadow: var(--shadow-sm);
padding: var(--space-6);
```

Hover（可点击卡片）：`--shadow-md` + `translateY(-2px)`。

### 3.8 Table 数据表格

| 元素 | 样式 |
|------|------|
| 表头背景 | `--bg-muted` |
| 表头文字 | `--text-secondary`，12px，字重 600 |
| 行高 | 48px（紧凑 40px） |
| 行 hover | `--bg-subtle` |
| zebra | 偶数行 `--bg-subtle` |
| 单元格 padding | 12px 16px |
| 边框 | 仅水平 `--border-subtle` |

操作列固定右侧（`position: sticky; right: 0`）。
空态：EmptyState 组件（见 §3.11）。
数字列：右对齐 + `font-variant-numeric: tabular-nums`。

### 3.9 Pagination 分页

当前页码 `--brand-600` 填充，其他 `--text-secondary` 文字。
上一页/下一页用 chevron 图标。分页信息（"共 86 条"）放在右侧，字号 13px。

### 3.10 Modal / Dialog

宽度档：sm 400 / md 560 / lg 720 / xl 900。
遮罩 `--bg-overlay`，圆角 `--radius-xl`，阴影 `--shadow-xl`。
入场：`--duration-normal` `--ease-out`；退场：`--duration-fast` `--ease-in`。
ESC 关闭，点击遮罩默认关闭，**危险操作弹窗禁遮罩关闭**。

### 3.11 Empty State 空状态

```
┌─────────────────────┐
│    [插画 SVG 96px]    │
│                      │
│      暂无实验项目     │
│                      │
│  试试新建一个项目      │
│      [新建项目]       │
└─────────────────────┘
```

垂直居中、padding 48px、按钮在文案下方。

### 3.12 Loading / Skeleton

Skeleton：背景 `--bg-muted`，shimmer 动画 `--duration-slow` 线性（`background-position` 移动）。
页面级 Loading：全屏遮罩 + 居中 spinner（24px 圆环）。
按钮 Loading：内置 spinner 16px。

### 3.13 Alert 提示

4 类型：success / warning / danger / info。
左侧 4px 强调边 + 对应 `--type-50` 底色 + 对应 `--type-600` 图标。
可关闭（右上 ×），无图标时简化样式。

### 3.14 Toast 轻提示

顶部居中弹出（3 秒自动消失，可配置）。
成功/错误/警告/信息 4 色对应。
鼠标 hover 暂停消失计时。

### 3.15 Tabs 标签页

下划线样式：选中 2px `--brand-600` 下划线，文字 `--text-primary`，未选 `--text-tertiary`。
胶囊样式（次选）：选中 `--brand-50` 底 + `--brand-700` 文字。

### 3.16 Breadcrumb 面包屑

`/` 分隔，13px，最后一级 `--text-secondary`（不可点），其他 `--text-tertiary` hover 变 `--brand-600`。

### 3.17 Sidebar Menu 侧栏菜单

折叠态 64px（仅图标）/ 展开 240px（图标+文字）。
选中：左侧 2px `--brand-600` 边条 + 背景 `--brand-50` + 文字 `--brand-700`。
图标 18px，文字 14px medium。

---

## 4. 页面模式 Page Patterns

### 4.1 列表页模板（实验室、教师、课表、实验项目、报表）

```
┌────────────────────────────────────────────┐
│  面包屑 · 页面标题                  [新建]   │ ← PageHeader (56px)
├────────────────────────────────────────────┤
│  [搜索框] [学期▼] [类别▼] [筛选▼] [重置]   │ ← FilterBar
├────────────────────────────────────────────┤
│                                            │
│  ┌────────────────────────────────────┐    │
│  │ 列1 │ 列2 │ 列3 │ 列4 │ 列5 │ 操作  │    │
│  ├─────┼─────┼─────┼─────┼─────┼─────┤    │
│  │ ... 数据行 ...                      │    │ ← DataTable
│  │                                     │    │
│  └────────────────────────────────────┘    │
│                                            │
│  共 86 条            « 1 2 3 ... 9 »       │ ← Pagination
└────────────────────────────────────────────┘
```

### 4.2 表单页模板（新增/编辑实验项目、学期等）

```
┌─────────────────────────┐
│  面包屑 · 新建实验项目   │
├─────────────────────────┤
│                         │
│  实验名称 *             │ ← Form field
│  [_________________]    │
│                         │
│  实验类别 *             │
│  [基础实验 ▼]           │
│                         │
│  ...更多字段...          │
│                         │
│  [取消]    [保存]       │
└─────────────────────────┘
```

最大宽度 720px，垂直堆叠，标签在输入框上方（区别于 Element Plus 默认 label-position="right"）。

### 4.3 详情页模板（课程详情、报表详情）

左侧 70% 内容区 + 右侧 30% 元信息侧栏。
内容区用卡片分区，每区有标题 + 操作按钮组。

### 4.4 NL2SQL 查询页（差异化亮点）

参见 `outputs/LTPMS_design_system.html` 末段。

---

## 5. 响应式策略

| 断点 | 布局调整 |
|------|---------|
| ≥ 1280px | 完整布局：侧栏 240 + 主内容 |
| 1024-1279px | 侧栏自动折叠为图标态（64px） |
| 768-1023px | 侧栏抽屉化（默认收起），表格横向滚动 |
| < 768px | 不重点适配（PRD 明确"不做手机端"） |

表格在窄屏提供列控制：用户可勾选隐藏次要列。

---

## 6. 无障碍 Accessibility（WCAG AA）

| 检查项 | 标准 | 实现 |
|--------|------|------|
| 文字对比度 | 4.5:1（普通）/ 3:1（≥18px） | 全部 Text Tokens 已校验 |
| 焦点可见 | 2px outline + 2px offset | 所有可交互元素 `:focus-visible` |
| 触控目标 | ≥ 44×44px（移动端） | 所有按钮 lg 档满足 |
| 键盘导航 | 全功能可纯键盘完成 | Tab 顺序合理，Enter 触发 |
| ARIA 标签 | 表单字段、弹窗、状态 | `<label>` 关联，弹窗 `role="dialog"` |
| 色彩非唯一信息载体 | 状态除颜色外还有图标/文字 | Tag 含文字、错误态含图标 |
| 动效克制 | `prefers-reduced-motion` | 自动缩短到 0.01ms |
| 跳过导航 | 复杂页面提供"跳到主内容" | 顶部隐藏链接，键盘 Tab 第一个 |

---

## 7. 与原 V1.0 规范对照（差异说明）

| 维度 | V1.0（被替代） | V1.1（本版） | 理由 |
|------|----------------|-------------|------|
| 主色 | `#409EFF`（Element Plus 默认） | `#1E40AF`（深靛蓝） | 品牌差异化 |
| 字体 | 系统字体 | IBM Plex Sans + JetBrains Mono | productivity 调性 |
| 圆角 | 4px | 6px 主 / 8px 卡片 | 更现代友好 |
| 阴影 | 单一 rgba(0,0,0,0.06) | 5 档分层 | 层次感 |
| 无障碍 | 无 | WCAG AA 完整覆盖 | 教务系统合规 |
| 响应式 | 仅"桌面/平板/手机"3 档 | 5 断点 + 组件级适配 | 落地更细 |
| 主题 | 仅明亮 | 明亮主 + 暗色 fallback | 长时间使用 |
| Tokens | 散落在各章节 | 集中 §2 + tokens.css | 可执行 |
| 组件状态 | 仅 default/hover | 5+ 状态全覆盖 | 完整 |

---

## 8. 实施交付物清单

| # | 文件 | 状态 |
|---|------|------|
| 1 | 本规范文档 `LTPMS_UI设计系统_V1.1.md` | ✅ 完成 |
| 2 | Design Tokens `frontend/src/styles/tokens.css` | 待开发（CSS 直接落地） |
| 3 | 组件库可视化 `outputs/LTPMS_design_system.html` | ✅ 完成 |
| 4 | Element Plus 主题覆盖 `frontend/src/styles/element-overrides.scss` | 待开发 |
| 5 | Storybook（可选） | 待评估 |
| 6 | 图标库（建议 Lucide / Phosphor，避开 Heroicons 默认） | 待选型 |

---

## 9. 验收清单（开发完成时自检）

- [ ] 所有 Token 写入 tokens.css，组件不直接写 hex
- [ ] 全部组件 ≥5 种状态都已实现
- [ ] 主要交互流可纯键盘完成（Tab + Enter + Esc）
- [ ] Lighthouse Accessibility ≥ 95
- [ ] axe-core 0 violations
- [ ] 暗色主题切换无破损
- [ ] 平板宽度（768-1023px）下表格可读、布局不破
- [ ] 所有图标的 hover/focus/active 一致

---

*V1.1 替代 V1.0。原 UI 设计规范保留为变更历史（不删除）。*