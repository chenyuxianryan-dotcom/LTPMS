# LTPMS 人员档案检索 — 需求拷问记录

> 版本：V1.0 ｜ 状态：**已评审定稿，开工实施** ｜ 拍板日期：2026-09-14
> 约定：本文档为实施蓝图，与《LTPMS_智能化升级路线图.md》《LTPMS_特色功能创意池.md》并排归档。
> 需求原文：*「期望系统的 AI 智能查询支持：直接输入人名即可检索该人员在本管理系统中关联的全部信息，且检索粒度越细、返回内容越详尽。」*

---

## 1. 拷问方法

本次需求评审采用「先查证、后提问」的拷问式评审：**凡是能从代码/数据库/文档查到的，一律查证，不问人；只把真正的决策交给决策者，一次只问一个，并附推荐答案与代价。**

共查证 6 项事实、提出 10 个决策点、全部拍板。

---

## 2. 事实核查（提问前已确认）

| # | 事实 | 证据 |
|---|---|---|
| F1 | 系统**没有学生、没有成绩、没有实验报告、没有设备台账**。"人员"只有 `ADMIN` / `TEACHER` 两种角色，唯一人员表是 `user` | `LTPMS_PRD.md:61-64` 明确 descope；`database/init.sql:14` |
| F2 | `user.real_name` **无索引、无唯一约束**；全库唯一人类可读唯一键是 `username`（工号） | `SHOW INDEX FROM user` 仅返回 `PRIMARY` / `uk_username` / `idx_role` |
| F3 | 全库**零物理外键**，关联全靠命名约定。`schedule` 无 `course_id` 列，到 `course` 需按 `(course_code, semester_id)` 字符串关联 | `information_schema.TABLE_CONSTRAINTS` 查询为空；`ReportMapper.xml:17`；`init.sql:4` |
| F4 | `laboratory.manager` 是自由文本姓名（`VARCHAR(32)`），22 个取值中 **17 个在 `user` 表查无此人** | 实测 SQL：仅 张伟/李娜/王强/赵敏/陈晨 可挂上账号 |
| F5 | `agent_loop.py:70` 的 `for tc in tool_calls` 支持一轮响应并行多个工具调用；`max_steps` 数的是 **LLM 轮次**而非工具调用次数 | `agent_loop.py:55` / `:70` |
| F6 | 数据现实：有业务数据的教师仅 6 人（user_id 2,3,4,5,6,33）。`schedule_time` / `notification` / `operation_log` **均为 0 行**；`query_history` 6 行且仅属 user_id 1 与 2 | 实测 SQL；`schedule.raw_schedule_text` 8/8 行格式为「周X第A-B节(1-16周)」，可被 `scripts/parse_schedule_time.py` 解析 |

**由此推翻的一个前提**：需求原文假设"该人员在本系统中关联的全部信息"是一个大集合，实际上这个集合很薄，且默认演示对象的档案完整度差异极大。这直接决定了决策 7 与决策 10。

---

## 3. 十项决策

### D1 使用者与场景
**结论**：ADMIN 与 TEACHER 双角色可用；但 **TEACHER 只能查本人**。
**理由**：教师侧若开放查他人，需同时改 `permission.py:13-25`（现在教师查 `course`/`schedule`/`experiment` 被强制注入 `teacher_id = 自己`，连"韩艳教哪些课"都查不出来）。选择"教师只能查自己"把权限模型收敛为一条清晰断言：**解析出的 person_id ≠ 当前 user_id 且角色非 ADMIN → 拒绝**。

### D2 实现路线
**结论**：**纯 Agent + 细粒度工具自主编排**（不做全景兜底工具）。
**代价（已接受并与 D4 绑定）**："返回内容越详尽"不再是代码天然属性，而是模型每轮自觉性的产物，必须由 D4 的断言机制兜底。

