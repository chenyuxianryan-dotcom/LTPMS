# LTPMS「课题项目」模块技术方案（合并后归属统一「实验项目」模块）

> ✅ 2026-09-20 r4：**管理员只读边界收口**——实验项目模块对 ADMIN 全局只读（读侧直通、写侧一律 403），入口由菜单迁至「课程管理」页头；同批修掉三项既有缺陷并新增「实验归属课程不可变更」校验（见 §8）。
> ✅ 2026-09-20 r3：批次三「**合并改造 + 仅 S7 归档**」已编码交付并通过全量验收（AC 套件 23/23，AC7 报表一逐格复核 63/63 格一致，见 §7）。本模块与「实验项目」已合并为一个模块：**课程容器（SYSTEM）+ 自建课题（USER）+ 容器内「实验目录」Tab**，前端路由收敛到 `/experiments`，旧 `/projects`、`/projects/:id` 为 301 语义重定向（前端路由 redirect）。命名红线 r2-H4 按 M-Q5 改写：**UI 模块名一律「实验项目」，实体级仍自称「课题」**（建课题/课题成员/课题进度），代码内保留 project 命名。S4（按班带入）/S6（评论）依 M-Q9 推迟到后续批次。

- 日期：2026-09-18　修订：r4（2026-09-20 管理员只读边界收口 + 三项缺陷修复 + 归属课程校验；r3 合并改造交付回写 M-Q1~Q9 + R1~R4；r2 曾回写冲突审查 C1~C5 / B1~B6 / H1~H4）
- 决策依据：Q1~Q7 拷问记录（批次一）+ 合并拷问记录 M-Q1~Q9/R1~R4（批次三，含 AC1~AC14 验收清单与 §7 迁移军规）
- 命名口径：UI 模块名「实验项目」；实体级自称「课题」；容器内目录仍称「实验目录」（M-Q5 / r2-H4 改写）
- 上游 PRD：`spark-output/prd/lab-project-mgmt.md`（含批次三合并增补章节）

## 0. 学生认证选型

`user.role` ENUM 扩为 `('ADMIN','TEACHER','STUDENT')`；学生登录名=学号，复用现有 `AuthController` 登录 / `is_default_pwd` 强制改密 / 锁定链路；学生属性放 `student` 档案表（user_id 一对一）。**不建独立登录体系。**

⚠️ r2-H1：role 是字符串硬编码，全库 `"TEACHER"/"ADMIN"` 判断 20+ 处（Course/Experiment/Schedule/Report/Insight/Dashboard/Notification/Teacher/Avatar service + JWT/RoleAspect + 前端 constants/store/router）。加 STUDENT **不会编译报错**，会静默走 else。**编码第一步 = 全量 grep 出"角色分支清单"，据此出三角色回归用例。**
已知必须改的点（取证）：
- `router/index.ts:134-143` 守卫 fallback `/dashboard` → 按角色分派，学生回 `/my-tasks`（r2-C1）
- `DashboardController:41 @RequireRole("TEACHER")`：学生不可达 teacher dashboard 数据（r2-C1 连带）
- `LoginPage.vue:59` "请输入工号" → "工号/学号"；`redirectAfterLogin` 角色化（r2-C5）
- `TeacherService:121/141/162` 的 `!"TEACHER".equals(role)→FORBIDDEN` 保持不变（隔离正确），学生重置密码走**新接口**（r2-C3）

## 1. 表结构（`database/upgrade_20260918_lab_project.sql`）

