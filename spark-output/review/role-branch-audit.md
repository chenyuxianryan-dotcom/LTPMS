# H1 角色分支清单审计（新增 STUDENT 前置取证）

- 日期：2026-09-18　范围：backend `com.ltpms` 全量 + frontend `src/` 全量 + nlq-service `app/`
- 结论口径：✅=对 STUDENT 行为已正确无需改；🔒=本次新模块内部收口；🛠=必须修复（列入本次编码）；📝=记录不改动

## 1. 认证与授权基础设施

| 位置 | 分支逻辑 | STUDENT 落入 | 判定 |
|---|---|---|---|
| `filter/JwtAuthFilter.java:44-47` | `ROLE_ + role` 通用拼接 | 得 `ROLE_STUDENT` | ✅ 无硬编码 |
| `aspect/RoleAspect.java` | 仅比对 `@RequireRole` 声明集 | 未声明 STUDENT 的受控端点 403 | ✅ 注解即闸门 |
| `config/SecurityConfig.java` | `anyRequest().authenticated()`，**凡不带 @RequireRole 的端点任何登录用户可达** | STUDENT 可达全部未注解端点 | 🛠 修复①：SecurityConfig 增加路径规则——学生白名单（auth/notifications/files/users/projects/tasks/datasets/dataset-versions/comments）`authenticated()`，其余 `/api/v1/**` `hasAnyRole("ADMIN","TEACHER")`。单点收口，不动 20+ 处业务 service |
| `service/AuthService.java` 登录/锁定/改密/`/auth/me` | 角色无关 | 复用链路完好 | ✅（R3 回归面：登录/锁定/强制改密需加 STUDENT 用例） |
| `entity/User.java` role 字段 | String，无枚举校验 | 需 DB ENUM 扩容 | 🛠 升级 SQL ALTER |

## 2. 后端业务 service 的 "TEACHER"/"ADMIN" 硬编码分支

带 `@RequireRole` 注解的端点学生被 RoleAspect 挡在门外，其 service 分支对 STUDENT 不可达（判 ✅ 隔离正确）；**风险集中在无注解端点**，由修复①统一兜底后逐条定性：

| 位置 | 逻辑 | STUDENT 若可达（无修复①时） | 判定 |
|---|---|---|---|
| `CourseController` GET `/courses`、`/courses/{id}`（**无注解**）+ `CourseService.java:64` | `"TEACHER".equals` 才过滤本人 | **看到全校课程**（数据泄露） | 🛠 由修复①封死；service 不改 |
| `CourseService.java:155/209/194/227` | 编辑/修订查询 | 端点已有 ADMIN/TEACHER 注解 | ✅ |
| `ExperimentController`（**类内 0 注解**）+ `ExperimentService.java:61/196` | TEACHER 过滤 else=全量 | **看到全校实验项目** | 🛠 修复①封死 |
| `ExperimentService.java:314 checkPermission` | ADMIN 直通 else 比 teacherId | STUDENT→403 | ✅ 隔离正确 |
| `ScheduleService.java:328/427/476/494/512` | ADMIN/TEACHER 分支 | 全部端点有注解 | ✅ |
| `ReportService.java:59/80` + `ReportController.java:162` | `"TEACHER".equals→限本人` else=全校明细；162 行 `TEACHER||aggregateOnly` | **学生按 ADMIN 口径看全校** | 🛠 修复①封死（该控制器仅 116 行有注解） |
| `InsightService.java:49` | ADMIN 全校 else teacherStats | 端点全有注解 | ✅ |
| `DashboardService.java:63/110` | `eq(User::getRole,"TEACHER")` 查教师名单 | 学生存在不污染计数；`DashboardController:30/41` ADMIN/TEACHER 注解 | ✅ 学生 403（C1 连带，前端不再路由学生到 dashboard） |
| `NotificationService.java:106/136` | 广播对象=全体 ADMIN | 不受 STUDENT 影响；学生收定向 `LAB_TASK` 通知 | ✅ 新增定向通知不改广播 |
| `TeacherService.java:59/102/121/141/162/188/266` | `!"TEACHER".equals→FORBIDDEN`；查询 `eq role=TEACHER` | 隔离正确，学生不被卷入 | ✅ 保持（技术方案 §0 明示）；学生重置密码走**新接口**（C3，落 ProjectAuthService/StudentService，不碰本类） |
| `AvatarService.java:144` | 代传他人头像须 ADMIN | STUDENT→拒绝 | ✅；本人换头像（`UserAvatarController`，角色无关按 userId）✅ 允许 |
| `RevisionService.java` | 角色仅透传 sourceRole | 兼容 | ✅ 新增 `TARGET_LAB_TASK="LAB_TASK"` 常量（target_type VARCHAR(16)，无需改表） |
| `NlqController.java:56-123` | 全带 ADMIN/TEACHER | 学生 403 | ✅（一期学生无 NLQ 入口） |

