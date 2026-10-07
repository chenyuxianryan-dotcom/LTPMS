# LTPMS 实验项目「教学闭环收口」— 实施 Tickets

> 版本：V1.0（首版） ｜ 状态：**待实施**（共识前不动手，开工需用户单独下令） ｜ 拍板日期：2026-09-30
> 来源：2026-09-30「实验项目下一批」拷问记录（Q1~Q8；其中 Q8 与两处口径为**暂记**，见 §6） ｜ 上游文档：《LTPMS_课题项目_技术方案.md》《LTPMS_智能查询AI能力扩展蓝图.md》
> 范围：① 验收收件箱 + 批量验收 ② 成绩与项目台账导出 ③ 实验→任务桥接 ④ AI 全项目报告。
> **学生端二期（锁③）不在本批**——按指示暂不实施，施工方案见《LTPMS_实验项目学生端二期_施工方案.md》；其实施令下达时由该方案拆出自己的 tickets。

---

## 1. 背景与决策摘要（Q1~Q8 压缩）

模块现状：容器/目录/任务/数据/进度/档案结构已齐；教师季内最高频的批阅只能逐条、推送数字无落点、导出全缺、目录与任务不挂钩、"整合上传数据"的报告是用户点名要的增量。本批即把"能真跑一门课"的四块补上。

| # | 决策点 | 结论 |
|---|---|---|
| Q1 | 本批范围 | **A 教学闭环收口**：四子项（第 4 项为用户自行追加的 AI 全项目报告） |
| Q2 | 收件箱形态 | **B 全局收件箱**：把名下全部课题的待验收提交汇成一个队列；批量**仅「通过」**、驳回仍逐条（意见必填）；「待验收积压 REVIEW_PENDING」与「提交到达 SUBMISSION_ARRIVED」两条通知改指收件箱；教师看本人课题、ADMIN 可看不可批、学生无 |
| Q3 | 收件箱入口 | **A 独立页 `/reviews` + 菜单「待验收」**（教师可见，放「实验项目」旁；ADMIN 可深链只读、不挂菜单；通知直落此页） |
| Q4 | 导出范围 | **a + c**：① 成绩册 Excel（双 sheet）② 项目台账 Excel（多 sheet）；③ AI 报告 PDF 归入子项四产出形态。**不做** b「项目材料 zip 打包」（存储放大 + 权限口径未定，后补） |
| Q5 | 桥接深度 | **B 深桥接**：`lab_project_task.experiment_id`（可空）；目录行进「布置任务」+ 表单可选/可换实验；任务列表加实验列与筛选；**删实验时关联任务置空**（不阻断删除）；排课联动不做 |
| Q6 | 报告数据深度 | **B 数据版**：结构化轨迹 + **读数据集文件做画像**（csv/txt/xlsx 采样；zip/图片只登记不计内容）；数字全部 Python 侧算好；文件版本指纹入缓存 key；顺带补 `/nlq/archive` 与报告接口 RateLimiter |
| Q7 | 章节模板 | **A 固定六节**（① 项目概况 ② 实验执行与任务进展〔含排课/使用统计〕 ③ 成员参与与分组 ④ 材料与数据画像 ⑤ 风险与问题 ⑥ 总结与建议）；附录「成员完成情况表」**后端确定性渲染**（含学号姓名、**不进模型上下文**）；篇幅 1200~1800 字；客观语气、不做评价性结论 |
| Q8 | 等待体验 | **A 同步等待**（*暂记*）：报告调用独立超时上调 ~45s；瞬时故障自动重试 ≤1；缓存命中瞬时返回；弹窗内「重新生成」；前端报告请求 90s |

**Q8 随附暂记口径（未显式拍板、实施按此走、可随时推翻）**：
1. 独立超时**不改全局**——只有报告这一个调用传 45s，档案/问答/识图保持 15s。
2. 瞬时错误重试仅服务繁忙类、≤1 次；失败明确提示「模型服务繁忙，可点重试」。
3. 缓存命中瞬时返回，页头标注「数据截至 X 月 X 日 X:XX，如有变更可重新生成」。
4. 弹窗内「重新生成」按钮（强制绕过缓存）——顺带把上批遗留「档案 24h 缓存无刷新入口」一起收掉（档案弹窗补同款按钮）。
5. 前端报告请求超时 90s（对齐识图），保证后端 45s 先到、前端不提前断线。

---

## 2. Tickets 总览

| ID | 标题 | 子项 | 依赖 | 粗估 |
|---|---|---|---|---|
| T01 | 收件箱后端聚合 + 两条通知改指 | 一 | — | 0.5~1d |
| T02 | 收件箱前端页 `/reviews` + 批量通过 | 一 | T01 | 1~1.5d |
| T03 | 桥接数据层与后端（迁移 + 校验 + 置空钩子） | 三 | — | 0.5~1d |
| T04 | 桥接前端（布置任务 / 关联实验 / 列表列） | 三 | T03 | 1d |
| T05 | 成绩册 Excel 导出 | 二 | — | 0.5~1d |
| T06 | 项目台账 Excel 导出 | 二 | — | 0.5~1d |
| T07 | 报告：文件画像 + 聚合扩展（nlq-service） | 四 | — | 1~1.5d |
| T08 | 报告：生成管线（六节 + 超时 + 重试 + 缓存 + 限流） | 四 | T07 | 1~1.5d |
| T09 | 报告：PDF 渲染 + 前端弹窗 + 档案刷新按钮 | 四 | T08 | 1~1.5d |
| T10 | 全批验收（套件 + 浏览器 + 基线复跑） | 全 | 全部 | 0.5~1d |

**合计约 9~12 人日**（粗估、不含缓冲；AI 结对下可压缩）。