```
user (改)          role ENUM 加 'STUDENT'

student            学生档案
  id, user_id→user, student_no(唯一), class_name, major, created_at

course_student     课程选课名单（r2-B4：带班级粒度）
  id, course_id→course, student_id→student,
  class_name VARCHAR(64) NOT NULL COMMENT '平行班区分——course 按(course_code,semester_id)唯一只有一行，名单必须按班存',
  source(IMPORT/MANUAL), joined_at
  UNIQUE(course_id, student_id)

lab_project        课题
  id, project_code(唯一 LP-YYYY-NNN), project_name,
  teacher_id→user,                    -- 行级隔离锚点
  course_id NULL, semester_id NULL,   -- 仅归类标签，不产生成员关系
  description, status(ACTIVE/ARCHIVED), created_at, updated_at

lab_project_member 成员（带入即快照）
  id, project_id, student_id,
  source(CREATE/MANUAL/COURSE_SNAPSHOT), joined_at
  UNIQUE(project_id, student_id)
  -- 快照：课程名单变更不回写；移出成员其未完成任务置 CLOSED（见 §3 统计口径）

lab_project_task   任务
  id, project_id, title, description TEXT,
  assignee_student_id→student,
  status ENUM('NOT_STARTED','IN_PROGRESS','SUBMITTED','ACCEPTED','REJECTED')
  -- r2-B1：CLOSED（成员移出/规则①驳回后关闭）加入状态枚举
  ,deadline DATETIME NULL,
  dataset_id NULL→lab_dataset,
  deleted TINYINT(1) DEFAULT 0,
  created_by, created_at, updated_at
  -- 逾期不落字段：deadline<NOW() && status IN (NOT_STARTED,IN_PROGRESS)
  -- deadline 修改走 schedule_revision 同款留痕：target_type='LAB_TASK'（r2-B2）

lab_task_submission 提交（追加式，驳回重提=新行）
  id, task_id, submitter_student_id, content TEXT,
  dataset_version_id NULL→lab_dataset_version,
  submitted_at, reviewed_by NULL, review_comment VARCHAR(512) NULL, reviewed_at NULL
  -- content/review_comment 仅本人+归属老师+ADMIN：后端字段裁剪（Q6）

lab_dataset        数据集
  id, project_id, dataset_name, description,
  latest_version INT, created_by→user, deleted TINYINT(1) DEFAULT 0

lab_dataset_version 版本链（只增不覆盖）
  id, dataset_id, version_no INT,
  stored_path COMMENT '服务端生成 UUID 文件名，绝不用用户原名拼路径（r2-B6）',
  original_filename, file_size, file_type COMMENT '魔数校验结果',
  uploader_id→user, note VARCHAR(256), deleted TINYINT(1) DEFAULT 0, created_at
  UNIQUE(dataset_id, version_no)

lab_comment        通用评论
  id, target_type('DATASET','DATASET_VERSION'), target_id,
  user_id→user, content VARCHAR(512), created_at
```

文件存储：`upload.lab-file-dir=./uploads/lab`（私有目录，不进静态放行）；类型白名单 xlsx/csv/txt/图片/zip ≤50MB（配置项），**按魔数不按后缀**（r2-B6）。

### 1.1 批次三 schema 变更（`database/upgrade_20260920_unify_experiment.sql`，幂等可重放）

```
lab_project (改)                  -- 模块合并的实体基座：一个表同时承载"课程容器"与"自建课题"
  + source ENUM('SYSTEM','USER') NOT NULL DEFAULT 'USER'
      -- SYSTEM=课程容器（course_id/semester_id 必填，系统懒生成，不可删除可归档）
      -- USER  = 自建课题（M-Q7：无课程绑定，建/改接口不收 courseId/semesterId）
  + UNIQUE uk_course_semester_source (course_id, semester_id, source)
      -- 同课同学期至多一个 SYSTEM 容器；USER 行 ENUM 值不同不与之冲突

lab_project_member (改)
  ~ source ENUM 扩第四值 AUTO_SNAPSHOT
      -- CREATE/MANUAL/COURSE_SNAPSHOT/AUTO_SNAPSHOT
      -- AUTO_SNAPSHOT = 容器生成瞬间的 course_student 全量快照（M-Q6：生成后名单变更永不回写）
```

**容器编号**：迁移脚本写 `LP-<迁移年>-S<3位课程ID>`（S 前缀），运行期生成器只扫 `LP-YYYY-%` 后**纯数字**后缀取 max+1（§8.1 编码约定）——两套编号互不撞号；生成撞号由 `uk_project_code` 兜底，调用方重试一次。

**存量迁移**（脚本第 3~4 段，INSERT...SELECT 幂等）：已有 experiment 行的课程 → 每课程一个 SYSTEM 容器 + `course_student` 全量快照；回滚脚本 `database/rollback_20260920_unify_experiment.sql`（随批交付）。军规：打标写入零例外、**禁 UPDATE 存量行**、迁移与回滚均不得触碰 `experiment` 表（保报表一口径）。

## 2. 接口清单（/api/v1/…）

> **角色列注（2026-09-20 r4）**：本清单写接口标注的 `A` 已随管理员只读收口降级——ADMIN **读侧照常直通、写侧一律 403**（判定收口见 §4 / §8）。