### D3 撞名与查无此人
**结论**：**精确优先 + 多候选反问**，绝不静默猜测。
- 多命中 → 停止取数，返回候选（工号 + 院系 + 手机尾号）请用户指认；
- 零命中 → 降级 LIKE + 同音候选，给出建议而非编造；
- 有账号者与"仅挂名者"（见 D6）分开返回。
**理由**：库内埋有 `刘思` vs `刘思思`（子串包含，`LIKE '%刘思%'` 必然误伤）与 `刘海燕`（实验室 manager 文本）vs `韩艳`（T015，同音不同字）两类陷阱，模糊匹配会制造错挂。

### D4 穷尽性兜底
**结论**：**能力清单 + 代码级完成度断言**。循环结束比对已调用工具集与清单，缺项则追加"尚未获取以下维度：…，仅调用缺失工具"再补一轮；二次仍缺则在返回体写 `missing_dimensions`，前端把对应区块标灰。
**理由**：`agent_service.py:43` 原本从不传 `max_steps`（写死 3），且前端 axios `timeout: 60000` 是比轮数更硬的天花板。经 F5 核实，最优编排只需 3 轮（resolve → 并行八维 → 汇总），故把 `max_steps` 提到 6 留出消歧与补漏余量即可，无需重构循环。

### D5 档案维度（能力清单）
**结论**：**业务六件套 + 行为足迹 + 派生洞察**，共八维：

| # | 维度 | 取数路径 |
|---|---|---|
| 1 | 身份与账号 | `user` |
| 2 | 任课课程 | `course.teacher_id` |
| 3 | 排课记录 | `schedule.teacher_id` + `lab_code` → `laboratory` |
| 4 | 课节时间明细 | `schedule.id` → `schedule_time` |
| 5 | 实验项目 | `user` → `course` → `experiment` 二跳 |
| 6 | 管理的实验室 | `laboratory.manager` 精确等值匹配 |
| 7 | 行为足迹 | `query_history` + `notification` + `operation_log`（**ADMIN only**） |
| 8 | 派生洞察 | 门数 / 项目数 / 人时数 / 就绪度缺口 |

### D6 孤儿姓名
**结论**：**双源解析**。`resolve_person` 同时查 `user.real_name` 与 `laboratory.manager`。仅命中后者时返回"挂名档案"：只给"管理的实验室"一维，其余维度标注"不适用（无系统账号）"。
**理由**：17 个孤儿姓名是真实业务数据的现状，不是脏数据——清洗与否属于业务判断，不应由技术方案替业务决定（已记录为待议）。档案维度一律**姓名精确匹配**，不用 LIKE。

### D7 呈现方式
**结论**：**分区档案卡，原始行不经 LLM 转写直接上屏**。
**理由**：`run_agent` 的返回体已含 `steps`，每个 step 内有该工具的完整原始 `result`（`agent_loop.py:84`），前端 `NlqWidget.vue` 已在渲染 steps 轨迹——**结构化上屏无需新增任何 API 协议**。若走"阿实写成一段 markdown"，8 个维度必然被模型为可读性而压缩。

### D8 返回载荷
**结论**：**双份载荷**。
- `display`：全量原始行，只进 `steps` 给前端画卡，**不进 LLM 上下文**；
- `model_view`：脱敏摘要 + 计数 + 关键字段，作为 `role=tool` 消息回喂模型。

**改动**：`agent_loop.py:90` 回喂内容由 `json.dumps(result)` 改为 `json.dumps(result.get("model_view", result))`。
**收益**：上下文不爆、PII 不出境、上屏依然详尽。
**代价**：模型不掌握明细，"再按档案里的手机号搜一次"这类追问做不到。

### D9 Prompt 分层与技能注入
**结论**：**常驻人格 + 人名意图路由按需注入清单**。
- 角色层：`AGENT_SYSTEM_PROMPT` 重写为「阿实」人格 + 三条铁律 + **档案模式"少说多列"切换声明**（用于化解人设轻快与档案详尽之间的张力）；
- 能力层：新增 `skills/person_dossier.yaml`，承载八维清单与三步编排规则；
- 路由：内存姓名索引（`user.real_name` ∪ `laboratory.manager`，约 40 个字符串），按**长度降序最长子串匹配**——降序是"刘思 vs 刘思思"不误伤的唯一原因。
- 配套修复：`Skill.allowed_tools` 原本仅作提示文本、不参与执行，本次让它**真正生效**（命中路由后收紧工具集，同时省掉无关 schema 的 token）。