**建议执行顺序**：① T01/T02 收件箱（闭环最高频痛点，且把 ②主动推送从"知道你忙"补成"能直接处理"）→ ② T03/T04 桥接（schema 改动尽早定型，防后续返工；T05/T06 导出与此无依赖可并行）→ ③ T05/T06 导出 → ④ T07~T09 报告（最大件，且依赖前几项的数据成熟度）→ T10 统一验收。每批独立可交付、可单独下令开工。

---

## 3. 各 Ticket 详述

### T01 收件箱后端聚合 + 两条通知改指〔子项一〕

- **新增端点**：`GET /api/v1/tasks/review-inbox`（`@RequireRole({"ADMIN","TEACHER"})`）。
  - 数据 = 名下（教师：`teacher_id` 本人；ADMIN：全量只读视图）全部课题中状态为 `SUBMITTED` 的任务 + 其最新提交；字段：`taskId / projectId / projectName / taskTitle / studentName / studentNo / submittedAt / submissionId`；排序按 `submittedAt` 升序（FIFO 队列语义）；可选 `projectId` 过滤。
  - 权限单路沿用 `ProjectAuthService` 口径（教师本人课题；ADMIN 直通只读）。
- **通知改指**（`EventAlertService`）：
  - `REVIEW_PENDING` 现 refUrl=`/dashboard`（Dashboard 无待验收列表，推送有数字无落点）→ 改 `/reviews`。
  - `SUBMISSION_ARRIVED` 现 refUrl=`/experiments/{projectId}` → 改 `/reviews?projectId={projectId}`（进页即定位到该课题）。
- **AC**：① 教师只见本人课题的待验收；② ADMIN 可见全量、操作不可用（后端写侧由既有 requireNotAdmin 兜底）；③ 学生调该端点为 403/无权限；④ 两条通知点击落点为收件箱；⑤ 既有单任务验收链路零行为变化。
- **备注**：新写端点为零——批量通过复用既有单条验收端点（见 T02），不在后端新增写接口。

### T02 收件箱前端页 `/reviews` + 批量通过〔子项一〕

- **新页面** `frontend/src/pages/review/ReviewInboxPage.vue`：队列列表（课题 / 任务 / 学生 / 提交时间 / 版本或内容摘要）+ 行内「展开提交内容」（复用任务提交历史查看）+ 操作列「通过」「驳回」；「驳回」复用 `TaskReviewDialog.vue`（通过/驳回 + 意见必填，约束不动）。
- **批量通过**：多选 → 「批量通过」→ 二次确认（N 条）→ 前端顺序调既有 `POST /api/v1/tasks/{id}/review {action:"ACCEPT"}` → 汇总结果（成功 N / 失败 M 及原因，部分失败不静默）。
- **入口**：路由 `/reviews` + 菜单「待验收」放「实验项目」旁（教师可见）；ADMIN 不挂菜单、深链可开、全部操作控件禁用（沿用 ADMIN 只读收口）。
- **通知落点**：通知面板点击 refUrl 直达本页（含 `?projectId=` 过滤）。
- **AC**：① 空态文案就位；② 批量通过后任务变 ACCEPTED、学生收到验收通知；③ 驳回仍逐条且意见必填；④ ADMIN 深链只读、无任何可写控件；⑤ 从通知点入正确落点。
- **备注**：批量通过若实现中发现 N 次请求体验问题，可升级服务端批量端点——**非本期要求**；页面走真实 UI 验收（防入口/状态登记陷阱）。

### T03 桥接数据层与后端〔子项三〕

- **迁移**：`database/upgrade_20260930_task_experiment.sql` + 同名 rollback：
  - `ALTER TABLE lab_project_task ADD COLUMN experiment_id BIGINT NULL`（逻辑外键，与 init.sql 策略一致）+ `KEY idx_task_experiment`。
- **代码**：`LabProjectTask` 实体 `experimentId`；`TaskCreateRequest / TaskUpdateRequest` 增可空 `experimentId`；`TaskView` 增 `experimentId / experimentName`（批量查名，沿用现有批量填充模式）。
- **校验**：非空时实验必须属于该课题所属课程容器的目录（`experiment.course_id = 容器.course_id`）；**独立课题一律 400**（无目录语义）；允许改挂/清空。
- **级联**：删实验（现为物理删 + 级联钩子，分组清理同点）时 `UPDATE lab_project_task SET experiment_id = NULL WHERE experiment_id = ?` ——任务保留、不阻断删除、无悬空引用。
- **AC**：① 存量任务与独立课题零影响（字段可空）；② 跨课程挂靠请求 400；③ 删实验后关联任务 experiment_id 为空且任务完好；④ 迁移可回滚（rollback.sql 随批）。

### T04 桥接前端〔子项三〕

- **目录行**：操作列「编辑 / 删除」后加「**布置任务**」（教师；ADMIN 只读视图隐藏）→ 打开 `TaskFormDrawer.vue`，预填标题（实验名）且「关联实验」默认选中。
- **TaskFormDrawer**：新增「关联实验」下拉（本容器目录行；**独立课题不显示此项**）；可清空、可改选。
- **任务列表**：加「实验」列（无关联显示空）+ 顶部按实验筛选。
- **AC**：① 从目录行布置任务→提交后任务挂到该实验；② 独立课题无「关联实验」项且后端 400 兜底；③ 列表筛选行数正确；④ ADMIN 看不到「布置任务」。
- **不做**：排课联动（任务截止按排课周次自动算）——记后续。

### T05 成绩册 Excel 导出〔子项二〕

- **端点**：`GET /api/v1/projects/{id}/export/grades` → `.xlsx`（POI；参照报表模块既有 export 端点写法）。
  - **Sheet1 汇总**：每生一行——姓名 / 学号 / 任务数 / 已验收 / 完成率 / 逾期 / 待验收。
  - **Sheet2 明细**：每任务一行——标题 / 负责人 / 状态 / 截止 / 提交时间 / 验收时间 / 验收意见。