### 学生 & 名单（TEACHER/ADMIN）
| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/students/import` | 批量建档；陌生学号自动建账号（默认密码 `Ltpms@学号`）；**学号撞非 STUDENT username → 该行拒绝**（r2-C2） |
| GET | `/students?keyword=&class=` | 学生检索 |
| POST | `/students/{id}/reset-password` | r2-C3：仅该生任一在管课题老师/ADMIN；写 operation_log |
| POST | `/courses/{id}/students/import` | 选课名单导入（含 class_name 列，r2-B4） |
| GET | `/courses/{id}/students?className=` | 名单查看（可按班过滤） |
| DELETE | `/courses/{id}/students/{studentId}` | 移出名单（不回写项目快照） |

### 课题 & 成员
| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| GET | `/projects?source=SYSTEM\|USER` | A/T/S | 统一「实验项目」列表（M-Q4 Segmented 数据源）：TEACHER=本人归属、ADMIN=全校、STUDENT=所在课题；`source` 留空=全部；行内含 source/courseName（课程徽标） |
| POST | `/projects` | T/A | 建**自建课题**（M-Q7 挂课收口：不收 course_id/semester_id，纯自建） |
| PUT | `/projects/{id}` | T/A | 改课题名/描述；course/semester **锁定不可改**（R2） |
| GET | `/projects/{id}` | 成员 | 详情，返回 **ProjectView**（与列表同口径：教师名/成员数/完成度）；ARCHIVED 全写接口 403 |
| POST | `/projects/{id}/archive` | 归属T/A | 归档定格（S7 批次三落地）；重复归档幂等 200 |
| DELETE | `/projects/{id}` | 归属T/A | 删除；SYSTEM 容器 403「容器不可删除，可归档」 |
| GET | `/projects/{id}/members` | 成员 | 成员列表（容器首次可见 AUTO_SNAPSHOT 快照行） |
| POST | `/projects/{id}/members` | T/A | `{studentIds:[]}` 或 `{courseId, className?}` 带入 |
| DELETE | `/projects/{id}/members/{studentId}` | T/A | 移出 + 其未完成任务置 CLOSED（r2-B1） |
| GET | `/projects/{id}/experiments` | 成员 | **容器「实验目录」Tab 数据**：按 project.course_id 读 experiment 行 |
| POST | `/projects/ensure-course-container` | A/T | `{courseId}` → 幂等确保该课当前学期 SYSTEM 容器（R1 显式入口；并发靠 `FOR UPDATE` 当前读 + uk 兜底） |
| POST | `/projects/ensure-my-course-containers` | S | 学生侧读侧 ensure：本人所选课程容器与名单快照就位（ADMIN 返回空列表，不被动建容器） |
| POST | `/experiments/import`（课程号随表单）、目录 CRUD `/experiments` 系列 | T（A 只读） | 目录读写仍落 `experiment` 表、course_id = 本容器课程且**编辑时不可变更**（r4）；"从上学期复制"按 **course_code 回溯上学期课程行**（R3），编号走 CodeGenerator 重生成 |
| GET | `/experiments/copy-availability?courseId=` | T（A 只读可查） | 「从上学期复制」按钮置灰探针（R3），不落库；写侧动作仍由 copy-from-previous 的 checkPermission 拦截 |
| POST | `/experiments/copy-from-previous?courseId=` | T | 批量复制上学期同课程号目录；参数为 **`@RequestParam`（查询串）**，前端 apiCopyFromPrevious 同形态（r4 缺陷修复） |

### 任务 & 提交 & 验收
| 方法 | 路径 | 角色 | 说明 |
|---|---|---|---|
| GET | `/projects/{id}/tasks` | 成员 | 学生侧字段裁剪 |
| POST | `/projects/{id}/tasks` | T/A | assigneeStudentIds>1 服务端复制多条 |
| PUT | `/tasks/{taskId}` | 归属T/A | deadline 等修改自动写 LAB_TASK 修订留痕（r2-B2） |
| DELETE | `/tasks/{taskId}` | 归属T/A | 规则①（已批准）：未提交可软删；已提交只能驳回→CLOSED |
| POST | `/tasks/{taskId}/start` | 负责学生 | →IN_PROGRESS |
| POST | `/tasks/{taskId}/submit` | 负责学生 | `{content, datasetVersionId?}` →SUBMITTED，通知老师 |
| POST | `/tasks/{taskId}/review` | 归属T/A | ACCEPT/REJECT(+comment)；通知学生 |
| GET | `/tasks/my?status=` | 学生 | 我的任务待办（按课题分组一次拉全） |

### 数据集
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/projects/{id}/datasets` | 成员可见 |
| POST | `/projects/{id}/datasets` |  multipart 新建 v1；**若同名已存在 → 409 返回既有 dataset 摘要，前端弹框二选一"挂为 v(N+1) / 换名新建"（r2-C4）** |
| POST | `/datasets/{id}/versions` | 传新版本（UUID 落盘） |
| GET | `/datasets/{id}/versions` | 版本链 |
| GET | `/dataset-versions/{vid}/download` | 成员鉴权；Content-Disposition 编码原名 |
| GET | `/dataset-versions/{vid}/preview` | CSV 流式截断；xlsx ≤2 万行流式读，超限"请下载"（r2-B5，现全量 XSSFWorkbook 禁止用于预览） |
| PUT | `/datasets/{id}` | 改名/描述：创建者+老师 |
| DELETE | `/datasets/{id}` | 数据集软删：创建者+老师 |
| DELETE | `/dataset-versions/{vid}` | r2-B3 版本软删：该版本上传者本人+老师 |
| GET/POST | `/comments?targetType=&targetId=` | 项目成员查/发 |

