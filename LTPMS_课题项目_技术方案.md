# LTPMS「课题项目」模块技术方案

> ⚠️ 2026-09-20：合并拷问 M-Q1~Q9 已定稿（见《LTPMS_课题项目合并_需求拷问记录.md》）——本模块与「实验项目」将统一为「实验项目」模块（实体容器+Segmented+挂课收口），本文命名红线 r2-H4 与 §6 批次二排期**待批次三编码时按 M 系列改写为 r3**。当前编码未授权，本文仍为批次一已交付实现的记录。

- 日期：2026-09-18　修订：r2（回写冲突审查 C1~C5 / B1~B6 / H1~H4 修订）
- 决策依据：Q1~Q7 拷问记录（见记忆 lab-project-mgmt-decisions）；需求全文见 `spark-output/prd/lab-project-mgmt.md`
- 命名红线：UI 一律自称「课题」（r2-H4），代码保留 project；与现有 /experiments「实验项目」互不影响

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

## 2. 接口清单（/api/v1/…）

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
| GET | `/projects` | T/A | 我的课题列表（含完成度%/逾期数/成员数，同源 stats） |
| POST/PUT | `/projects`、`/projects/{id}` | T/A | 建/改（course_id/semester_id 可选标签） |
| GET | `/projects/{id}` | 成员 | 详情；ARCHIVED 全写接口 403 |
| POST | `/projects/{id}/archive` | 归属T/A | 归档定格 |
| GET | `/projects/{id}/members` | 成员 | 成员列表 |
| POST | `/projects/{id}/members` | T/A | `{studentIds:[]}` 或 `{courseId, className?, snapshot:true}` 按班勾选带入 |
| DELETE | `/projects/{id}/members/{studentId}` | T/A | 移出 + 其未完成任务置 CLOSED（r2-B1） |

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

- **ProjectAuthService（硬红线）**：ADMIN 直通 / TEACHER 比 teacher_id / STUDENT 查 `lab_project_member`，新模块所有端点唯一入口，禁止再复制 if-else（H2）。
- NLQ：`lab_project*` 进 `nlq-service/app/services/permission.py` 白名单（teacher_id 行注入）；`student`/`course_student` 一期不进（规则②已批准，登记升级 checklist）（H3）。
- 通知：type=`LAB_TASK`，ref_url=`/projects/{id}`（学生点击依赖 r2-C1 守卫修复）。
- 前端：`/projects`、`/projects/:id`、`/my-tasks`，`meta.roles` 含 STUDENT；矩阵纯 CSS 格子（仿 StatGridCard 气质）；新页面零音效、不动 unit-skin 三页。
- 修订留痕：`schedule_revision` 的 target_type 扩展 `LAB_TASK`（表本身 target 通用，无需改结构）。

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
| **合计** | **~15 人日**（r2 +2） |

## 6. 待拍板登记（编码授权前需确认）

1. 规则①：已提交任务不可删、只能驳回后关闭（§2 任务 DELETE）。——**已批准并执行**（2026-09-18，端点自测覆盖：已提交/已验收删除被拒、REJECTED 删除→CLOSED、未提交软删）。
2. 规则②：`student`/`course_student` 一期不进 NLQ 白名单（§4）。——**已批准并执行**（`permission.py` 仅注册 `lab_project` 系 4 表；`student`/两跳表命中即 PermissionRejectError 安全拒绝，回归测试 32/32 通过）。