### D10 改动范围与数据策略
**结论**：
- **范围**：必需改造 + **只堵 `user` 表通道绕过这一个洞**。其余既有缺陷（`sql_validator` 的 `version` 子串误杀、`query_service` 空结果丢列、MCP 零身份作用域）记录在案、本轮不动。
- **通道绕过的必要性**：新档案工具会做"教师只能查自己"的断言，但 `query_data` 旧通道里 `user` 在 `PUBLIC_TABLES`（`permission.py:27`），教师一句 `SELECT username, real_name, phone, password FROM user` 至今可拖走全员数据。同一边界两条通道，**只修一条等于没修**。
- **数据**：只跑 `scripts/parse_schedule_time.py` 回填课节（8 行，已验证可解析）；足迹只呈现真实的 `query_history`，**不伪造** `notification` / `operation_log` 审计流水；稀疏档案在卡片上明写"暂无记录"。演示主角锁定 **韩艳(33)** 与 **张伟(2)**。
- **收尾两条**：① 检索终端新增「我的档案」按钮，直接锁定 `person_ref` 为当前用户、省掉 resolve 一轮；② `notification` 维度本轮**占位**（显示"系统尚未启用通知"），不为 0 行表做完整工具。

---

## 4. 边界定义（写死，不可越）

**① 实体边界**：本系统"人员"仅为 ADMIN / TEACHER。`experiment.experimenter_type` 是"本科生/研究生"字典值，**不是人**。系统无学生档案，阿实应如实说明而非硬编。

**② 权限边界**：教师 → 本人；ADMIN → 全体；挂名人员 → 仅 ADMIN、仅实验室一维。该边界必须由**档案工具与 `query_data` 两条通道同时守住**。

**③ 详尽度边界**：详尽体现在**行的完整性**，不体现在维度的可发现性。八维是闭集、由代码枚举；维内返回全量行（受 `MAX_ROWS` 保护）。**模型负责指路，不负责压缩。**

---

## 5. 验收标准

1. ADMIN 查「韩艳」→ 3 轮内出八区块；课节/实验/身份/任课/排课有货，实验室与足迹显示"暂无记录"而**非区块消失**。
2. ADMIN 查「杨彬」→ 挂名档案：1 个实验室 + 其余七维"不适用（无系统账号）"。
3. ADMIN 查「刘思」→ **只反问、不给任何业务数据**，候选带工号。
4. 教师（韩艳）查「张伟」→ 拒绝；查「韩艳」→ 得本人档案，手机号明文不进模型。
5. 安全回归：教师经 `query_data` 执行 `SELECT real_name,phone,password FROM user` → 必须 `PermissionRejectError`。
6. 单测：注入 fake `chat_fn` 只发 3 个工具 → 断言自动补一轮，`missing_dimensions` 正确。
7. 时延：全档案 P95 < 25s（3 次 LLM 往返，距前端 60s 有 2.4 倍余量）。

---

## 6. 遗留与风险

| 项 | 说明 | 处置 |
|---|---|---|
| 17 个孤儿 manager 姓名 | 是否建占位账号 / 清洗数据，属业务判断 | 记录在案，本轮用"挂名档案"承载 |
| `notification` / `operation_log` 全空 | 功能就绪但无数据；`notification` 连写入路径都没有 | 本轮占位，不伪造审计流水 |
| 人时数语义 | `SUM(total_hours * student_count)` 是**学生学时**，非教师工作量 | 卡片上如实标注，禁止表述为教师工作量 |
| 两份业务查询逻辑 | 档案 SQL 写在 Python，与 Java `ReportMapper.xml` 存在漂移风险 | 集中单文件 + 每段注释指向对应 mapper 行号 + 加对账测试 |
| 既有缺陷（未修） | `sql_validator` 子串误杀；`query_service` 空结果丢列；MCP 零身份作用域 | 本轮不动，留待后续 |

---

## 7. 实施与验收补记（2026-09-14）

