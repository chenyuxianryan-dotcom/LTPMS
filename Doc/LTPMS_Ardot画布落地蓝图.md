# LTPMS Ardot 画布落地蓝图

> 用途：把 `LTPMS_UI设计系统_V1.1.md` 的设计系统 + 关键页面落到 Ardot 画布。
> 状态：**执行方案已就绪，等待 Ardot MCP 连接后直接跑**。
> 版本：V1.0 | 日期：2026-09-07

---

## 0. 前置条件（唯一阻塞项）

Ardot 画布 MCP 工具（`create_design` / `open_design` / `batch_edit` / `fetch_file_info` / `locate_available_space` / `capture_layout` / `capture_screenshot`）当前会话**未挂载**。

**激活路径**（按你既往的 `@scene + @skill` 工作流）：
1. 在编辑器里打开/新建一个 Ardot 设计文件（或切入 Ardot 场景），让内置 Ardot MCP 挂载到会话；
2. 激活后回我一句「连上了」，我立即从 §3 开始执行，无需重新推导。

---

## 1. 画布文件规划

**文件**：`LTPMS_UI设计系统_V1.1`（新建 Ardot 设计文件）

**Frame 结构**（自上而下排布，间距 80px）：

| # | Frame 名 | 尺寸 | 内容 |
|---|---------|------|------|
| F0 | 封面 Cover | 1440×900 | 项目名 + 版本 + 设计原则 3 条 |
| F1 | Design Tokens | 1440×1200 | 色板 / 字体阶梯 / 间距 / 圆角 / 阴影 |
| F2 | 组件库 Component Library | 1440×1400 | Button / Input / Tag / Table / Empty / Alert |
| F3 | **智能查询页 NL2SQL** | **1440×960** | **首个落地的完整页面** |
| F4 | 列表页模板（实验室） | 1440×960 | PageHeader + FilterBar + DataTable + Pagination |
| F5 | 表单页模板（新建实验项目） | 1440×900 | 标签在上、720px 居中 |

> 落地顺序：**F3 先行**（差异化亮点、最能体现设计系统），再 F1 → F2 → F4 → F5。

---

## 2. Token → Ardot 映射

### 2.1 颜色（Ardot Fill / 变量）

| Ardot 变量名 | 值 | 用途 |
|-------------|-----|------|
| `brand-50` | `#EFF6FF` | 选中底色 / 主色 hover 底 |
| `brand-100` | `#DBEAFE` | 链接 hover |
| `brand-600` | `#2563EB` | 主按钮 / 链接 / 焦点环 |
| `brand-700` | `#1E40AF` | **主品牌色**（导航高亮、主按钮文字） |
| `brand-800` | `#1E3A8A` | hover / 标题 |
| `bg-canvas` | `#FAFAFA` | 页面底色 |
| `bg-elevated` | `#FFFFFF` | 卡片 / 弹窗 |
| `bg-muted` | `#F5F5F5` | 表头 / 斑马纹 |
| `bg-subtle` | `#F9FAFB` | 输入框底 / 行 hover |
| `border-subtle` | `#F1F5F9` | 卡片内分割线 |
| `border-default` | `#E2E8F0` | 常规边框 |
| `border-strong` | `#CBD5E1` | hover 边框 |
| `text-primary` | `#0F172A` | 标题 |
| `text-secondary` | `#475569` | 正文 |
| `text-tertiary` | `#64748B` | 辅助 / 占位 |
| `text-quaternary` | `#94A3B8` | 禁用 |
| `success-50/600` | `#ECFDF5` / `#059669` | 成功 |
| `warning-50/600` | `#FFFBEB` / `#D97706` | 警告 |
| `danger-50/600` | `#FEF2F2` / `#DC2626` | 危险 |

### 2.2 字体

| Ardot 变量名 | 值 | 用途 |
|-------------|-----|------|
| `font-sans` | IBM Plex Sans（中文 fallback：PingFang SC / Microsoft YaHei） | 正文 / 标题 |
| `font-mono` | JetBrains Mono | 数字 / SQL / 编号 |

