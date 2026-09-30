# LTPMS 学期与实验室开放管理 — 需求拷问记录

> 版本：V1.1（审查调整修订） ｜ 状态：**已评审定稿，待开工** ｜ 拍板日期：2026-09-14
> V1.1 变更：采纳独立审查结论（见 §11），修订 D15 / S6 / §4 / §6 / §7 / §10 并补齐任务描述。
> 约定：本文档为实施蓝图，与《LTPMS_人员档案检索_需求拷问记录.md》同格式并排归档。
> 需求原文：*「该项目学期管理模块：目前功能单一单调，无明显实际操作功能，可有可无；修改方向：引入学校学年安排，统筹安排各个实验室的开放管理等情况。」*

---

## 1. 拷问方法

沿用「先查证、后提问」的拷问式评审：**能从代码/数据库/文档查到的，一律查证，不问人；只把真正的决策交给决策者，一次只问一个，并附推荐答案与代价。**

共查证 10 项事实、提出 17 个决策点、全部拍板。

---

## 2. 事实核查（提问前已确认）

| # | 事实 | 证据 |
|---|---|---|
| F1 | 学期页现状仅「列表 + 新建 + 编辑」，无删除、无详情、无学年视图；仅 `UPCOMING` 状态可编辑基础信息 | `frontend/src/pages/semester/SemesterPage.vue:8,27-34,77` |
| F2 | 无校历数据模型：`semester` 表只有学年/序号/名称/起止/状态；全库**无** holiday、开放时段、例外日表 | `database/init.sql:55`（权威文件）；全库 grep 无命中 |
| F3 | 学期状态按日期动态计算并回写缓存；「当前学期」三级判定（落在区间 → 最近结束 → 最近创建 UPCOMING） | 《LTPMS_技术设计文档.md:209-229》；`SemesterService` |
| F4 | 全库零物理外键，关联靠命名约定；`schedule.lab_code` 为字符串逻辑外键 | 人员档案拷问 F3 同源结论；`init.sql:67` |
| F5 | 系统**无学生账号**，只有 `ADMIN`/`TEACHER` 两角色 → 任何预约/申请的主体先天缺席 | `init.sql` user.role enum |
| F6 | 系统**无节次→钟点映射**：`schedule_time` 只有 tinyint 节次，全库无节次日程表 | `init.sql:85-95` |
| F7 | 实测：`schedule_time` 8 行全为 `1-16` 周 / `ALL` / 节次 1-8 / 周一~周五；`laboratory` 39 间全部启用；`semester` 2 行且 `start_date` 均为周一（2026-02-23、2026-09-07） | 实测 SQL |
| F8 | 现有热力图只统计排课节数、**无分母**，`UtilizationItem` 无 rate 字段 | `InsightService.java:104-140`；`InsightStats.java:50` |
| F9 | 既有可复用模式：课表导入「整学期覆盖」；「从上学期复制」已是既定交互（US-02） | `ScheduleService.doImport`；《LTPMS_PRD.md:20` |
| F10 | NLQ 的 schema 全表透传（新表自动进入模型上下文），但需补权限白名单与口径提示 | `schema_cache.py`；`permission.py` |

**由此确立的关键约束**：F5 + F6 直接否定了「预约体系」与「钟点表达」两条路线的性价比（见 D1、D5）。

---

## 3. 决策记录

### D1 开放管理深度
**结论**：只做「开放计划 + 占用视图」，**不做预约/审批**。
**理由**：F5——无学生账号，预约主体只有教师，功能完整度先天残缺；而「空闲时段可查可用」才是开放管理的真实落点，工作量可控（3 张新表）。

### D2 校历范围
**结论**：教学周历（第 1 周起始日 → 周次↔真实日期映射）+ 节假日表；**不做**考试周、调课补课。
**理由**：`schedule_time.week_range='1-16'` 目前只是文本；有了周历才能回答「第 3 周周三具体是哪天」，开放时段的生效周范围与导出、NLQ 都依赖它。调课会反向改写课表语义，本轮不引入。

### D3 学年实体
**结论**：**派生，不建表**；寒暑假不单独管理。
**理由**：学年 = `semester.academic_year` 分组即可表达；寒暑假边界每年变化且无业务动作挂靠，单独建表必然沦为废数据。

### D4 开放计划粒度
**结论**：按**学期实例**维护（如 2026-2027-1），提供「从上学期复制」。
**理由**：占用比对按学期进行（课表本就按学期导入）；周次↔日期的映射依赖该学期周历；与既有「整学期覆盖 / 上学期复制」模式一致。

### D5 时段表达
**结论**：复用**节次**（星期几 + 起止节次 + 生效周范围 + 单双周），与 `schedule_time` 同构；不引入钟点。
**理由**：F6——系统无节次→钟点映射，用钟点需先建新主数据且与课表换算口径易错位；节次表达下占用比对是同字段直接可比。

### D6 例外管理
**结论**：时段**启停开关** + **例外日表**（起止日期 + 原因）；节假日与例外日在视图中渲染为闭馆。
**理由**：检修、承接考试、假期施工是真实运维场景；实现成本低（1 张表 + 同页表单）。

### D7 附加属性
**结论**：**开放对象**（全校/本院/指定专业/教师专用）+ **备注**；不存容量。
**理由**：回答「开放给谁用」是学校开放管理表的必备列；容量可由 `laboratory.device_count` 参考，不另存。

### D8 权限
**结论**：**仅 ADMIN 维护**（校历/开放计划/例外日）；TEACHER 只读。
**理由**：`laboratory.manager` 只是姓名字段、无账号（F4 同源），教师侧不存在「我管理的实验室」绑定关系；开放编辑需新建整套绑定数据，本轮不做。

### D9 排课矛盾处理
**结论**：**只读标红**，不阻断课表导入。
**矛盾判定口径**：课表时段与开放时段在「星期 + 节次 + 周次」三维不重叠即矛盾（**含**周次交集与单双周相容性判定；**不含**节假日/例外日——否则国庆 7 天必然把大量课标红，噪声盖过信号）。
**理由**：课表导入是既有主流程，不应被新模块卡住；矛盾数据反而成为管理员修正开放计划的线索。

### D10 页面结构
**结论**：**全部收进学期页**：学年分组卡片 → 学期详情三区（周历 / 节假日 / 开放计划）；实验室管理页不动。
**理由**：直接对症「学期管理功能单一单调」；「统筹各实验室」的跨实验室视角（批量、总览）天然属于学期语境。

### D11 占用视图形态
**结论**：**总览列表**（每间实验室：开放节时、占用节时、利用率、矛盾数、是否已配置）+ 点击展开**单间周网格**（7 天 × 12 节，四色：占用 / 开放空闲 / 未开放 / 闭馆）。
**理由**：列表给全局，网格回答「36-401 周五下午有空吗」这一具体问题。

### D12 初始数据
**结论**：批量设置 + 从上学期复制 + **外置种子脚本**（不进 init.sql）。
**种子内容**：对齐真实数据（工作日 1-8 节、1-16 周、节次 1-8）；部分机房加晚间 9-10 节；1-2 间周末开放示例；2-3 条例外日；节假日（国庆 2026-10-01~07、元旦 2027-01-01）；上一学期（2025-2026-2）造少量数据供「复制」演练；**保留 1-2 处「有课但未开放」矛盾**用于演示与预警验证。
**理由**：页面一打开即非空可验；种子外置，干净部署不受虚构数据污染（与「不伪造数据」约定一致）。

### D13 利用率口径
**结论**：**学期累计周加权**——分子 = Σ(排课节数 × 生效周数)，分母 = Σ(开放节数 × 生效周数)，单/双周减半；利用率 = 分子 ÷ 分母。
未配置开放计划的实验室显示**灰色「未开放」态**，不参与红黄绿评分（也不按隐式默认全开放计算）。
**不扣**节假日/例外日的逐日影响（口径标注「不含扣减」）。
**理由**：短周期开放与长周期开放必须公平可比；逐日精确需引入调休语义，性价比不足。允许 >100%（超排时照实显示）。

### D14 数据智能联动
**结论**：热力图口径改（上表）+ 异常预警新增「有课但实验室未开放」规则 + 数据智能既有四接口（就绪度/热力图/预警/冲突）**回归验收**。
**理由**：口径改动风险面集中在既有聚合接口，必须回归确认未破坏其余三个。

### D15 教师侧入口
**结论**：**教师侧两入口**——① NLQ 可问（新表进只读白名单 + 口径提示）② 仪表盘「今日开放实验室」小块（两角色）。原「课表页开放注记」调整为 **ADMIN 视角功能**：`/schedules` 页面在路由 / 菜单 / 后端接口三层均 ADMIN-only，教师本就无课表页访问权限，开放给教师需同时扩三层权限面，与本节「不新增权限面」原则冲突，不做。
**理由**：开放信息的价值在教师侧才被消费，但只读展示不应以扩大权限面为代价；课表页注记对管理员排课时参照开放窗口仍有价值，保留于 ADMIN 视角。

### D16 验收范围
**结论**：六条新功能剧本（S1~S6，见 §7）+ 数据智能既有接口回归。

### D17 文档产出
**结论**：本文档 + 开工时同步更新《LTPMS_API设计文档.md》（新增接口）与《LTPMS_技术设计文档.md》（新表与口径变更），不做全量重写。

---

## 4. 数据模型设计

### 4.1 新增（2 张新表 + 1 张例外日表 + 1 列）

DDL 编码三件套（`ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`）与现库全表一致；禁止 general_ci / 0900_ai_ci 混用（跨表 `lab_code` 比较会触发 Illegal mix of collations）。

```sql
-- 学期增加周历字段（回填 = start_date，两学期均恰为周一）
ALTER TABLE semester
  ADD COLUMN week1_start_date DATE NULL COMMENT '第1周起始日；空视为 start_date';