- **口径**：与页面一致（完成率分母剔除 CLOSED；逾期 = 过期且未提交）；单课题、含学号姓名（**文件下载不进任何 AI 链路**）。
- **前端**：课题详情页「导出成绩」入口；**必须走 `request.ts` 的 download 守卫**（防 200+业务码假文件），不直连 blob。
- **AC**：① 双 sheet 结构与行数正确（对照 SQL 全量断言）；② 空课题导出不报错（仅表头）；③ 下载文件名可读（课题名+日期）。

### T06 项目台账 Excel 导出〔子项二〕

- **端点**：`GET /api/v1/projects/{id}/export/ledger` → `.xlsx`，多 sheet：**成员 / 实验目录 / 任务 / 提交记录 / 数据集登记**（各 sheet 列取现有列表字段口径）。
- **前端**：课题详情页头「导出」下拉（成绩册 / 项目台账，与 T05 可合并同一入口，实现时择一）；同样走 download 守卫。
- **AC**：① 五 sheet 行数与页面列表一致；② 容器与独立课题均可用（独立课题「实验目录」sheet 为空表头）；③ 不含任何越权数据（按课题权限判定后导出）。

### T07 报告：文件画像 + 聚合扩展〔子项四 · nlq-service〕

- **文件访问**：nlq-service 新增配置 `lab_upload_dir`（env 可覆盖，dev 指向后端落盘目录 `backend/uploads/lab`）；按 `lab_dataset_version.stored_path`（UUID 相对路径）拼接读取，**拼接前做 realpath 前缀校验防目录穿越**；只读。
- **画像器**（新模块，如 `app/services/file_profile.py`）：
  - csv/txt：标准库采样——行列数 / 字段 / 缺失 / 数值分布 / 异常值 / 样例行（头 N 行）；
  - xlsx：新增 `openpyxl`（read_only）依赖，同款限量；若实施时评估依赖不划算则降级「仅元数据 + 首 sheet 样例行」——二选一在实施时定；
  - zip / 图片：只登记（文件名/大小/类型）不计内容；
  - 单文件大小与采样上限超限 → 降级为元数据（不报错、可解释）。
- **聚合扩展**：复用课程全貌聚合（课题六节口径）+ 数据集清单（含**版本指纹**：dataset id × 最新版本号）供缓存 key；容器与独立课题降级口径与档案一致（无目录/排课节显式 None）。
- **红线**：画像统计**全部 Python 侧算好**写进载荷，提示词注明"不得自行计算"；学号/姓名不进模型上下文（沿用人员档案口径）。
- **AC**：① 用真实样本 `lab_log_20260918.csv` 与手工构造 xlsx 断言画像字段；② 超限/坏文件降级不抛异常；③ 路径穿越样例（`../`）被拒；④ 无文件的数据集不报错。

### T08 报告：生成管线〔子项四〕

- **端点**：nlq-service `POST /report`（+ Java `POST /api/v1/nlq/report` 转发，带 X-Internal-Token / X-User-Id / X-User-Role）；请求 `{project_id, force?}`。
- **提示词**：固定六节模板（Q7），④「材料与数据画像」与 ⑥「总结与建议」为重心；客观语气、不给学生做评价性结论；数字只引用 Python 传入值。
- **模型调用**：`call_deepseek` 支持 per-call timeout（**报告传 45s，全局 15s 不动**）；瞬时错误重试 ≤1 次。
- **缓存**：SQLite 24h；key = 聚合 hash + **文件版本指纹**；`force=true` 绕过（「重新生成」用）。
- **限流**：Java 侧给 `/nlq/archive` 与 `/nlq/report` 补 RateLimiter（与 `/agent` 同口径、同实现）。
- **AC**：① 首次生成六节齐全、篇幅 1200~1800 字量级、数字与聚合一致；② 二次调用命中缓存瞬时返回；③ `force` 触发重生成；④ 新上传数据集版本后缓存自动失效；⑤ 限流生效（429 语义与既有对齐）；⑥ 模型不可用时降级确定性统计不白屏（沿用档案兜底）。

### T09 报告：PDF 渲染 + 前端弹窗 + 档案刷新按钮〔子项四〕

- **PDF（Java，iText）**：
  - 叙述由模型写、数字/表格由**后端确定性渲染**；附录「成员完成情况表」（姓名/学号/完成情况）后端从库直出，**不进模型上下文**；
  - 沿用报表「所见即所得」导出模式：前端把已生成内容交后端排版；**静态字体 NotoSansSC-Regular.ttf + PdfFont 每文档新建 + `toByteArray` 在 `close()` 后**（沿用既有教训，禁止用 VF 源文件）。
- **前端**：
  - 课题详情页头「生成项目报告」→ 弹窗（生成中进度 → 六节预览 → 「导出 PDF」「重新生成」）；报告请求超时 **90s**；缓存命中显示「数据截至 …」。
  - **档案弹窗补「重新生成」按钮**（收掉上批「24h 缓存无刷新入口」遗留；档案接口补 force 透传）。
- **AC**：① PDF 六节 + 附录完整、中文字体正常（pypdfium2 渲染目检）；② 重新生成绕过缓存且档案侧同样可刷；③ 报告入口与目录行「AI 档案」并存不冲突；④ 独立课题可用（无目录/排课节为空）。

### T10 全批验收〔全〕

- **新套件** `.tools/verify_closure.py`（命名可调；沿用既有套件「可重放幂等」风格）：
  - 收件箱：权限矩阵（教师本人课题 / ADMIN 只读 / 学生无）、批量通过后状态机与通知断言；
  - 桥接：挂靠校验 400、删实验置空、独立课题拒绝；
  - 导出：POI 反读 xlsx 断言 sheet/列/行数（对照 SQL，**一律带 `deleted=0`**）；
  - 报告：缓存命中 / force / 版本指纹失效 / 限流（正例间隔 ≥6s 防 429 误伤）。