## 3. 前端角色分支

| 位置 | 逻辑 | STUDENT 现状 | 判定 |
|---|---|---|---|
| `router/index.ts:109-149` 守卫 | 角色不符 fallback `'/dashboard'`（117/135/142 三处）+ 布局 `redirect:'/dashboard'`(22) + 兜底路由(100) | 学生被弹到 dashboard → `/dashboard/teacher` 403 空壳 | 🛠 修复②：`homeForRole()` 角色化（STUDENT→`/my-tasks`）+ 学生默认拒绝（仅 `/projects*`、`/my-tasks` 放行） |
| `pages/login/LoginPage.vue:14/15/59` | "工号/请输入工号"文案与校验语 | 学生不知可学号登录 | 🛠 修复③：文案「工号/学号」（C5） |
| `LoginPage.vue:100 redirectAfterLogin` | 默认 `'/dashboard'` | 学生落地 403 壳 | 🛠 并入修复② 的 `homeForRole` |
| `store/user.ts:41-44` | `isAdmin` 布尔 | 无 isStudent 判据 | 🛠 加 `isStudent` computed |
| `constants/index.ts:16-19` ROLE | 仅 ADMIN/TEACHER | — | 🛠 加 `STUDENT:'STUDENT'` |
| `types/api.d.ts:18` `TRole` | 联合类型两值 | 类型不含 STUDENT | 🛠 扩 `'STUDENT'` |
| `layouts/BasicLayout.vue:170-184` MENU_ITEMS | 无 roles 的项（智能查询/数据智能/概览/课程管理/报表中心）对学生可见 | 学生菜单一堆死链 | 🛠 修复④：教师/管理员菜单项补 roles；学生仅「我的任务」「课题项目」；`roleLabel:189` 三元 → 三角色映射；NlqWidget 对学生隐藏 |
| `DashboardPage/CoursePage/NlqPage/ReportPage` 各处 `isAdmin ? A : B` | 学生视角会渲染"教师"壳 | 守卫修复后学生物理不可达 | 📝 不改，列入回归用例 |
| `pages/landing/LandingPage.vue` | 公开页 | 登录入口跳 /login | ✅ |

## 4. nlq-service

| 位置 | 逻辑 | 判定 |
|---|---|---|
| `app/services/permission.py:49-53` | `ADMIN` 直通，else 按 `PERMISSION_RULES` 注入 `{table}.teacher_id=:uid`，未知受控表 raise | 🛠 修复⑤：`lab_project`/`lab_project_member`/`lab_project_task`/`lab_dataset` 进白名单（后三者经 `requires` 单跳 JOIN `lab_project` 按 teacher_id 注入）；`student`/`course_student` **不进**（规则②已批准）；`lab_task_submission`/`lab_dataset_version` 因需双跳链（submission→task→project）超出单跳 requires 机制，一期不进白名单（模型引用即 raise 拒绝，安全侧）；schema_cache 走 information_schema 自动感知新表，无需改 |

## 5. 三角色回归用例清单（编码完成后自测口径）

1. ADMIN：登录→dashboard/admin→全校数据→修订学生课表→通知教师——全路径不变。
2. TEACHER：登录→dashboard/teacher→仅本人 course/experiment/report→/teachers 403——不变。
3. STUDENT：登录（学号+默认密码强制改密）→落地 /my-tasks；`/api/v1/courses|experiments|reports|dashboard|teachers|nlq|insight|schedules|students` 全部 403（修复①）；notifications 可见可标读；本人头像可换；/projects 仅成员课题。
4. 边界：学号撞教师 username → 导入行拒绝不触碰教师账号（C2）；非在管老师重置学生密码 → 403（C3）；学生调建任务/验收 → 403（双保险）。

## 6. 已确认正确、保持不动的点（防过度修改）

- RoleAspect/JwtAuthFilter/AuthService 全链路角色无关，STUDENT 零改动复用。
- TeacherService / AvatarService / ExperimentService.checkPermission 的 else-拒绝语义正确。
- NotificationService 定向通知按 userId，不受角色扩容影响。
- schedule_revision 表结构通用（target_type VARCHAR），仅扩取值。
