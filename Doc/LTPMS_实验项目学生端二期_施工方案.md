# LTPMS 实验项目 学生端二期（锁③）— 施工方案

> 版本：V1.0（首版） ｜ 状态：**暂不实施**（施工方案冻结待命；开工需用户单独下令） ｜ 拍板日期：2026-09-30
> 依据：锁③定稿（2026-09-21）＋ AI 角色重界定（2026-09-21）＋ 2026-09-30 拷问 Q9=B「两刀切分」
> 定位：本文档 = 未来实施会话的执行底稿（文件级改动 + 验收计划 + 排期）；执行流程沿用 scoped-issue-fix（先方案后编码、严格限定范围）。
> 关联：实施 gates 见 §8；第一刀范围已含「角色重界定」三项收尾中的免责与限流默认。

---

## 1. 决策基线

**锁③（学生 AI 五技能）定稿**：
- 可见范围 = **项目汇总 + 本人明细**（沿用报表 `aggregate_only` 分层心智）；
- 入口 = **悬浮球 + 项目页内嵌双开**；
- 首批 5 技能 = `my_briefing` / `task_steps` / `submit_precheck` / `concept_explain`（带资料出处）/ `troubleshoot_guide`（苏格拉底式排障）；
- 红线 = **不代写 / 不代做 / 不代交 / 不预测分数**；
- 结构性前提 = 服务端注入身份段 + 硬约束「他人名下行不得进本人清单」（`lab_project_member` 只有 student_id 一列，模型无从自知身份，不做必错——回滚教训③）。

**AI 角色重界定（同批生效）**：
- 身份 B「实验教学 AI 助手」；口径 **B2**（域内为主·域外不拒；**凡涉本校/本系统事实一律先查库，查不到直说、禁止模型记忆补造**）；
- **C1 本批不联网**（观察 2~4 周「答不了」占比 >15% 且集中于时效话题才议白名单——未来会话必须遵守）；
- **D2 有据才打标**（系统库挂「系统数据」、上传文档挂出处；有标=有据、无标=仅供参考）；
- **E2 学生分级解放**：知识照答、方法照给；**生成性任务只给脚手架**（框架 + 要点 + 自查清单），边界靠"改写请求"而非"拒绝"。

**Q9 切分（两刀）**：
| 刀 | 范围 | 性质 |
|---|---|---|
| **第一刀** | 开闸 + 身份注入 + `concept_explain` + `my_briefing` | 知识照答 + 本人清单；把安全模型（他人数据绝不进本人清单）钉死 |
| **第二刀** | `task_steps` + `submit_precheck` + `troubleshoot_guide` | 三件生成性技能；借第一刀试运行反馈定边界话术 |

---

## 2. 现状（三道闸）与已有底座

**学生端目前是「全关」状态**：
1. **网关硬闸**：`SecurityConfig` 学生可达白名单不含 `/api/v1/nlq/**` → 学生调 NLQ 任何端点 403（r2-H1 收口口径）。
2. **前端闸**：`BasicLayout.vue` 的 `<NlqWidget v-if="!userStore.isStudent" />` 隐藏悬浮球；学生菜单只有「我的任务 / 实验项目」。
3. **服务端闸**：`permission.py` 无学生规则（注释「student / course_student 一期不入白名单」）；技能库 5 个全是教师/管理向，学生技能 0 个。

**已有底座（开闸后可直接站）**：
- `knowledge_search` 已学生感知（`course_student → student.user_id` 过滤）→ `concept_explain` 检索底子现成；
- `agent_loop` 已做服务端身份注入（LLM 传来的 role/user_id 一律被服务端覆盖）→ 只差提示词与工具层硬约束；
- 晨报 `MorningBriefService.generateForStudent` 已在跑（3 天内到期 + 逾期、无待办不发）→ `my_briefing` 口径可直接搬；
- `ProjectAuthService` 学生读分支（isMember via `lab_project_member`）已存在 → 项目页内嵌入口行级权限现成；
- 回滚备份（`.tools/rollback-backup-20260920/`，gitignored）里的学生路线实现可参考，**但口径按本方案、不照抄**（回滚教训 5 条见附录）。

---

## 3. 总体设计要点

- **四层安全**（自上而下，任一层单独成立）：
  1. 网关：学生只放行 `/api/v1/nlq/agent` 一个端点；
  2. 技能装配：技能带 `roles` 声明，学生只挂载学生技能，工具 allowlist 收敛；
  3. 执行侧：`_execute_tool` 校验 allowlist（暴露面外工具名直接拒绝——**同时加固教师识图的知识限定路径**）；
  4. 权限层：`permission.py` 对学生显式拒绝（即使被绕过前两层也执行不了 SQL）。