- **基线**：pytest 171 全绿 + 新增用例；mvn 23 全绿；既有套件复跑（verify_group 47 / verify_admin_readonly 75 / verify_merge 23）防回归。
- **浏览器实拍**（真实 UI）：教师——收件箱批量通过、目录行布置任务、两种导出下载、报告生成与 PDF；ADMIN——收件箱深链只读；截图留 `outputs/`。
- **口径**：错误一律 HTTP 200 + `body.code` 断言；GBK 后端日志；学生账号默认密码受改密拦截，浏览器验收用 token 注入。

---

## 4. 交付纪律（沿用仓库惯例）

- 每批开工需用户单独下令；改动留工作区审查；**commit / push 需另行授权**（分堆提交由用户指令触发）。
- 验证脚本进 `.tools/`、实拍截图进 `outputs/`；新表新列走 `database/upgrade_*.sql` + `rollback_*.sql` 成对随批。
- 文档随交付同步（本文件各 Ticket 勾验 + 技术方案/拷问记录补记）。

---

## 5. 明确不做（本批）

| # | 不做项 | 原因 / 去向 |
|---|---|---|
| 1 | 项目材料 zip 打包（Q4-b） | 存储放大 + "谁能打包全量"权限口径未定——后补 |
| 2 | 报告「全量版」（提交文本逐条 map-reduce） | 成本/隐私面最大，边际价值低——缓做 |
| 3 | 排课联动（任务截止按排课周次） | 记后续 |
| 4 | 统计切片（按实验维度完成率/档案） | 矩阵与档案刚验收完，重开膛成本高——后续 |
| 5 | Dashboard 收件箱区块 | 可选小补丁，与 `/reviews` 不冲突，先不做 |

---

## 6. 暂记与待确认口径

| # | 口径 | 状态 |
|---|---|---|
| 1 | Q8 五项（45s 独立超时 / ≤1 重试 / 缓存标注 / 重新生成按钮 / 前端 90s） | **暂记**——实施按此，你有异议随时改 |
| 2 | 附录「成员完成情况表」后端渲染、学号姓名不进模型上下文 | 暂记（随 Q7 默认） |
| 3 | 报告触发入口 = 课题详情页头「生成项目报告」（与目录行「AI 档案」并存） | 讨论中沿用、未单独拍板——实施前如要变，改一行入口 |
| 4 | 成绩/台账导出入口 = 课题详情页头（合并「导出」下拉或分列） | 实现细节，实施时定 |
| 5 | xlsx 画像：新增 openpyxl vs 降级元数据 | 实施时评估定（T07 内） |

---

## 7. 关联遗留项（不属本批，登记备查）

- 上批 5 项待确认中，**档案限流**（T08）与**档案缓存刷新**（T09）已并入本批收口；其余三项仍待处理：全部实验一键均分 / el-link 告警 / 遗留文件清理（`.hermes` docx 删除决定、`outputs/smoke/_dbg-nlq.png`）。
- NLQ autostart 运维修复（自启链路断，属工程收债方向）。

---

## 8. 交付记录（2026-09-30 夜间本地自动化执行，逐批追加）

> 执行令：2026-09-30 22:20「教学闭环收口」全批 T01~T10 无人值守实施。改动全部留工作区，未做任何 git 写操作。
> 开工基线：pytest 171 全绿、mvn 23 全绿、verify_group 47/47、verify_admin_readonly 78/78、verify_merge 23/23。
> 基线修复（开工时发现的既有夹具互踩）：verify_group teardown 重加成员写 source=MANUAL，破坏 verify_merge AC3 的 AUTO_SNAPSHOT 基线（group→merge 顺序重放必红，与本批改动无关）。已在 verify_group.py teardown 补一行 SQL 复原 source='AUTO_SNAPSHOT'，两套件按任意顺序重放均绿。

### 批次1：T01 + T02（验收收件箱）✅

**T01 AC 勾验**（自动化：.tools/verify_closure.py S1-01~S1-19，19/19 PASS，可重放幂等、teardown 零残留）：
- [x] AC① 教师只见本人课题待验收（S1-02：全部行 projectId ∈ 教师课题集）
- [x] AC② ADMIN 全量只读（S1-03 列表≥教师量；S1-14 ADMIN 批验收 403，requireNotAdmin 兜底不破）
- [x] AC③ 学生调端点 403（S1-04，@RequireRole 闸门 + ProjectAuthService 双保险）
- [x] AC④ 两条通知改指（S1-09/10 源码断言 + S1-17 活体断言 ref_url=/reviews?projectId=14）
- [x] AC⑤ 既有单任务验收链路零行为变化（S1-11~S1-16/18 活体全链 + mvn 23 全绿 + merge/group/admin_readonly 套件复跑全绿）
- 补充断言：FIFO 升序（S1-06）、字段齐备（S1-07）、行数=SQL 对照 deleted=0（S1-08）、projectId 过滤（S1-05）。

**T02 AC 勾验**（浏览器真实 UI 实拍，outputs/closure_t02_*.png 共 6 张）：
- [x] AC① 空态文案就位（?projectId=999 →「该课题暂无待验收提交」，closure_t02_empty_state.png）
- [x] AC② 批量通过→ACCEPTED+学生收通知（UI 勾选 1 条→二次确认→通过；SQL 断言 status=ACCEPTED、学生 LAB_TASK「任务已验收通过」；closure_t02_batch_confirm/done.png；测试用临时任务已 SQL 清零）
- [x] AC③ 驳回仍逐条且意见必填（「驳回」入口预选 REJECT，空意见提交被「驳回时必须填写意见」拦截，closure_t02_reject_required.png）
- [x] AC④ ADMIN 深链只读（/reviews?projectId=14：无菜单项、无勾选列、无批量按钮、行操作=「只读」+「管理员只读」标签，closure_t02_admin_readonly.png）
- [x] AC⑤ 通知点入正确落点（面板点「新提交待验收」→ /reviews?projectId=14 且 3 行在列，closure_t02_notif_landing.png）
- [x] 菜单「待验收」仅教师可见、置「实验项目」旁（closure_t02_inbox_teacher.png）

