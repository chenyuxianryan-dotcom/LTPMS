# 基于AI大模型的实验教学项目管理系统 — 组件规范

> 版本：V1.0 | 日期：2026-09-06 | 状态：待确认

---

## 1. 组件树总览

```
App
├── Layouts
│   ├── DefaultLayout          # 登录后主布局
│   │   ├── AppHeader          # 顶部导航栏
│   │   ├── AppSidebar         # 左侧菜单
│   │   └── AppContent         # 内容区（<router-view>）
│   └── BlankLayout            # 登录页等无导航布局
│
├── Views (页面)
│   ├── Login                  # 登录页
│   ├── Dashboard
│   │   ├── AdminDashboard     # 管理员首页
│   │   └── TeacherDashboard   # 教师首页
│   ├── Laboratory
│   │   └── LabList            # 实验室管理
│   ├── Semester
│   │   └── SemesterList       # 学期管理
│   ├── Schedule
│   │   └── ScheduleImport     # 课表导入
│   ├── Teacher
│   │   └── TeacherList        # 教师账号管理
│   ├── Course
│   │   └── CourseList         # 课程列表（教师视角）
│   ├── Experiment
│   │   └── ExperimentList     # 实验项目管理
│   ├── Report
│   │   ├── CourseProjectReport  # 课程项目统计
│   │   └── PersonHoursReport    # 人时数统计
│   ├── NLQuery
│   │   └── NLQueryPage        # 自然语言查询页
│   └── Profile
│       └── ChangePassword     # 修改密码
│
├── Components (通用组件)
│   ├── DataTable              # 通用数据表格（封装el-table）
│   ├── ImportUploader         # Excel导入组件
│   ├── ExportButton           # 导出按钮（Excel/PDF）
│   ├── SemesterSelector       # 学期选择器
│   ├── DictSelect             # 字典下拉选择器
│   ├── ConfirmDelete          # 删除确认弹窗
│   ├── EmptyState             # 空状态占位
│   ├── PageHeader             # 页面标题+面包屑
│   └── PermissionButton       # 权限按钮（按角色显示/隐藏）
│
└── Composables (组合式函数)
    ├── useAuth                # 认证相关（登录、登出、当前用户）
    ├── usePagination          # 分页逻辑
    ├── useImport              # Excel导入逻辑
    ├── useExport              # 导出逻辑
    └── useDict                # 字典数据加载
```

---

## 2. 通用组件详细规范

### 2.1 DataTable — 通用数据表格

**职责**：封装 el-table + el-pagination，统一列表页交互。

**Props 接口**：

| Prop | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| columns | Column[] | — | 列配置数组 |
| loadData | Function | — | 异步数据加载函数，接收 (page, size, filters) |
| pageSize | number | 20 | 每页条数 |
| stripe | boolean | true | 斑马纹 |
| showSelection | boolean | false | 显示多选框 |
| showIndex | boolean | false | 显示序号列 |
| rowKey | string | 'id' | 行唯一标识字段 |
| emptyText | string | '暂无数据' | 空状态文案 |

**Column 配置**：

```typescript
interface Column {
  prop: string        // 字段名
  label: string       // 列标题
  width?: number      // 固定宽度
  minWidth?: number   // 最小宽度
  fixed?: 'left' | 'right'  // 固定列
  sortable?: boolean  // 是否可排序
  formatter?: (row, col, val) => string  // 格式化函数
  slot?: string       // 自定义插槽名
}
```

**Events**：

| 事件 | 参数 | 说明 |
|------|------|------|
| selection-change | `rows[]` | 多选变化 |
| row-click | `row` | 行点击 |
| page-change | `{page, size}` | 翻页 |

**空状态**：当 `loadData` 返回空数组时，显示 EmptyState 组件。

---

### 2.2 ImportUploader — Excel导入组件

**职责**：封装文件上传、格式校验、预览、确认导入的完整流程。

**Props 接口**：