- **身份注入落法**：学生分支置于 `agent_service` **最前**（先于 self_dossier 与人名路由）——学生请求彻底不进人名路由、`self_dossier` 对学生无效；工具层以 `user_id` 服务端绑定本人，模型无身份入参通道（见 §4.6 口径差异登记）。
- **单一实例入口**：悬浮球与项目页入口共用**同一个** `NlqWidget` 实例（回滚教训①：双实例分裂状态）；开合状态与上下文放 Pinia 单例。
- **context 透传链**：`ProjectDetailPage` 设 context（仅提示词框架，**不构成权限**——伪造 project_id 也只见本人数据）。

---

## 4. 第一刀施工方案（详细）

### 4.1 开闸（Java，唯一安全敏感点）

| 文件 | 改动 |
|---|---|
| `SecurityConfig.java` | 学生白名单 matcher 追加 **`/api/v1/nlq/agent`**（须在「其余 `/api/v1/**` 仅 ADMIN/TEACHER」matcher 之前） |
| `NlqController.java` | 仅注释更新：`/agent` 标注「学生可用；写回不经此入口」 |
| `NlqService.java` | 零改动（限流、`X-User-Id / X-User-Role` 注入已按 userId 工作） |

**不开**：`/query`、`/history`、`/archive`、`/narrative`、`/recognize-schedule`、`/image-query`、`/schema` —— 学生访问保持 403，验收时逐一敲门验证。

### 4.2 角色化底座（nlq-service）

| 文件 | 改动 |
|---|---|
| `skill_loader.py` | `Skill` 增可选 `roles`（yaml 缺省 = `("ADMIN","TEACHER")` **fail-closed**；存量 5 个技能文件**零改动**）；新增按角色过滤 |
| `agent_service.py` | 增 **STUDENT 分支，置于最前**：按 roles 过滤技能、allowlist = 学生技能工具并集、`required=()`、`forced_ref=None`；跳过人名路由与 person_dossier |
| `agent_loop.py` | `_execute_tool` 增 allowlist 执行侧校验（非暴露工具名拒绝执行） |
| `permission.py` | STUDENT 显式 `raise PermissionRejectError`（知识检索走独立学生化 SQL，不经此函数，不受影响） |
| `prompt_builder.py` | 追加学生模式段：角色认知 / 红线（不代写·不代做·不代交·不预测分数）/ 本校事实禁猜（B2）/ 知识带出处（D2）/ 生成性只给脚手架（E2）/ 安全操作以实验室规程与教师指导为准 / 免责一句 |

**query_data 对学生三层关闭**：① 不进 allowlist（看不见）→ ② 执行侧校验（看得见也执行不了）→ ③ permission 显式拒绝（绕过前两层也查不了）。

### 4.3 两个学生技能

| 文件 | 内容 |
|---|---|
| `skills/concept_explain.yaml`（新） | `roles: [STUDENT]`；`allowed_tools: knowledge_search`；E2 分级（知识照答、方法给脚手架、生成性只给框架）+ 资料出处 + 通用知识声明「非课程指定」 |
| `skills/my_briefing.yaml`（新） | `roles: [STUDENT]`；`allowed_tools: student_briefing`；prompt 规定「**逾期先算后说**」（先与 `NOW()` 比再开口，回滚教训④） |
| `app/services/student_tools.py`（新） | 工具 `student_briefing`：**无身份入参**，`user_id → 本人 student.id` 服务端绑定；取 `assignee_student_id=本人 AND deleted=0 AND status ∉ {ACCEPTED,CLOSED}` 按 ACTIVE 课题分组；Python 侧算 `days_left / overdue`（`deadline<NOW() AND status∈{NOT_STARTED,IN_PROGRESS}`，与 `ProjectStatsService` 同口径）/ `due_soon`（≤3 天，与晨报同口径）/ 待验收（最新提交 `reviewed_at IS NULL`）；每课题附聚合块（成员数 / 任务数 / 已验收 / 逾期）；**绝无学号、姓名、他人行** |
| `app/main.py` | lifespan 注册 `register_student_tools()` |

**数据隔离三保险**：工具无身份通道 + SQL 硬绑定本人 + 双学生夹具断言互不可见。

### 4.4 入口与上下文