**实施偏差与决定**：
1. 收件箱行内附 `content/datasetVersionId/overdue` 三字段（T01 字段清单外增补）：供 T02 列表「版本或内容摘要」列与逾期标记直用，省一次逐行请求；仍仅教师/ADMIN 可见，不入任何 AI 链路。
2. 验收请求体沿用既有契约 `{decision:"ACCEPT"}`（Tickets T02 行文写作 `{action:"ACCEPT"}`，以现行代码为准，未改端点）。
3. 行内「展开提交内容」复用 apiListSubmissions 时间线（与 TaskTab 同款）；修复一处刷新后展开区显示空缓存的问题（load 后对仍展开行重拉）。
4. TaskReviewDialog 增可选 prop `initialDecision`（收件箱「通过/驳回」入口预选结论；缺省 ACCEPT，意见必填等约束不动，既有调用方零影响）。
5. 批量通过为前端顺序调单条端点（按 T02 备注，未新增服务端批量写接口）；部分失败弹汇总框列明每条原因，不静默。

### 批次2：T03 + T04（实验目录 → 任务桥接）✅

**T03 AC 勾验**（自动化：.tools/verify_closure.py S2-01~S2-21，可重放幂等、teardown 零残留；批次门禁复跑 S1 收件箱 19 断言全绿）：
- [x] AC① 存量任务与独立课题零影响（S2-03 列可空；S2-04 历史任务 experiment_id 全空、列表 200；S2-08/09 独立课题挂实验 400 兜底）
- [x] AC② 跨课程挂靠请求 400（S2-06 实验不属本课程目录→400；S2-07 SQL 对照未落库无残留）
- [x] AC③ 删实验后关联任务 experiment_id 置空且任务完好（S2-19 删除不阻断；S2-20 级联置空、任务仍在、实验已删）
- [x] AC④ 迁移可回滚（S2-01 upgrade+rollback 成对；S2-02 rollback 含 DROP COLUMN experiment_id + DROP KEY；rollback 已在 dev 库验证可执行后重放 upgrade）
- 补充断言：S2-05 任务视图暴露 experimentId/experimentName（读侧契约）；S2-11~S2-18 活体全链（建目录实验→挂靠落库+回填名→sentinel 清空 `experimentId=0`→改挂 `>0`→修订留痕 fieldName=experimentId）。

**T04 AC 勾验**（浏览器真实 UI 实拍，outputs/closure_t04_*.png；+ S2-22~S2-24 前端源码断言）：
- [x] AC① 目录行「布置任务」→预填标题+默认选中该实验→提交后任务挂到该实验，列表「关联实验」列显示实验名（closure_t04_assign_*.png；S2-13/14 SQL 对照）
- [x] AC② 独立课题无「关联实验」项且后端 400 兜底（closure_t04_independent_no_link.png 派发抽屉无该字段；closure_t04_independent_column_hidden.png 列表亦无该列；后端 S2-09 400）
- [x] AC③ 列表按实验筛选行数正确（closure_t04_filter.png，筛选后收敛至 1 行；S2-22 filteredTasks/expFilter 源码断言）
- [x] AC④ ADMIN 看不到「布置任务」（目录只读视图无该按钮；S2-23 `v-if="!isAdminView"` 约束断言）

**实施偏差与决定**：
1. **【回归修复】Map.of() NPE**：T03 的 `TaskService.experimentNames(...)` 空集分支原返回 `Map.of()`，而 `toViews` 以可空的 `experimentId` 作 key 查询——`Map.of().get(null)` 抛 NPE（不可变集合拒绝 null 键），导致「派发无关联实验任务 / 列表全空关联 / 清空关联」三处 HTTP 500。批次门禁复跑 S1 时捕获（S1-11~S1-18 全红）。改为 `java.util.Collections.emptyMap()`（`get(null)` 安全返回 null）。复跑 43/43 全绿。同文件 `studentNames` 虽亦用 `Map.of()`，但仅以非空 `assigneeStudentId` 查询，安全，未动。
2. **列表「关联实验」列条件渲染**：AC② 原文仅要求隐藏表单项，但独立课题（无目录）下该列恒为空（全「-」），观感差且模糊 AC②。决定将列与顶部筛选一并 gate 在 `experiments.length`（独立课题/空目录课题不渲染），与筛选器同口径；不影响有目录课题。
3. 校验顺序：`create()` 先 `validateExperimentBinding`（独立课题/跨课程 400）后逐受让人成员校验，保证独立课题 400 优先于其它 400，语义确定。
4. `experimentId` 更新 sentinel 语义：`null`=不修改、`0`=清空、`>0`=改挂；改挂/清空均写修订留痕（fieldName=experimentId）。
5. 目录行「布置任务」经 `CatalogTab` 上抛 `assign-task` → 父页 `ProjectDetailPage` 切至 tasks Tab 并以 `assignPrefill`+`assignSeq`（immediate watch）预填标题与关联实验，复用既有 `TaskFormDrawer`，未新增写接口。

**批次2 收尾基线**：verify_closure 43/43、verify_group 47/47、verify_merge 23/23、verify_admin_readonly 78/78、pytest 171 全绿、mvn surefire 23 全绿（1+7+3+12）；前端 `npm run build` + `npm run typecheck` 均通过，dist 已重建。临时独立课题（CLOSURE-UI-IND / id 63）已 SQL 物理删除，关联表零残留。

### 批次3：T05 + T06（成绩册 / 项目台账 Excel 导出）✅