| Prop | 类型 | 说明 |
|------|------|------|
| action | string | 上传接口地址 |
| templateUrl | string | 模板下载地址 |
| accept | string | 接受的文件类型，默认 `.xlsx,.xls` |
| maxSize | number | 最大文件大小(MB)，默认 5 |
| extraData | object | 附加参数（如 semesterId） |
| previewEnabled | boolean | 是否启用预览步骤 |

**交互流程**：

```
步骤1：上传文件（拖拽或点击选择）
  ↓ 格式校验（文件类型、大小）
步骤2：预览（如 previewEnabled=true）
  → 展示前10行数据
  → 标红校验失败的行
  → 显示"成功N条，失败M条"
  ↓
步骤3：确认导入
  → 调用后端接口
  → 返回导入结果
```

**Events**：

| 事件 | 参数 | 说明 |
|------|------|------|
| import-success | `{success, failed, errors}` | 导入完成 |
| import-error | `error` | 导入失败 |

---

### 2.3 ExportButton — 导出按钮

**职责**：封装报表导出，支持 Excel 和 PDF 两种格式。

**Props 接口**：

| Prop | 类型 | 说明 |
|------|------|------|
| url | string | 导出接口地址 |
| params | object | 查询参数 |
| filename | string | 导出文件名 |
| formats | string[] | 支持的格式，默认 `['excel', 'pdf']` |

**渲染**：

- 单格式时渲染为一个按钮
- 多格式时渲染为下拉按钮组（`el-dropdown`）

---

### 2.4 SemesterSelector — 学期选择器

**职责**：下拉选择学期，自动标记当前学期。

**Props 接口**：

| Prop | 类型 | 说明 |
|------|------|------|
| modelValue | number | 当前选中的学期ID |
| showCurrent | boolean | 是否标记当前学期，默认 true |

**行为**：
- 加载时请求 `/api/v1/semesters` 获取学期列表
- 当前学期显示"（当前）"标签
- `v-model` 双向绑定

---

### 2.5 DictSelect — 字典下拉选择器

**职责**：根据字典类型自动加载选项，封装为下拉选择框。

**Props 接口**：

| Prop | 类型 | 说明 |
|------|------|------|
| dictType | string | 字典类型（如 'category'） |
| modelValue | string | 当前值 |
| placeholder | string | 占位文案 |
| clearable | boolean | 是否可清空 |

**行为**：
- 组件挂载时请求 `/api/v1/dicts/type/{dictType}` 加载选项
- 支持 `v-model` 双向绑定
- 选项列表全局缓存（同一字典类型只请求一次）

---

### 2.6 PermissionButton — 权限按钮

**职责**：根据当前用户角色决定按钮是否显示/禁用。

**Props 接口**：

| Prop | 类型 | 说明 |
|------|------|------|
| roles | string[] | 允许的角色列表，如 `['ADMIN']` |
| fallback | 'hide' \| 'disable' | 无权限时的行为，默认 'hide' |

**用法示例**：

```vue
<PermissionButton :roles="['ADMIN']" type="danger">
  删除
</PermissionButton>
<!-- 教师看到的是：（按钮隐藏） -->
```

---

## 3. 核心页面组件

### 3.1 NLQueryPage — 自然语言查询页

**组件结构**：

```
NLQueryPage
├── QueryInput          # 查询输入区
│   ├── el-input        # 输入框
│   ├── el-button       # 查询按钮
│   └── ExampleList     # 示例问题列表（3-5条）
├── QueryResult         # 查询结果区
│   ├── ResultHeader    # 结果概要（条数、耗时）
│   ├── DataTable       # 结果表格
│   ├── ExportButton    # 导出按钮
│   ├── SqlToggle       # 展开/收起生成的SQL
│   └── SuggestionsList # 相关追问推荐
└── QueryHistory        # 查询历史侧栏
    ├── HistoryItem     # 单条历史记录
    └── el-pagination   # 分页
```