### 2.3 字号 / 字重（Text Style）

| Style 名 | px | 行高 | 字重 |
|----------|----|------|------|
| `text-xs` | 12 | 1.5 | 400 |
| `text-sm` | 13 | 1.5 | 400 |
| `text-base` | 14 | 1.5 | 400（正文默认） |
| `text-lg` | 16 | 1.5 | 500 |
| `text-xl` | 18 | 1.4 | 600 |
| `text-2xl` | 20 | 1.35 | 600 |
| `text-3xl` | 24 | 1.3 | 600（H1） |
| `text-4xl` | 30 | 1.25 | 700 |
| `text-5xl` | 36 | 1.2 | 700 |

### 2.4 间距 / 圆角 / 阴影

- 间距基准 4px：`4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64`
- 圆角：`xs 2` · `sm 4` · `md 6`（按钮/输入默认）· `lg 8`（卡片）· `xl 12`（弹窗）
- 阴影（统一 `rgba(15,23,42,*)`）：
  - `sm`：`0 1px 2px rgba(15,23,42,.06), 0 1px 1px rgba(15,23,42,.04)`
  - `md`：`0 4px 6px -1px rgba(15,23,42,.08), 0 2px 4px -2px rgba(15,23,42,.04)`

---

## 3. F3 · 智能查询页（NL2SQL）节点级清单

> 内容语言：**中文**，保留英文术语（NL2SQL / SQL / ⌘K 等）原样。
> Frame 1440×960，背景 `bg-canvas #FAFAFA`。坐标单位 px，原点左上。

### 3.1 左侧栏 Sidebar（0,0,240,960 · bg `#FFFFFF` · 右边框 1px `border-subtle`）

| 节点 | 类型 | 坐标/尺寸 | 文本 | 样式 |
|------|------|-----------|------|------|
| Logo 块 | Frame | (24,20,192,48) | 「LTPMS」+ 副标「实验教学项目管理系统」 | 主标 20px/600 `#0F172A`；副标 12px `text-tertiary` |
| 菜单组 | Frame | (0,80,240,700) | 9 项，每项 40px 高 | — |
| └ 菜单项×9 | Text+Frame | 每项 (0,_,240,40) | 概览 / 实验室管理 / 教师管理 / 课程管理 / 课表管理 / 实验项目 / 报表中心 / **智能查询** / 系统设置 | 默认 `text-secondary`；图标 18px |
| └ 选中项「智能查询」 | Frame | — | 智能查询 | 左侧 2px `brand-700` 边条 + 底 `brand-50` + 文字 `brand-700` 500 |
| 底部用户 | Frame | (0,884,240,76) | 头像 + 「张老师」+ 角色「教师」 | 头像 32px 方形，`brand-100` 底 `brand-800` 字 |

### 3.2 顶栏 Header（240,0,1200,56 · bg `#FFFFFF` · 底边框 1px `border-subtle`）

| 节点 | 坐标/尺寸 | 文本 | 样式 |
|------|-----------|------|------|
| 面包屑 | (264,0,240,56) | 首页 / 智能查询 | 13px；末级 `text-secondary`，前级 `text-tertiary` |
| 搜索框 | 居中 (720,10,280,36) | ⌘K 搜索 | 圆角 6px，`border-default`，占位 `text-quaternary` |
| 通知铃 | 右 (1360,10,36,36) | icon | `text-secondary` |
| 头像 | (1404,12,32,32) | 张 | `brand-600` 底白字 |

### 3.3 主内容区（264,80 → 1440,960 · 内边距 24）