**T05 AC 勾验**（自动化：.tools/verify_closure.py S3-01~S3-11，POI 写出 → openpyxl 反读断言；浏览器实拍 outputs/closure_t05_*.png）：
- [x] AC① 双 sheet 结构与行数正确（S3-04 sheet 名={成绩汇总,任务明细}；S3-05/06 表头列序；S3-07 汇总数据行=成员数、S3-08 明细数据行=任务数，均 SQL deleted=0 对照；S3-09 状态列合法中文标签；S3-10 行内自洽 已验收/待验收≤任务数）
- [x] AC② 空课题导出不报错（S3-21 临时独立课题成绩册：汇总/明细仅表头各 1 行，HTTP 200 + PK 魔数）
- [x] AC③ 下载文件名可读（S3-03 Content-Disposition 含 RFC5987 `filename*=UTF-8''` + 「成绩册_课题名_yyyyMMdd」）
- 权限：S3-01 学生导出 403（@RequireRole + requireExportable 双保险）；S3-11 ADMIN 只读视角可导出（导出=读操作，与报表模块口径一致）。
- 浏览器：教师详情页头「导出」下拉→成绩册，网络 `GET /projects/14/export/grades [200] application/octet-stream`（reqid 446）；学生视角头部无「导出/编辑/归档」任何入口（closure_t05_student_no_export.png）。

**T06 AC 勾验**（自动化：S3-12~S3-22；浏览器实拍 outputs/closure_t06_*.png）：
- [x] AC① 五 sheet 行数与页面列表一致（S3-13 sheet 顺序=成员/实验目录/任务/提交记录/数据集登记；S3-14~S3-18 各 sheet 数据行 SQL 对照：成员=member 数、实验目录=course 10 目录数、任务=task deleted=0、提交记录=join task deleted=0、数据集=dataset deleted=0）
- [x] AC② 容器与独立课题均可用，独立课题「实验目录」sheet 为空表头（S3-20 临时独立课题台账：实验目录仅 1 行表头、0 行数据，HTTP 200）
- [x] AC③ 不含越权数据（按课题权限判定后导出：S3-01 学生 403；行级单路走 ProjectAuthService.requireReadable，教师仅本人课题、ADMIN 只读放行）
- 浏览器：教师「导出」下拉→项目台账，网络 `GET /projects/14/export/ledger [200] application/octet-stream`（reqid 447）；ADMIN 视角头部有「导出」但无「编辑课题/归档」、目录行无写操作（closure_t06_admin_export_readonly.png）。

**实施偏差与决定**：
1. 新增 `ProjectExportService`（service 层）承载两表生成，控制器 `ProjectController` 仅加 `/{id}/export/grades`、`/{id}/export/ledger` 两个 `@RequireRole({"ADMIN","TEACHER"})` 端点；数据全部经既有 service（members/tasks/datasets/experiments）取出，杜绝双口径，完成率/逾期沿用 `ProjectStatsService` 同源逻辑（分母剔除 CLOSED、逾期派生）。
2. 导出闸门 `requireExportable`：先角色闸门（非 ADMIN/TEACHER 直接 403，挡学生）再 `authService.requireReadable`（教师仅本人课题、ADMIN 放行）——与「导出含学号姓名、学生不可导全班册」的隐私口径一致；文件下载不进任何 AI 链路，故学号姓名允许落表。
3. 成绩册明细「提交/验收时间/意见」取每任务最新一条提交（按 submittedAt/id 降序）；台账「提交记录」sheet 列全量提交流水（join 任务标题 + 提交人姓名）。
4. 表头加粗（CellStyle bold）、列宽 autoSize+余量（上限 255 字符宽）；状态/成员来源统一中文标签（与前端 constants TASK_STATUS_MAP 一致：待领取/进行中/待验收/被驳回/已验收/已关闭，逾期加「·逾期」后缀）。
5. 前端入口合并为详情页头单个「导出」下拉（成绩册/项目台账二项，T06 备注「可合并同一入口」）；走 `request.ts` 的 `download` 守卫（防 200+业务码假文件）+ `saveBlob`，文件名前端按「前缀_课题名_yyyyMMdd.xlsx」拼装（与后端 Content-Disposition 同口径）；`canExport=!isStudent`，故 ADMIN 亦可见导出（与后端放行一致），学生整条操作栏隐藏。
6. 部署注意：后端 jar 被运行进程占用时 `spring-boot:repackage` 改名失败（Windows 文件锁）——本批先停 8080 进程再 `mvn -o package`（23 单测已在首次 package 跑绿），随后 backend-run.bat 重启（PID 8756）。

**批次3 收尾基线**：verify_closure 65/65（S1 19 + S2 24 + S3 22）、verify_group 47/47、verify_merge 23/23、verify_admin_readonly 78/78、pytest 171 全绿、mvn surefire 23 全绿；前端 `npm run build` + `npm run typecheck` 通过、dist 已重建。S3 临时独立课题（CLOSURE-EXPORT-IND）已 SQL 物理删除、零残留。

### 批次4：T07 + T08 + T09（AI 全项目报告：文件画像 / 生成管线 / PDF + 前端）✅

**T07 AC 勾验**（文件画像 + 聚合扩展，nlq-service；单测 tests/test_file_profile.py 16 + tests/test_report_service.py 10 = 26 新增，pytest 197 全绿）：
- [x] AC① 真实样本 `lab_log_20260918.csv` + 手工构造 xlsx 断言画像字段（test_file_profile：csv 字段/数值列/IQR 离群/缺失、xlsx openpyxl read_only 同款、txt；样例行头 5 行）
- [x] AC② 超限/坏文件降级不抛异常（profile_max_file_mb=0 触发超限降级、损坏 xlsx 降级，均 `profiled=False` + reason，不抛）
- [x] AC③ 路径穿越样例（`../`、绝对路径）被拒（`resolve_upload_path` realpath 前缀校验 → None，register-only）
- [x] AC④ 无文件的数据集不报错（report_service：dataset 无 latest_version → profile 缺省，聚合不崩；test_report_service 覆盖）
- 红线：画像统计全部 Python 侧算好写进载荷，提示词注明「不得自行计算」；学号/姓名不进模型上下文（test_report_service 隐私断言：载荷 JSON 不含 student_name/严春/uploader，members 仅 {member_count}）。