### 量化（单一事实源）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/projects/{id}/progress` | `{projectRate,total,accepted,overdueCount,students:[{studentId,name,rate,cells:[{taskId,status,overdue}]}],tasks:[...]}`；**后端仅一个 ProjectStatsService.stats()，列表页/详情/矩阵全走它（r2-B1）** |

## 3. 统计口径（r2-B1 冻结）

- 完成度% = ACCEPTED / (总任务 − CLOSED − 软删)，CLOSED=成员移出或规则①关闭。
- 已验收任务永久计入分子分母；CLOSED 只出现在移出时刻 → **历史百分比单调可复现，归档后定格**。
- 逾期为查询时派生，不落库、不进分母。

## 4. 横切实现约束

- **ProjectAuthService（硬红线，r4 收口口径）**：**读侧** ADMIN 直通 / TEACHER 比 teacher_id / STUDENT 查 `lab_project_member`；**写侧** ADMIN 一律 403——`requireNotAdmin` 前置在 `requireOwned`，课程目录侧 `ExperimentService.checkPermission`、数据集侧 `requireManager`、任务学生路径（start/submit）同口径拦截。新模块所有端点唯一入口，禁止再复制 if-else（H2）。容器生成侧对 ADMIN 只保留"命中已有容器"的只读回退，不生成。
- **ensure 幂等（R1）**：容器生成走「先查后插 + 命中 `uk_course_semester_source` 撞键时 `SELECT ... LIMIT 1 FOR UPDATE` **当前读**回取」——REPEATABLE READ 下普通一致性读看不到并发已提交行，必须当前读，否则并发双 ensure 双双 500（批次三实测缺陷，见 §7）。
- **NLQ**：`lab_project*` 进 `nlq-service/app/services/permission.py` 白名单（teacher_id 行注入）；`student`/`course_student` 一期不进（规则②已批准，登记升级 checklist）（H3）。
- **通知**：type=`LAB_TASK`，ref_url=**`/experiments/{id}`**（批次三随路由收敛改名；存量行 `/projects/{id}` 由前端 redirect 兼容）。
- **学生可达面（r2-H1 白名单）**：STUDENT 仅 `/auth`、`/projects`、`/tasks`、`/datasets`、`/dataset-versions`、`/notifications`、`/users`、`/files`；`/semesters`、`/dicts`、`/nlq/**`、`/reports/**` 等一律 403。故前端**加载侧不得在学生的挂载路径上 await 教师端点**（批次三实测缺陷：学生进列表/目录 Tab 因 `/semesters` 403 中断挂载导致空列表，已改为按角色条件请求）；目录「操作」列与写工具栏仅教师可见（材料清单/AI 档案走 NLQ，学生不可达）。
- **前端路由**：`/experiments`（列表，meta.roles = ADMIN+TEACHER+STUDENT）、`/experiments/:id`（容器/课题详情，Tab：实验目录[仅 SYSTEM]/成员/任务/数据/进度）、`/my-tasks`；`/projects`、`/projects/:id` 为 redirect（保 query）；菜单按角色：TEACHER / STUDENT 各一项「实验项目」，**ADMIN 无此菜单项、入口在「课程管理」页头**（r4）。
- **矩阵**：纯 CSS 格子（仿 StatGridCard 气质）；新页面零音效、不动 unit-skin 三页。
- **修订留痕**：`schedule_revision` 的 target_type 扩展 `LAB_TASK`（表本身 target 通用，无需改结构）。
- **审计留痕**：ensure / AUTO_SNAPSHOT 带入 / 归档统一写 `operation_log`（`PROJECT_CONTAINER_ENSURE`、`PROJECT_AUTO_SNAPSHOT`、`PROJECT_ARCHIVE`）；**手工 INSERT 必须自填 `ip`**（该列 NOT NULL 无默认，批次三实测缺陷：漏填致 DataIntegrityViolation 静默丢日志）。