**执行环境**：真实四层链路（Chromium → Vite 8000 → Spring Boot 8080 → FastAPI 8001 → DeepSeek → MySQL 3307），无 mock。

### 7.1 验收逐条结果

| # | 验收标准 | 结果 | 证据 |
|---|---|---|---|
| 1 | ADMIN 查「韩艳」出八区块 | ✅ 8 卡 11.7s | 阿实悬浮球实测，完整手机号 13349957310 上屏 |
| 2 | ADMIN 查「杨彬」挂名档案 | ✅ 1 实验室（36-601）+ 七维「不适用」 | 9 卡，无工具报错卡 |
| 3 | ADMIN 查「刘思」只反问 | ⚠️ 现场不可复现 | 演示库无重名账号；刘思/刘思思 均在 `laboratory.manager`，现场走挂名档案；ambiguous 分支由单测（撞名 fake）+ 冒烟脚本覆盖 |
| 4 | 教师查「张伟」拒绝 / 查「韩艳」得本人档案 | ✅ | 拒绝回复「教师身份仅可查询本人档案」；本人档案 4.0s、手机号打码 `133****7310`、足迹降级「仅管理员可见」 |
| 5 | `user` 表越权回归 | ✅ | `tests/test_permission.py::test_teacher_user_table_rejected`（PermissionRejectError） |
| 6 | 完成度补漏单测 | ✅ | `test_missing_dimension_triggers_one_nudge_then_report` 等 3 例 |
| 7 | P95 < 25s | ✅ | 本人档案 3.9~4.5s；管理员查他人档案 11.6s |

单测：`131 collected / 130 passed / 1 failed`，唯一失败为环境性——`.env` 设 `DEEPSEEK_MODEL=deepseek-v4-flash`，而 `test_deepseek_client.py` 硬编码断言 `deepseek-chat`；与本次改动无关，未动。

E2E 脚本：`.tools/e2e_dossier_test.cjs`（可重复执行）；截图：`.tools/e2e-*.png`。

### 7.2 验收暴露并修复的缺陷（均在本轮范围内）

| 缺陷 | 现象 | 根因 | 修复 |
|---|---|---|---|
| 「我的档案」按钮静默失效 | 前端 `self_dossier: true` 在 Java 一跳丢失，走成普通问答（问「我」是谁 → not_found） | `NlqRequest.selfDossier` 未声明 Jackson 绑定，snake_case 字段被丢弃 | 字段加 `@JsonProperty("self_dossier")` |
| 完成度断言把模型逼进死胡同 | 教师查他人被拒后，系统仍要求补齐八维，模型输出一段英文自我辩解的废话 | `agent_loop` 的 `required_dimensions` 不随 resolve 结局收窄 | resolve 非 exact（denied/not_found/ambiguous/manager_only）时断言自动失效；exact 与锁定入口行为不变 |
| 挂名档案取不到实验室 | `person_managed_labs` 拒绝姓名，挂名维报「未找到人员」 | resolve 的 manager_only 分支不下发 `person_ref`，工具仅认工号/id | manager_only 时 `model_view.person_ref` = 挂名姓名；该工具对无账号姓名回退 `laboratory.manager` 精确匹配（仅 ADMIN，教师仍拒绝） |

### 7.3 交付清单

- 后端：`person_tools.py`（9 工具）+ `dossier_sql.py` + `intent_router.py`（姓名索引最长匹配）+ `agent_loop.py`（双份载荷/断言/锁定）+ `agent_service.py`（路由与自锁）+ `routers/nlq.py`（`self_dossier`）+ `skills/person_dossier.yaml` + `prompt_builder.py`（阿实人格与档案模式）；Java：`NlqRequest` / `NlqService` 透传。
- 前端：`PersonDossier.vue`（八维分区卡，终端与悬浮球共用）+ `NlqPage.vue`（「我的档案」按钮与锁标识）+ `NlqWidget.vue`（档案卡上屏）+ `api/nlq.ts` / `types/api.d.ts`。
- 测试：`tests/test_person_dossier.py`（27 例）+ `test_permission.py` / `test_agent_service.py` 扩充。