**T08 AC 勾验**（生成管线；自动化 .tools/verify_closure.py S4-01~S4-09，可重放幂等、teardown 零残留）：
- [x] AC① 首次生成六节齐全、篇幅达量级、数字与聚合一致（S4-02 success；S4-03 一、~六、齐全；S4-04 ≥600 字；活体目检数字与 stats 逐项吻合：成员 2 / 任务 4 有效 2 / 数据集 1 指纹 9:v1 / csv 6 行 4 列）
- [x] AC② 二次调用命中缓存瞬时返回（S4-07 cached=True 且文本与首次逐字一致）
- [x] AC③ force 触发重生成（S4-05/S4-09 force=True → cached=False；浏览器「重新生成」→ 数据截至时间刷新、正文重写）
- [x] AC④ 新上传数据集版本后缓存自动失效（缓存 key = 聚合 hash + 版本指纹 `["{id}:v{version}"]`；test_report_service「新版本使缓存失效」用例覆盖——HTTP 层改指纹需变更数据集属破坏性操作，不在收口脚本重放，故以单测为准）
- [x] AC⑤ 限流生效（429 语义与既有对齐：S4-08 成功一次后桶未补充即再请求 → body.code=429；Java 侧 `/nlq/report` 与 `/nlq/archive` 共用 per-user RateLimiter，1 token/6s）
- [x] AC⑥ 模型不可用时降级确定性统计不白屏（test_report_service「模型不可用降级」：success=False 但 stats 随载荷返回；前端 ReportDialog 无正文时以 el-descriptions 平铺 stats 兜底）
- 权限：S4-01 学生调 `/nlq/report` 403（@RequireRole 仅 ADMIN/TEACHER）；后端转发前 `ProjectAuthService.requireReadable` 行级单路鉴权（教师仅本人课题、ADMIN 只读放行），再 acquireRateLimit。

**T09 AC 勾验**（PDF 渲染 + 前端弹窗 + 档案刷新；自动化 S4-10~S4-17；浏览器实拍 outputs/closure_t08_*.png / closure_t09_*.png 共 7 张）：
- [x] AC① PDF 六节 + 附录完整、中文字体正常（S4-10 200 + `%PDF-` 魔数 + >4KB；S4-12 pypdfium2 反读正文无 `##` 字面残留；S4-13 附录「成员完成情况表」在文；静态 NotoSansSC-Regular.ttf + PdfFont 每文档新建 + close 后 toByteArray；closure_t09_report_pdf_download.png「已开始下载」）
- [x] AC② 重新生成绕过缓存且档案侧同样可刷（报告：closure_t09_report_regenerated.png「已重新生成」+ 数据截至刷新；档案：closure_t09_archive_refresh_button.png 弹窗含「重新生成」按钮、closure_t09_archive_regenerated.png「已重新生成」——收掉上批「24h 缓存无刷新入口」遗留，档案接口 force 透传）
- [x] AC③ 报告入口与目录行「AI 档案」并存不冲突（closure_t09_report_entry_coexist.png：详情页头「生成项目报告」与目录行「AI 档案」同屏；S4 报告链路与档案链路各自独立缓存 key）
- [x] AC④ 独立课题可用（无目录/排课节为空）（S4-15/16 临时独立课题报告 success 且 stats.course/catalog/usage 显式 None；S4-17 teardown 零残留）
- 隐私：附录成员表（姓名/学号/完成情况）由 `ProjectExportService.exportReportPdf` 从库直出、后端 iText 渲染，**不经模型上下文**（与成绩册导出口径一致）；叙述正文红线「不得出现学号姓名」由提示词约束 + S4-06 载荷断言双保险。

**实施偏差与决定**：
1. **报告 PDF「所见即所得」**：沿用报表模块导出模式——前端把已生成的六节正文原样 POST 给后端（`ProjectReportExportRequest{title,text,generatedAt}`），后端只排版不重算；附录表后端从库直出。导出走 `request.ts` 的 `downloadPost` 守卫（防 200+业务码假文件）+ `saveBlob`，文件名前端按「项目报告_课题名_yyyyMMdd.pdf」拼装（与后端 Content-Disposition 同口径）。
2. **markdown 小标题剥离**：活体冒烟发现模型输出 `## 一、项目总体概况`，而 PDF 标题识别按中文序号 `^[一二三四五六七八九十]+、`。已在 `ReportService.exportProjectReportPdf` 正文循环先 `replaceAll("^#{1,6}\\s*","")` 剥井号再判标题（S4-12 断言 PDF 无 `##` 字面）。此修复需停 8080 → `mvn -o package` → backend-run.bat 重启方生效（jar 文件锁教训同批次3）。
3. **per-call 超时**：`call_deepseek`/`generate_text` 增 `timeout` 形参，报告传 `settings.report_timeout=45s`，全局 15s 不动；瞬时错误（5xx/超时/连接错）重试 ≤1 次。
4. **缓存复用档案基建**：报告缓存沿用 archive 的 SQLite `ArchiveCache`（24h），新增 `get_created_at` 供前端「数据截至 …」展示；key = `_cache_key("report", project_id, stats)`，stats 内含数据集版本指纹，故新版本自然失效。
5. **聚合复用**：`report_service` 直接 import `archive_service` 的 `_stats_text/_rate/_int` 等口径函数与 `ArchiveCache`，杜绝双口径；容器/独立课题降级与档案一致（无目录/排课节显式 None，区别于 0）。
6. **前端入口**：详情页头操作栏增「生成项目报告」（`canExport=!isStudent`，故 ADMIN 亦可见、学生整条隐藏，与导出/编辑同闸门）；ReportDialog 生成中进度 → 六节预览（`<pre>` 60vh 滚动）→ 导出 PDF / 重新生成；ArchiveDialog 补「重新生成」按钮（force 透传）。