-- 节假日（逐日一行，UI 支持区间批量添加）
CREATE TABLE holiday (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  holiday_date DATE NOT NULL COMMENT '停课日',
  name VARCHAR(64) NOT NULL COMMENT '名称 如国庆节',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_holiday_date (holiday_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='节假日表';

-- 实验室开放时段（与 schedule_time 同构，挂在学期）
CREATE TABLE lab_open_time (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lab_code VARCHAR(32) NOT NULL,
  semester_id BIGINT NOT NULL,
  day_of_week TINYINT NOT NULL COMMENT '1=周一..7=周日',
  start_section TINYINT NOT NULL,
  end_section TINYINT NOT NULL,
  week_range VARCHAR(32) NOT NULL COMMENT '周次范围 如1-16',
  week_type ENUM('ALL','ODD','EVEN') DEFAULT 'ALL',
  audience VARCHAR(16) NOT NULL DEFAULT '全校' COMMENT '开放对象',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
  remark VARCHAR(255) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_lot_semester (semester_id),
  KEY idx_lot_lab (lab_code, semester_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验室开放时段表';

-- 例外日（临时闭馆/检修期）
CREATE TABLE lab_open_exception (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lab_code VARCHAR(32) NOT NULL,
  semester_id BIGINT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  reason VARCHAR(128) NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_loe_lab (lab_code, semester_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实验室开放例外日表';
```

### 4.2 落地约定
- `database/init.sql` 为 schema 权威（docker-compose 与《开发指南》引用）；`backend/database/init.sql` 为引导副本（建库 + 账号 + 种子）。两处表定义同步更新，并借本次补齐副本缺失的 `avatar` 列。增量走 `database/upgrade_20260914_semester_open.sql`（ALTER + 三表 + 回填 `week1_start_date = start_date`）。
- 开放对象四值（全校/本院/指定专业/教师专用）用代码内固定枚举（VARCHAR 存中文），**不进** dictionary 主数据。
- 学期状态动态计算机制（F3）不变；**周历/节假日/开放计划对 ACTIVE 学期可配置**，不受学期基础信息「已开始不可编辑」限制（本学期配置校历是主流程）。
- 「批量设置」语义 = **覆盖式**（对选中的实验室先删后插，对齐课表导入的整学期覆盖习惯），UI 明确提示。
- 口径约定：节假日按学期过滤 = `holiday_date ∈ [semester.start_date, semester.end_date]`；「从上学期复制」幂等 = 按选中实验室覆盖式（与批量设置同语义）；占用总览的「开放节时 / 占用节时」与 D13 同源（周加权），「已配置」= 存在 `enabled=1` 的时段行（全停用视为未开放灰态）。
- 本次新增的 ADMIN 写接口（周历 / 节假日 / 开放计划 / 例外日）统一挂 `@OperationLog` 注解（切面已就绪，operation_log 此前零使用）。

---

## 5. 接口设计（RESTful, `/api/v1`）

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/semesters/{id}/calendar` | 登录 | 周历（week1StartDate + 周次↔日期表） |
| PUT | `/semesters/{id}/calendar` | ADMIN | 设置第 1 周起始日 |
| GET | `/holidays?semesterId=` | 登录 | 该学期节假日列表 |
| POST | `/holidays` | ADMIN | 新增（支持日期区间批量展开） |
| DELETE | `/holidays/{id}` | ADMIN | 删除 |
| GET | `/lab-openings?semesterId=&labCode=` | 登录 | 开放时段列表 |
| POST / PUT / DELETE | `/lab-openings`、`/{id}` | ADMIN | 单条增改删 |
| POST | `/lab-openings/batch` | ADMIN | 批量覆盖式设置（多实验室 + 统一时段） |
| POST | `/lab-openings/copy` | ADMIN | 从上学期复制（`fromSemesterId → toSemesterId`，可选实验室子集） |
| GET | `/lab-open-exceptions?semesterId=&labCode=` | 登录 | 例外日列表 |
| POST / DELETE | `/lab-open-exceptions`、`/{id}` | ADMIN | 例外日增删 |
| GET | `/lab-openings/overview?semesterId=` | 登录 | 总览：每实验室 开放节时/占用节时/利用率/矛盾数/是否已配置 |
| GET | `/lab-openings/grid?semesterId=&labCode=` | 登录 | 单间周网格（7×12 四色态 + 闭馆日集合） |
| GET | `/lab-openings/today?semesterId=` | 登录 | 今日开放实验室（仪表盘小块 & 教师端） |
| GET | `/insight/utilization` | 登录 | **返回结构扩展**：新增 openSlotHours / occupiedSlotHours / rate / planned（旧字段保留） |
| — | `/insight/anomalies` | 登录 | **新增规则**：`NOT_OPEN`「有课但实验室未开放」 |

> 注：`/insight/all`（一站式聚合，阿实 Agent 与仪表盘共用）与 utilization / anomalies 同源，口径改动与回归须覆盖。

---

## 6. 页面结构

- **学期管理页（重构）**：学年分组卡片（含周次进度、周历配置状态）→ 学期详情三区 Tab：
  - ① 周历：第 1 周起始日 + 周次↔日期表 + 节假日管理；
  - ② 开放计划：39 间实验室列表（已配置/未配置）+ 单条编辑 + 批量设置 + 从上学期复制 + 例外日；
  - ③ 占用视图：总览列表 + 单间周网格。
  - 详情区建议拆为 `frontend/src/pages/semester/components/*.vue` 子组件，避免单文件失控。
- **仪表盘**：新增「今日开放实验室」小块（两角色可见；数据源直连 `/lab-openings/today`，不改 dashboard 聚合 DTO）。
- **课表页**：行内开放时段注记（只读，**ADMIN 视角**；教师无课表页访问权限，见 D15）。
- **数据智能页**：热力图显示利用率百分比与「未开放」灰态；预警列表呈现 `NOT_OPEN` 新类型（含标签映射，`InsightPage.vue` 与 `api/insight.ts` 同步改造）。

---

## 7. 验收剧本

| # | 剧本 | 通过标准 |
|---|---|---|
| S1 | 学年卡片与校历 | 2026-2027 学年卡片含两学期；为 2026-2027-1 设第 1 周起始日 2026-09-07 → 周次表正确显示「第 4 周 = 09-28~10-04」并标记国庆假日 |
| S2 | 开放计划维护 | 批量设置 10 间实验室（工作日 1-8 节 / 1-16 周 / 全校）→ 单间微调（36-401 加周二 9-10 节）→ 添加例外日 → 「从上学期复制」从 2025-2026-2 复制成功且幂等 |
| S3 | 占用视图 | 总览 39 行（含未配置灰态）→ 36-401 周网格四色正确 → 种子中的矛盾数据标红 |
| S4 | 利用率口径 | 热力图显示「占用节时/开放节时」百分比；未配置实验室灰色「未开放」；与手工 SQL 核算一致 |
| S5 | 异常预警 | 出现「有课但实验室未开放」条目，与 S3 标红数据一一对应 |
| S6 | 教师侧入口与管理端注记 | 教师账号：NLQ 可答开放类问题、仪表盘「今日开放」小块正确（含节假日/例外日闭馆判定）；ADMIN 账号：课表页开放注记可见 |
| 回归 | 数据智能接口 | 就绪度 / 热力图 / 预警 / 冲突（含 `/insight/all` 一站式聚合）的既有断言与冒烟全部重跑通过 |

---

## 8. 边界定义（写死，不可越）

- 开放信息：仅 ADMIN 可写；TEACHER 只读（含 NLQ 只读可问）。
- 不做预约、审批、门禁、容量管理、开放计划 Excel 导入。
- 矛盾检测只读，**不改**课表导入流程；节假日/例外日不参与矛盾判定（仅渲染为闭馆）。
- 学期 CRUD 既有语义不变（无删除、基础信息仅 UPCOMING 可编辑）；周历/节假日/开放计划不受该限制。
- 种子脚本不进 init.sql；干净部署不含虚构开放数据。

---

## 9. 不做清单与风险

- **不做**：考试周、调课补课、周次范围强校验（仅提示）、跨学年周范围重映射提示之外的处理；教师侧不开放课表页 / 学期页访问（教师入口仅 NLQ + 仪表盘「今日开放」小块）。
- **风险 1**：利用率口径变更影响既有热力图数字 → 由回归验收覆盖，口径在页面标注「不含节假日/例外日扣减」。
- **风险 2**：`week1_start_date` 若非周一 → 页面提示但不强拦（周次映射按该日起算）。
- **风险 3**：批量设置为覆盖式语义，误操作会清掉既有计划 → 确认弹窗明示「将覆盖选中实验室本学期的全部开放时段」。

---

## 10. 实施步骤

1. 数据层：三新表 + 学期周历列 DDL（统一 `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`）；`database/init.sql`（权威）与 `backend/database/init.sql`（副本，顺带补 avatar 缺口）同步；增量 `database/upgrade_20260914_semester_open.sql`（ALTER + 三表 + 回填）；Java 实体 / Mapper。
2. 后端：学期日历接口（周历 + 节假日 CRUD，过滤 = `holiday_date ∈ [start_date, end_date]`）；写接口挂 `@OperationLog`。
3. 后端：开放计划 CRUD + 批量覆盖 + 从上学期复制（覆盖式、幂等）+ 例外日；写接口挂 `@OperationLog`。
4. 后端：抽公共 `WeekMath`（网格 / 利用率 / 矛盾判定强制复用）；占用总览 / 周网格 / today 接口（四色态与矛盾判定；总览口径与 D13 同源）。
5. 后端：利用率口径改造 + 预警 `NOT_OPEN` 新规则 + 回归（四接口 + `/insight/all`）；测试 = 纯逻辑工具类单测（util 测试模式）+ 探针冒烟。
6. 前端：学期页重构（学年卡片 + 三区 Tab）；先行补 `types/api.d.ts` 与 api 模块（holiday / labOpening）。
7. 前端：占用视图（总览 + 周网格）+ 开放计划维护 UI + 数据智能页联动（热力图百分比 / 灰态、预警新类型、insight 类型扩展）。
8. 前端 + NLQ：仪表盘「今日开放」小块 + 课表页注记（ADMIN）；NLQ 白名单（`permission.py` 加 3 表）+ 口径提示（prompt_builder）+ 测试补白名单用例。
9. 种子脚本 + 前端 `npm run build` + 端到端验收（S1~S6 + 回归）。
10. 文档同步（API 设计文档 / 技术设计文档）+ 本文档 §12 补记回填。

---

## 11. 审查调整记录（V1.0 → V1.1）

2026-09-14 独立审查（硬冲突 / 结构偏差 / 范围遗漏 / 口径补遗共 19 项发现）全部采纳或决议，归并如下：

| # | 审查发现 | 调整落点 |
|---|---|---|
| 1 | 教师侧「课表页注记」不可达（路由 / 菜单 / 接口三层 ADMIN-only） | D15、S6 |
| 2 | 双 init.sql 已漂移（副本缺 avatar） | §4.2 |
| 3 | 数据智能页前端改动未落任务 | §10-7、任务 #21 |
| 4 | 前端类型与 api 模块未列任务 | §10-6、任务 #20 |
| 5 | 前端 build 步骤缺失（vite preview 跑 dist） | §10-9、任务 #23 |
| 6 | NLQ 改动被低估（白名单 / 口径 / 测试） | §10-8、任务 #22 |
| 7 | 回归缺 `/insight/all` | §5 注、§7、§10-5 |
| 8 | 后端单测承诺超出测试基建（仅 util 级） | §10-5 |
| 9 | 周次解析将现第四、五份实现 | §10-4（公共 WeekMath） |
| 10 | 仪表盘小块数据源未写明 | §6 |
| 11 | 新表 DDL 缺编码三件套（collation 混用风险） | §4.1 |
| 12 | 节假日过滤 / copy 幂等 / overview 口径 /「已配置」判定未写死 | §4.2 |
| 13 | 审计注解零使用 | §4.2（挂 @OperationLog） |

> 审查项「教师自助浏览网格」决议：维持 D15，不新增教师页面。任务承接为 #15~#24。

---

## 12. 实施与验收补记

> 回填日期：2026-09-14 ｜ 状态：**全部完成（任务 #15~#24）** ｜ 验收结论：API 探针 **PASS 43 / FAIL 0**，浏览器 UI 验收通过（管理员 + 教师双角色）

### 12.1 交付物清单

| 层 | 交付 |
|---|---|
| 数据层 | `database/init.sql`（权威）与 `backend/database/init.sql`（副本，补齐 avatar）同步三新表 + `week1_start_date`；增量脚本 `database/upgrade_20260914_semester_open.sql`（ALTER + 三表 + 回填）；Java 实体 / Mapper |
| 后端 | `SemesterController/Service`（周历）与 `HolidayController`（节假日 CRUD）；`LabOpeningController/Service`（单条 CRUD + 批量覆盖 + 复制幂等 + 总览 / 周网格 / today）；`LabOpenExceptionController`；公共 `WeekMath`（网格 / 利用率 / 矛盾判定复用）；`InsightService` 利用率口径改造 + `NOT_OPEN` 预警；写接口全部挂 `@OperationLog` |
| 前端 | `pages/semester/*`（学年卡片 + 三区 Tab；8 个子组件：周历面板 / 开放计划面板 / 占用面板 / 周网格 + 单条、批量、复制、例外日 4 个对话框）；`api/semester.ts`、`api/labOpening.ts`、`types/api.d.ts` 扩展；仪表盘「今日开放实验室」小块（两角色）；课表页「开放注记」列（ADMIN）；数据智能页热力图百分比 / 未开放灰态 / `NOT_OPEN` 标签映射 |
| NLQ | `permission.py` 白名单 + 3 表；`prompt_builder.py` 补开放口径与周历口径；`tests/test_permission.py` 补白名单用例 |
| 种子 / 验收 | `scripts/seed_lab_openings.py`（外置脚本，不进 init.sql）；`outputs/acceptance_open_check.py`（43 项 API 探针，覆盖 S1~S6 + 回归） |

### 12.2 验收证据

**API 探针（`outputs/acceptance_open_check.py`，PASS 43 / FAIL 0）**，关键实测值（与种子设计逐项核对手工 SQL 一致）：

| 观测点 | 实测 |
|---|---|
| S1 周历 | 第 1 周 = 2026-09-07 起、共 20 周；第 4 周 = 09-28~10-04 含国庆标记；8 天节假日（国庆 7 + 元旦 1） |
| S2 写剧本 | 单条增改删、批量覆盖（1 间 1 条）、复制两次均返回 2 间（幂等）、例外日增删、重复区间节假日拒绝（400） |
| S3 占用 | 39 间总览；36-401 周网格四色正确；矛盾标红 2 处（36-201 / 37-102） |
| S4 利用率 | 36-401 = 672/128 ≈ 19.0%；36-201 = 256/32 = 12.5%；37-102 = 64/48 = 75.0%；38-201 = 832 节时（验证单双周减半：40×16 + 10×16 + 4×8）；35-509 rate=null（未配置灰态） |
| S5 预警 | `NOT_OPEN` 2 条（36-201 周1 第3-4节、37-102 周3 第5-7节），与 S3 标红一一对应 |
| S6 教师侧 | 教师可查开放时段（67 行只读）；`/lab-openings/today` 11 间（38-201 相邻节次合并为 1-10 节、开放对象聚合「全校、本院」、38-301 教师专用）；教师 NLQ「今天有哪些实验室开放？」返回 12 条真实结果（SCOPE:SELF） |
| 回归 | `/insight/readiness|utilization|anomalies|conflicts` 全 200；`/insight/all` 四键齐全 |

**浏览器 UI 验收（vite preview :8000，管理员 admin + 教师 T003）**：

- 学期管理页：学年分组卡片（2026-2027 进行中 / 2025-2026 已归档 + 周次进度）；周历 Tab（周次↔日期表、国庆/元旦备注、节假日逐日列表）；开放计划 Tab（39 行 / 13 已配置 / 2 处矛盾、36-401 展开 6 段明细、批量设置 / 从上学期复制 / 例外日管理三弹窗渲染正确）；占用视图 Tab（13/39、占用节时合计 464、利用率标签色阶、36-401 周网格抽屉四色 + 闭馆日集合）。
- 概览页：两角色「今日开放实验室」均 11 间，元信息「2026-2027-1 · 2026-09-14 · 周一 · 第 2 周」。
- 课表管理页（ADMIN）：「开放注记」列正确（36-201/37-102 显示矛盾 1 处、36-401 显示 6 段开放）。
- 数据智能页：热力图百分比 + 「未开放」灰态；预警列表含 `未开放` 两条；冲突检测无冲突。
- 教师侧：菜单收敛（无实验室/学期/课表/教师/字典管理）；直访 `/semesters` 被路由守卫重定向回概览；NLQ 页真实链路返回 12 条结果表。

**验收中发现并修正**：周网格抽屉底部标签「本周闭馆日」与数据口径不符（后端返回的是**学期范围**闭馆日集合）→ 已改为「闭馆日（全学期）：」并前端重建复验。

### 12.3 种子脚本使用说明

- 运行：`python scripts/seed_lab_openings.py`（清理后重灌；输出预期「13 间实验室已配置；2 处矛盾（36-201、37-102）」）。
- 种子布局：10 间批量工作日 1-8 节 / 1-16 周 / 全校；36-201 与 37-102 故意各留 1 处矛盾；38-201 含工作日晚上（本院）+ 周六单周；38-301 教师专用；37-101 周末（指定专业）；3 条例外日；8 天节假日；上学期（2025-2026-2）10 行供复制剧本。
- 注意：验收探针的 S2 写剧本会覆盖 36-401/36-402 的计划，**跑完探针需重跑种子恢复基线**；探针内部已将 S6 只读剧本前移，避免自身状态漂移。

### 12.4 遗留与例外（明确不处理）

1. **既有 NLQ 单测 1 例失败（非本次引入）**：`tests/test_deepseek_client.py::test_chat_completion_passes_tools` 期望模型 `deepseek-chat`，而本地 gitignore 的 `.env` 设置 `DEEPSEEK_MODEL=deepseek-v4-flash`（环境驱动），全量结果 155 passed / 1 failed；`config.py` 本次未改动，按最小改动范围未修。
2. **教师账号默认密码机制**：T003 首次登录触发「强制改密」为既有机制；浏览器验收时以客户端方式绕过（未实际改密，种子账号保持 `is_default_pwd=1`），不影响 API 侧凭据。
3. **按 §8 边界未做**：预约/审批/门禁/容量、开放计划 Excel 导入、教师侧学期页访问、矛盾阻断导入——均按蓝图决议维持不做。