| 文件 | 改动 |
|---|---|
| `BasicLayout.vue` | 去掉 `v-if="!userStore.isStudent"`，悬浮球学生可见 |
| `store/nlq.ts`（新） | setup-store：`open` + `context`（**单一实例**的开关与上下文归属地；模板照 `store/user.ts`） |
| `NlqWidget.vue` | open 状态改读 store；按角色选示例问题（新增学生版 3 条）；学生模式顶部一行静态免责小字；429 文案沿用 |
| `ProjectDetailPage.vue` | 头部「问阿实」按钮（**学生可见、教师暂不加**〔可推翻〕）→ `openWithContext({project_id, project_name})`；卸载清 context |
| `api/nlq.ts` | `apiNlqAgent` 增可选 context 参数 |
| `NlqRequest.java` / `NlqService.java` / `routers/nlq.py` / `agent_service.py` | `context` 透传（Java DTO → forwardAgent → `AgentRequest` → 学生提示词上下文段） |

### 4.5 收尾项落地（角色重界定「免责 / 限流」）

- **限流**：沿用现有 Java 用户级 RateLimiter，**零新代码**；已知实测为 **1 请求 / 6 秒零突发**（`maxBurstSeconds=1` 特性），学生多轮追问会出现 429——本轮如实呈现（文案就位）；可选令牌桶调参（+0.5d）标「可推翻」，先按现状交付。
- **免责**：学生 prompt 段 + 悬浮球学生模式一行小字（无新增状态）。
- **安全让位**：prompt 段写「涉及实验安全操作，以实验室规程与教师指导为准」。

### 4.6 与锁③原文的口径差异（⚠️ 需老板过目后生效）

锁③写「注入 student id=X + 硬约束他人行不进清单」。落地改为：**prompt 身份段不含原始 id**（学号/主键不进 LLM 上下文，与人员档案隐私红线同口径），**硬约束从提示词下沉到工具层结构性绑定**（工具无身份入参、SQL 服务端绑本人）。
效果更强：原文靠提示词约束模型，落地版连查询通道都不给模型——回滚教训③的根因（模型误归属他人任务）被**结构性消除**。

### 4.7 第一刀文件级改动清单（19 项）

| # | 文件 | 改动 |
|---|---|---|
| 1 | `backend/.../SecurityConfig.java` | 学生白名单追加 `/api/v1/nlq/agent` |
| 2 | `backend/.../NlqController.java` | 注释更新 |
| 3 | `backend/.../dto/NlqRequest.java` + `NlqService.java` | context 透传 |
| 4 | `nlq-service/app/services/skill_loader.py` | `Skill.roles` + 角色过滤 |
| 5 | `nlq-service/app/services/agent_service.py` | 学生分支（最前）+ context 段 |
| 6 | `nlq-service/app/services/agent_loop.py` | 执行侧 allowlist 校验 |
| 7 | `nlq-service/app/services/permission.py` | STUDENT 显式拒绝 |
| 8 | `nlq-service/app/services/prompt_builder.py` | 学生模式段 |
| 9 | `nlq-service/skills/concept_explain.yaml`（新） | 技能定义 |
| 10 | `nlq-service/skills/my_briefing.yaml`（新） | 技能定义 |
| 11 | `nlq-service/app/services/student_tools.py`（新） | `student_briefing` 工具 |
| 12 | `nlq-service/app/main.py` | 注册学生工具 |
| 13 | `nlq-service/app/routers/nlq.py` | `AgentRequest.context` |
| 14 | `frontend/src/layouts/BasicLayout.vue` | 学生可见悬浮球 |
| 15 | `frontend/src/store/nlq.ts`（新） | open + context 单例 |
| 16 | `frontend/src/components/NlqWidget.vue` | 读 store / 学生示例 / 免责小字 |
| 17 | `frontend/src/pages/project/ProjectDetailPage.vue` | 「问阿实」按钮 + context 注入/清理 |
| 18 | `frontend/src/api/nlq.ts` | context 参数 |
| 19 | 测试：pytest 新增 + `.tools/verify_student_ai.py`（新） | 见 §6 |

---

## 5. 第二刀规划（`task_steps` / `submit_precheck` / `troubleshoot_guide`）

三件均为**生成性技能**，主体是提示词工程（yaml + 如需在 `student_tools.py` 扩只读工具）；边界话术借第一刀 2~4 周试运行反馈定稿：

| 技能 | 定位 | 边界话术要点 | 复用工具 |
|---|---|---|---|
| `task_steps` | 任务拆解脚手架 | 给步骤框架 + 每步要点 + 自查清单；**不给成品内容**；任务信息来自本人任务数据 | `student_briefing` + `knowledge_search` |
| `submit_precheck` | 提交前预检清单 | 按任务/数据集生成「交前自查项」；不评判对错、不预测验收结果 | `student_briefing` + `knowledge_search` |
| `troubleshoot_guide` | 苏格拉底式排障 | **给思路不给答案**：先问诊（现象/已试什么）→ 逐步引导定位；安全类问题一律让位规程与教师 | `knowledge_search` |

风险最高的是 `troubleshoot_guide`（边界漂移），故排在试运行反馈后定稿；三技能不新增写通道、不碰任何数据表。