**状态管理**：

```typescript
interface NLQueryState {
  question: string           // 输入框内容
  loading: boolean           // 查询中
  result: QueryResult | null // 查询结果
  history: HistoryItem[]     // 查询历史
  examples: ExampleQuestion[] // 示例问题（动态加载）
}
```

### 3.2 ExperimentList — 实验项目管理页

**组件结构**：

```
ExperimentList
├── CourseInfo          # 当前课程信息卡（课程名、学期、教师）
├── ActionBar           # 操作栏
│   ├── el-button       # 新增项目
│   ├── el-button       # 从上学期复制
│   ├── ImportUploader  # Excel导入
│   └── el-button       # 下载模板
├── DataTable           # 实验项目列表
│   └── [操作列]
│       ├── el-button   # 编辑（当前学期可点，历史学期禁用）
│       └── ConfirmDelete  # 删除（当前学期可点，历史学期隐藏）
└── ExperimentForm      # 新增/编辑表单（el-dialog弹窗）
    ├── el-input        # 实验名称
    ├── DictSelect      # 实验类别
    ├── DictSelect      # 实验类型
    ├── DictSelect      # 实验所属学科
    ├── DictSelect      # 实验要求
    ├── DictSelect      # 实验者类别
    ├── el-input-number # 每组人数
    └── el-input-number # 实验学时数
```

**交互行为**：
- "从上学期复制"：点击后弹出确认框，显示"将复制N个实验项目"，确认后调接口，成功后刷新列表
- 新增/编辑：弹窗表单，字典字段用 DictSelect，实验编号由后端自动生成不需要输入
- 删除：ConfirmDelete 弹窗二次确认
- 历史学期：所有编辑/删除按钮禁用或隐藏

---

## 4. 状态管理（Pinia）

### 4.1 Store 划分

| Store | 职责 |
|-------|------|
| `useUserStore` | 当前用户信息、Token、登录/登出 |
| `useAppStore` | 全局UI状态（侧栏折叠、当前学期ID） |
| `useDictStore` | 字典数据缓存 |

### 4.2 useUserStore

```typescript
interface UserState {
  token: string | null
  user: {
    id: number
    username: string
    realName: string
    role: 'ADMIN' | 'TEACHER'
    isDefaultPwd: boolean
  } | null
}

// actions
login(username, password) → void
logout() → void
fetchUserInfo() → void
changePassword(oldPwd, newPwd) → void
```

### 4.3 useAppStore

```typescript
interface AppState {
  sidebarCollapsed: boolean     // 侧栏是否折叠
  currentSemesterId: number | null  // 当前选中的学期
}

// actions
toggleSidebar() → void
setCurrentSemester(id) → void
```

### 4.4 useDictStore

```typescript
interface DictState {
  cache: Map<string, DictItem[]>  // dictType → items
}

// actions
loadDict(dictType: string) → Promise<DictItem[]>
getDict(dictType: string) → DictItem[]
```

---

## 5. 复用性清单

| 组件 | 复用场景 | 复用次数 |
|------|---------|---------|
| DataTable | 实验室列表、教师列表、课表列表、课程列表、项目列表、报表 | 8+ |
| ImportUploader | 实验室导入、课表导入、教师导入、项目导入 | 4 |
| ExportButton | 两个报表页各1个 | 2 |
| SemesterSelector | 课表管理、报表查询、NL查询（可选） | 3+ |
| DictSelect | 实验项目表单（5个字典字段） | 5 |
| ConfirmDelete | 实验室删除、项目删除、课表删除、字典删除 | 4+ |
| PageHeader | 所有页面 | 15+ |
| PermissionButton | 管理员专属操作按钮 | 10+ |
| EmptyState | 所有列表页的空状态 | 8+ |

---

*组件规范确认完毕后，进入阶段6：开发指南。*

**这份组件规范是否OK？有要调整的吗？**