## 5. 工作量粗估（r2，单人全栈）

| 块 | 估 |
|---|---|
| 角色分支清单 grep 审计 + 三角色回归用例设计 | 0.5 天（r2 新增，编码前置） |
| 表+实体+Mapper+升级SQL | 1.5 天 |
| 课题/成员/任务/提交/验收 API + ProjectAuthService | 3.5 天 |
| 数据集+版本链+UUID 落盘+魔数校验+流式预览 | 2.5 天 |
| progress 统计单源 + 矩阵前端 | 1.5 天 |
| 学生/课程名单导入（撞号校验+班级粒度+重置密码） | 2 天 |
| 学生端两页 + 守卫/落地角色化 + 菜单文案 | 2 天 |
| NLQ 白名单 + 通知 + 联调自测 | 1.5 天 |
| **合计（批次一/r2）** | **~15 人日**（r2 +2） |

**批次三（合并改造 + S7）估 ~5-6 人日**：schema+迁移 0.5｜S7+容器禁删 0.5｜目录 Tab 组件化迁移 2｜Segmented+路由+全站改名 1｜三角色回归+报表一验证 1（拷问记录 §4）。

## 6. 待拍板登记（批次一遗留 → 均已闭环）

1. 规则①：已提交任务不可删、只能驳回后关闭（§2 任务 DELETE）。——**已批准并执行**（2026-09-18，端点自测覆盖：已提交/已验收删除被拒、REJECTED 删除→CLOSED、未提交软删）。
2. 规则②：`student`/`course_student` 一期不进 NLQ 白名单（§4）。——**已批准并执行**（`permission.py` 仅注册 `lab_project` 系 4 表；`student`/两跳表命中即 PermissionRejectError 安全拒绝，回归测试 32/32 通过）。
3. 批次二排期（原 S4/S6/S7 三件套）。——**批次三已改判**（M-Q9）：只做**合并改造 + S7 归档**；S4（按班带入）/S6（评论）显式推迟。

## 7. 批次三交付与验证记录（2026-09-20）

**交付物**

| 类别 | 内容 |
|---|---|
| schema | `database/upgrade_20260920_unify_experiment.sql`（幂等可重放）+ `database/rollback_20260920_unify_experiment.sql` |
| 后端 | `ProjectService`（容器 ensure/归档/删除收口/source 过滤/视图同源）、`ProjectController`（新增 3 端点 + 详情改 ProjectView）、`NotificationService` ref_url 改名 |
| 前端 | `ExperimentPage.vue` 重写（Segmented + 迁移入口 + 深链 ensure）、`pages/experiment/components/CatalogTab.vue` 新建（旧实验页 CRUD/导入/复制/材料/NLQ 档案全量迁移）、`ProjectDetailPage.vue`（目录 Tab + 归档 + 类型徽标）、`router`（redirect）、`BasicLayout` 菜单收敛；旧 `ProjectPage.vue` 删除 |
| 验收脚本 | `.tools/verify_merge.py`（AC 套件，HTTP 层 23 项断言） |

**实测发现并修复的两个缺陷**（静态审查未捕获，均来自联调实测）

1. **并发 ensure 幂等洞**：REPEATABLE READ 下败者的一致性读看不到胜者刚提交的容器 → 双双撞 uk → 调用方收到 500。修复：catch 块内 `LIMIT 1 FOR UPDATE` 当前读回取（§4）。
2. **审计日志静默丢失**：`operation_log.ip` NOT NULL 无默认，手工 INSERT 未填 ip → `PROJECT_AUTO_SNAPSHOT` 落库失败。修复：对齐 `OperationLogAspect`，从 `RequestContextHolder` 取 ip（空串兜底）。