---

## 6. 验收计划（第一刀）

- **pytest 新增 10~14 例**：
  - roles 缺省 fail-closed（存量 5 技能不受影响）/ 显式 `roles:[STUDENT]` 解析；
  - 学生分支：跳过人名路由（「某某的任务」不触发 person_dossier）、`self_dossier` 对学生无效、allowlist 收敛；
  - `_execute_tool` 拒绝 allowlist 外工具名；
  - `permission.py` STUDENT 显式拒绝；
  - `student_briefing`：双学生夹具互不可见、`deleted=0`、逾期按 `NOW()` 口径、SUBMITTED 待验收口径、无学号姓名；
  - 学生 prompt 段出现且不含 id。
- **基线**：pytest 171 + 新增全绿；mvn 23 全绿（Java 仅白名单一行 + DTO 字段）。
- **`.tools/verify_student_ai.py`**（照 `verify_group.py` 约定，可重放幂等）：
  - 学生（dev 夹具：user 58 / 20230203）正常问 → 只见本人任务（夹具：任务 26/27/28，**不见王娟的任务 25**）；「王娟的任务」不返他人数据；
  - 逐个敲不开的 nlq 端点（`/query /history /archive /recognize-schedule /image-query`）→ 403；
  - 正例请求间隔 **≥6s** 防误触 429；限流本身单独断言；
  - 教师回归抽 2 例；HTTP 200 + `body.code` 断言；验证 SQL 一律 `deleted=0`。
- **浏览器验收**（真实 UI，防入口/状态登记陷阱）：`npm run build` + 学生 token 注入（学生默认密码受改密弹窗拦截）→ 悬浮球可见、简报含逾期样例、项目页「问阿实」带上下文、折叠再展开状态保持（单实例验证）；教师回归无串扰；截图留 `outputs/`。
- 夹具备忘：项目 14（容器 LP-2026-002）、任务 25（王娟）/26/27/28、提交 11/12/13、数据集 9；学生 13=user 58、学生 11=user 56。

---

## 7. 排期

| 刀 | 步骤 | 估时 |
|---|---|---|
| 一 | ① 开闸 + 角色化底座（4.1/4.2） | 1d |
| 一 | ② 两技能 + student_tools + 注册（4.3） | 1d |
| 一 | ③ context 透传（4.4 Java+Python 侧） | 0.5d |
| 一 | ④ 前端入口（4.4 前端侧） | 1d |
| 一 | ⑤ 测试（pytest + verify 脚本） | 0.5~1d |
| 一 | ⑥ 浏览器验收 + 基线复跑 + 截图 | 0.5d |
| 一 | **小计** | **≈4.5~5 人日** |
| 二 | 三技能 yaml + 边界话术定稿 + 单测 + 浏览器抽查 | ≈1.5~2.5 人日（依赖第一刀试运行反馈） |

完成后按仓库惯例分堆提交（**不 push** 除非另行授权）。

---

## 8. 前置与待补（开工 gate）

1. **实施令**：开工需用户单独下令（本方案不构成开工授权）。
2. **角色重界定三项收尾**：
   - 免责 → 已给默认（§4.5），实施即生效、可推翻；
   - 成本与限流 → 已核现状（1/6s 零突发风险）；令牌桶调参与否实施前定；
   - **成功标准 → 待定稿**（建议默认：安全事件 0——他人数据泄漏零发生；观察 2~4 周学生提问活跃度与 429 发生率，作为后续开闸/调参依据）。
3. **未决项登记**：学生仪表盘（当时提过可捎带，本刀不含）；`/image-query` 学生开闸（Java 注释「全角色可用」但网关拦——本二期不含）。

---

## 9. 明确不做

学生识图（image-query）、学生 NL2SQL、`/query` 与 `/history` 开放、生成性三技能（第一刀内不做）、Agent 直接写库（红线永续：写意图终止于人工确认）、预测分数/排名类任何能力。

---

## 附录：回滚教训（2026-09-20 V1.2 删档时留档，禁重踩）

1. 同页双 `NlqWidget` 实例会分裂状态 → 状态必须模块级共享（本方案 §3 已落）。
2. ADMIN 只读不能只贴标签：行内控件要一并禁用，后端再兜 403。
3. 学生身份：`lab_project_member` 只有 student_id 一列，模型无从自知 → 身份注入 + 他人行硬约束是结构性前提（本方案改为工具层绑定，见 §4.6）。
4. `my_briefing` 类技能必须「逾期先算后说」（先 `NOW()` 再比 deadline）。
5. 验收脚本对照 SQL 一律带 `deleted=0`（软删行会被 API 正确排除，脚本不排会假 FAIL）。