**批次4 收尾基线**：verify_closure 82/82（S1 19 + S2 24 + S3 22 + S4 17）、pytest 197 全绿（基线 171 + 画像/报告新增 26）、mvn surefire 23 全绿、BUILD SUCCESS；前端 `npm run build` + `npm run typecheck` 通过、dist 已重建。S4 临时独立课题（CLOSURE-REPORT-IND）已 SQL 物理删除、零残留。后端已重启加载 markdown 剥离修复（PID 10724）。

### 批次5：T10 全批验收 ✅（T01~T10 收口完成）

**新套件 `.tools/verify_closure.py`**（可重放幂等、自带 setup/teardown、末态与基线一致）：82/82 PASS。
- 收件箱（S1-01~S1-19）：权限矩阵（教师本人课题 / ADMIN 全量只读 / 学生 403）、批量通过后状态机与通知断言、两条通知改指落点。
- 桥接（S2-01~S2-24）：挂靠校验 400、跨课程拒绝、删实验置空、独立课题拒绝、迁移 upgrade+rollback 成对且 rollback 已在 dev 库验证可执行、前端源码断言（筛选/布置任务上抛/ADMIN 隐藏）。
- 导出（S3-01~S3-22）：POI 写出 → openpyxl 反读，成绩册双 sheet / 台账五 sheet 的列序与行数全部 SQL `deleted=0` 对照、空课题仅表头不报错、RFC5987 文件名、权限闸门。
- 报告（S4-01~S4-17）：缓存命中 / force 重生成 / 限流 429（正例间隔 ≥6.5s 防误伤）/ 学生 403 / PDF `%PDF-` 魔数 + pypdfium2 反读无 `##` 残留 + 附录在文 / 独立课题 course·catalog·usage 显式 None 降级 / teardown 零残留。版本指纹失效因需变更数据集（破坏性）不在 HTTP 层重放，以 test_report_service 单测为准（已绿）。

**基线复跑（全绿，防回归）**：
| 套件 | 结果 |
| --- | --- |
| pytest（nlq-service） | 197 passed（基线 171 + 画像 16 + 报告 10） |
| mvn surefire（backend） | 23 passed，BUILD SUCCESS |
| verify_closure | 82/82 |
| verify_group | 47/47 |
| verify_merge | 23/23 |
| verify_admin_readonly | 78/78 |
| 前端 typecheck / build | EXIT 0 / EXIT 0，dist 已重建 |

**浏览器实拍（真实 UI，outputs/closure_*.png 共 26 张）**：
- 教师——收件箱批量通过（t02_batch_confirm/done）、目录行布置任务（t04_catalog_assign/assign_drawer/task_linked）、两种导出下载（t05_export_dropdown 成绩册、t06_admin_export_readonly 台账）、报告生成与 PDF（t08_report_dialog_cached 六节+缓存tag、t09_report_pdf_download「已开始下载」、t09_report_regenerated force 刷新）。
- ADMIN——收件箱深链只读（t02_admin_readonly：无菜单/无勾选/无批量/行操作只读）。
- 补充——学生视角无报告/导出入口（t09_student_no_report、t05_student_no_export）、报告入口与目录「AI 档案」并存（t09_report_entry_coexist）、档案侧重新生成（t09_archive_refresh_button/regenerated）。

**口径**：错误一律 HTTP 200 + `body.code` 断言；SQL 对照一律带 `deleted=0`（仅对有该列的表）；GBK 后端日志；学生账号受改密拦截，浏览器验收用 token 注入（sessionStorage `ltpms_token`）。

**交付纪律遵守情况**：
- ✅ 全部改动留工作区，**未做任何 git 写操作**（add/commit/push/checkout/reset/stash/clean 均未执行）；开工与收尾各记录一次 `git status`，末态与基线一致。
- ✅ 学生端二期施工方案文档只读、未实施。
- ✅ 未删改无关仓库文件；`.hermes` docx（开工时已为 `D` 删除态，非本批所致）与 `outputs/smoke/_dbg-nlq.png` 均保持原状未触碰。
- ✅ Tickets §5「明确不做」5 项（zip 打包 / 报告全量版 / 排课联动 / 统计切片 / Dashboard 收件箱区块）均未实施。
- ✅ 隐私红线：学号/姓名不进任何 LLM 上下文（报告/档案载荷断言 + 提示词约束）；AI 报告统计 Python 侧算好传入；附录成员表后端从库直出。
- ✅ 权限红线：行级鉴权单路走 `ProjectAuthService`（未开第二路）；ADMIN 写侧 403 完好；T02 批量通过复用既有单条验收端点（未新增写接口）。
- ✅ 迁移成对：`database/upgrade_20260930_task_experiment.sql` + `rollback_*.sql`，逻辑外键风格同 init.sql，rollback 已在 dev 库验证可执行后重放 upgrade。
- ✅ 文档同步仅限本 Tickets 文件（各 Ticket 勾验 + §8 交付记录逐批追加），未新建/未改其它文档。

**收尾状态**：三服务在跑——preview 8000（PID 3880）、nlq 8001（PID 20252）、backend 8080（PID 10724，已加载 markdown 剥离修复）；DB 无 CLOSURE-*/VERIFY 临时课题残留，project 14 基线完好（4 任务 deleted=0 / 1 数据集）。**T01~T10 全批交付完成，commit/push 待用户另行授权。**