**AC 结论**：AC1~AC14 全部通过（脚本 23/23 + 浏览器三角色走查）；AC7 报表一"合并前后逐格一致"以**同 SQL 直连 MySQL 与接口输出逐格比对**复核（9 行 × 7 列 = 63/63 格一致，`ReportService`/`ReportMapper.xml` 在批次 diff 中零改动为结构性证据）；AC10 为继承 r2-B1 不变式（`TaskService` 本批零改动）。

**已知边界（本批不做）**：S4 按班带入、S6 评论；学生侧材料清单/AI 档案未放行（NLQ 白名单未含学生）；`/projects` 重定向为前端路由级（后端 API 路径仍为 `/api/v1/projects`，资源语义即"课题/容器"）。

## 8. 管理员只读边界收口（r4，2026-09-20）

**用户指令**：修复管理员越权——实验项目模块对 ADMIN 收口为只读：隐藏 ADMIN 菜单入口、统一入口迁至「课程管理」页头、管理员进入后仅可浏览教师实验项目，新增/编辑/删除/审核等写操作一律禁止。

**决策与实现（单一收口点，不留第二判定路径）**

| 面 | 处置 |
|---|---|
| 入口 | ADMIN 菜单项「实验项目」移除；「课程管理」页头新增「实验项目」按钮（管理员可见）→ `/experiments`；深链 `?courseId=` 时**只读定位**已有容器（不触发 ensure 写侧），无容器则提示 |
| 读侧 | ADMIN 直通不变（列表/详情/目录/成员/任务/数据/进度/提交记录/修订/下载预览/复制探针） |
| 写侧·课题 | `ProjectAuthService.requireNotAdmin` 前置在 `requireOwned`：改/归档/删/成员/任务/数据集写入一律 403；`POST /projects` 自建课题对 ADMIN 亦 403 |
| 写侧·ensure | `POST /projects/ensure-course-container` 命中已有容器 → 只读回退返回；会生成时 403（`ensure-my-course-containers` 本就对学生/管理员停用） |
| 写侧·目录 | `ExperimentService.checkPermission` 对 ADMIN 抛 403（增/改/删/导入/复制上学期/批量迁移一律拦）；`copy-availability` 为读探针，放行 |
| 写侧·数据/任务 | `DatasetService.requireManager` / `deleteVersion` 移除 ADMIN 特判；任务学生路径（start/submit）对 ADMIN 同样 403 |
| 前端 | `ExperimentPage` / `ProjectDetailPage` / `CatalogTab` / `DatasetTab` / `TaskTab` / `VersionList` 按 `isAdmin` 隐藏全部写入口并挂「只读」标识；只读视图保留材料清单/AI 档案等读侧入口 |

**验证**：`.tools/verify_admin_readonly.py`（HTTP 三身份 **75 项断言全通过**；全部写尝试落在 T900 临时课题 `ADM-GUARD-*` 内做包含性验证，命中真实数据的写尝试只保留幂等拦截项，容器 14 / 课程 10 目录 / 容器名册前后快照零变更）+ 浏览器实拍（管理员只读视图、教师写侧不受影响）。

**同日修复的三项既有缺陷（G 段回归断言）**

1. 独立课题（`course_id IS NULL`）详情 500：`ProjectService` 名称映射空表返回 `Map.of()`，`toViews` 以 null 键查询抛 NPE → 空表改普通 `HashMap`（`courseNames` / `userNames` 两处同款收口）。
2. 实验编辑保存 400：`ExperimentRequest.courseId` 为 `@NotNull`，前端编辑载荷未回带 → `apiUpdateExperiment` 类型收紧（`TExperimentUpdate`）+ `CatalogTab` 编辑表单回带 courseId。
3. 「从上学期复制」500：后端签名为 `@RequestParam Long courseId`，前端按 JSON body 传导致参数缺失 → `apiCopyFromPrevious` 改查询串形态（与 `apiMigrationConfirm` 同写法）。

**同批新增收口**：`ExperimentService.update` 拒绝请求体 `courseId` 与行上归属不一致（400「实验项目归属课程不可变更」）——原实现以请求体值落库，而权限按"实验当前所属课程"判定，归属教师可把自己的实验搬进他人课程；且实验编号前缀绑课程，搬移即失配。