| 节点 | 坐标/尺寸 | 文本 | 样式 |
|------|-----------|------|------|
| H1 | (264,96,600,32) | 智能查询 | 24px/600 `text-primary` |
| 副标题 | (264,132,700,22) | 用自然语言查询实验数据 · 自动注入数据权限 | 14px `text-tertiary` |
| **查询卡片** | Frame (264,170,872,148) | — | `#FFFFFF`，radius 8，`border-subtle`，shadow-sm，pad 20 |
| └ 输入框 | (284,190,852,64) | 占位「例如：2025-2026 学年第 1 学期我开了几门实验课？」 | radius 6，`bg-subtle`，`border-default`，focus `border-focus` |
| └ 查询按钮 | (284,262,96,36) | 查询 | `brand-600` 底白字，radius 6 |
| └ 示例 chips×3 | (392,262,_,28) | 「查本学期我的实验项目」「全校实验人时数合计」「CS101 历史开课」 | `brand-50` 底 `brand-700` 字，radius 9999 |
| **结果卡片** | Frame (264,342,872,540) | — | `#FFFFFF`，radius 8，shadow-sm |
| └ SQL 折叠头 | (288,366,824,24) | ▾ 生成的 SQL · 自动注入数据权限 | mono 13px `text-secondary` |
| └ SQL 代码块 | (288,398,824,120) | `SELECT ... WHERE course.teacher_id = :uid`（含 `-- ★ 权限注入` 注释高亮） | 深底 `#0F172A`，mono 13px，关键字青色、注释 `#94A3B8` |
| └ 结果表 | (288,534,824,300) | 表头：实验编号 / 实验名称 / 课程 / 学时 / 人数 / 状态 | 表头 `bg-muted` 12px/600；行高 48；zebra `bg-subtle`；数字列右对齐 tabular-nums |
| └ 状态 Tag | 行内 | 进行中 / 已完成 / 待开始 | `success-50`+`success-600` / `brand-50`+`brand-700` / `warning-50`+`warning-600` |
| └ 继续追问 chips | (288,846,_,28) | 「只统计必修」「按人时数排序」 | 描边中性，`border-default`，hover `border-strong` |

### 3.4 历史侧栏（1136,80,304,880 · bg `#FFFFFF` · 左边框 1px `border-subtle`）

| 节点 | 坐标/尺寸 | 文本 | 样式 |
|------|-----------|------|------|
| 标题 | (1160,104,_,24) | 查询历史 | 16px/500 `text-primary` |
| 时间线项×5 | 每项高 64 | 「查本学期我的实验项目」+ 时间「2 分钟前」 | 左侧点状指示 `brand-600`；标题 14px，时间 12px `text-quaternary` |

---

## 4. 执行序列（Ardot 连接后即跑）

1. `create_design(fileName: "LTPMS_UI设计系统_V1.1")` —— 一次。
2. 建全局变量（§2 颜色/字号/间距）→ `apply_variables` 绑定。
3. 按 F3 → F1 → F2 → F4 → F5 顺序，每 Frame 用 `batch_edit`（≤25 ops/次），顺序：结构 → 内容 → 样式。
4. 每个 Frame 完成后 `capture_layout` 查溢出 + `capture_screenshot` 视觉核对（收敛阈值：每区最多修 2 轮，≤4px 忽略）。

---

## 5. 关键组件规格（Ardot 节点参数）

| 组件 | 尺寸 | 关键参数 |
|------|------|---------|
| Button md | 36px 高，padding-x 16，字号 14 | primary `brand-600`；focus 2px `border-focus` outline |
| Button sm/lg | 28 / 44 | lg 触控 ≥44px |
| Input | 36px 高，radius 6 | default `border-default`+`bg-subtle`；focus 2px `border-focus`+`bg-elevated` |
| Tag | padding 1×8，radius 2，12px/500 | 5 类底色见 §2.1 |
| Table 行高 | 48（紧凑 40） | 表头 `bg-muted` 12px/600；水平线 `border-subtle` |
| Card | radius 8，pad 24 | `#FFFFFF` + `border-subtle` + shadow-sm |
| Modal | width sm400/md560/lg720/xl900 | radius 12，shadow-xl，遮罩 `rgba(15,23,42,.6)` |
