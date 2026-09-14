# 基于AI大模型的实验教学项目管理系统 — API/数据模型文档

> 版本：V1.2 | 日期：2026-09-14 | 状态：已修订（V1.1 按后端设计审查报告修复 P0/P1/P2；V1.2 增量：学期周历/节假日/实验室开放计划三新表与新接口，修订清单见文末附录）

---

## 0. 全局约定（V1.1 新增）

| 约定项 | 决策 | 说明 |
|--------|------|------|
| 字符集 | `utf8mb4` / `utf8mb4_unicode_ci` | MySQL 8 默认排序规则，中文排序更准确；写死进 init.sql，禁止 general_ci 混用 |
| 外键策略 | **逻辑外键，应用层保证** | 不建物理 FOREIGN KEY 约束（教务导入容错需要；表定义中 "FK →" 仅表示逻辑引用关系） |
| 分页参数 | 入参 `page`（从1起）/ `size` | MyBatis-Plus 侧映射为 `current=page` / `size=size`，由公共分页封装统一转换 |
| 时间字段 | DATETIME + `CURRENT_TIMESTAMP` 默认值 | 全部数据库服务器时间 |
| 逻辑删除 | V1 使用物理删除 + 二次确认 | 学期/教师等主数据用 status 字段停用代替删除 |
| ID 类型 | BIGINT 自增 | 雪花 ID 等分布式方案不引入（单机部署） |

---

## 1. 数据库设计

### 1.1 ER关系总览

```
user(用户) 1──N course(实验课程) 1──N experiment(实验项目)
    │
    ├── 1──N query_history(查询历史)
    ├── 1──N notification(站内通知)
    └── 1──N operation_log(操作审计，V1.1 新增)

semester(学期) 1──N schedule(课表) ──→ 自动生成 course(实验课程)
                        │
                        └── N──1 laboratory(实验室)

semester(学期) 1──N lab_open_time(开放时段) N──1 laboratory(逻辑引用 lab_code)
semester(学期) 1──N lab_open_exception(开放例外日) N──1 laboratory(逻辑引用 lab_code)
holiday(节假日) 为全局逐日表，按日期区间挂靠学期（无外键，双学期共享区间需人工避免）

dictionary(字典表) ──→ experiment(实验项目) 的枚举字段引用
schedule_time(课表时间明细) N──1 schedule(课表)
```

### 1.2 表结构定义

#### 1.2.1 user（用户表）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | 用户ID |
| username | VARCHAR(32) | ✅ | — | 登录用户名（=工号） |
| password | VARCHAR(128) | ✅ | — | BCrypt加密密码 |
| real_name | VARCHAR(32) | ✅ | — | 真实姓名 |
| role | ENUM('ADMIN','TEACHER') | ✅ | 'TEACHER' | 角色 |
| department | VARCHAR(64) | ❌ | NULL | 所属院系 |
| phone | VARCHAR(20) | ❌ | NULL | 联系电话 |
| status | TINYINT | ✅ | 1 | 0=禁用 1=启用 |
| is_default_pwd | TINYINT | ✅ | 1 | 1=默认密码（首次登录强制改密） |
| failed_attempts | INT | ✅ | 0 | 连续登录失败次数（达5次锁定，见账号锁定策略） |
| locked_until | DATETIME | ❌ | NULL | 锁定截止时间；非空且 > NOW() 时拒绝登录 |
| created_at | DATETIME | ✅ | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | ✅ | ON UPDATE | 更新时间 |

索引：`UNIQUE(username)`，`INDEX(role)`

#### 1.2.2 laboratory（实验室表）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | — |
| lab_code | VARCHAR(32) | ✅ | — | 实验室编号（如 36-401） |
| lab_name | VARCHAR(64) | ✅ | — | 实验室名称 |
| manager | VARCHAR(32) | ❌ | NULL | 实验室负责人 |
| device_count | INT | ❌ | 0 | 设备数 |
| status | TINYINT | ✅ | 1 | 0=停用 1=启用 |
| created_at | DATETIME | ✅ | CURRENT_TIMESTAMP | — |
| updated_at | DATETIME | ✅ | ON UPDATE | — |

索引：`UNIQUE(lab_code)`

#### 1.2.3 semester（学期表）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | 学期ID |
| academic_year | VARCHAR(16) | ✅ | — | 学年（如 2026-2027） |
| semester_num | TINYINT | ✅ | — | 学期序号（1或2） |
| semester_name | VARCHAR(32) | ✅ | — | 如 2026-2027-1 |
| start_date | DATE | ✅ | — | 开始日期 |
| end_date | DATE | ✅ | — | 结束日期 |
| week1_start_date | DATE | ❌ | NULL | 第 1 周起始日；空视为 start_date（V1.2 新增，供周次↔日期映射；增量脚本 `database/upgrade_20260914_semester_open.sql` 回填） |
| status | ENUM('UPCOMING','ACTIVE','ENDED') | ✅ | 自动计算 | 状态 |
| created_at | DATETIME | ✅ | CURRENT_TIMESTAMP | — |

索引：`UNIQUE(semester_name)`

#### 1.2.4 schedule（课表表）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | 课表ID |
| semester_id | BIGINT | ✅ | — | FK → semester.id |
| course_code | VARCHAR(32) | ✅ | — | 课程号 |
| course_name | VARCHAR(64) | ✅ | — | 课程名 |
| total_hours | DECIMAL(5,1) | ✅ | — | 实验总学时 |
| teacher_id | BIGINT | ✅ | — | FK → user.id |
| lab_code | VARCHAR(32) | ✅ | — | 上课地点（实验室编号） |
| class_names | VARCHAR(256) | ✅ | — | 上课班级（逗号分隔） |
| student_count | INT | ✅ | — | 选课人数 |
| raw_schedule_text | VARCHAR(256) | ❌ | NULL | 原始上课时间文本（兜底） |
| created_at | DATETIME | ✅ | CURRENT_TIMESTAMP | — |

索引：`INDEX(semester_id)`，`INDEX(teacher_id)`，`INDEX(course_code)`

#### 1.2.5 schedule_time（课表时间明细表）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | — |
| schedule_id | BIGINT | ✅ | — | FK → schedule.id |
| day_of_week | TINYINT | ✅ | — | 星期几（1=周一 ... 7=周日） |
| start_section | TINYINT | ✅ | — | 开始节次 |
| end_section | TINYINT | ✅ | — | 结束节次 |
| week_range | VARCHAR(32) | ❌ | NULL | 周次范围（如 1-16） |
| week_type | ENUM('ALL','ODD','EVEN') | ✅ | 'ALL' | 全部/单周/双周 |

索引：`INDEX(schedule_id)`

#### 1.2.6 course（实验课程表）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | — |
| course_code | VARCHAR(32) | ✅ | — | 课程号 |
| course_name | VARCHAR(64) | ✅ | — | 课程名 |
| semester_id | BIGINT | ✅ | — | FK → semester.id |
| teacher_id | BIGINT | ✅ | — | FK → user.id |
| total_hours | DECIMAL(5,1) | ✅ | — | 实验总学时 |
| major | VARCHAR(128) | ❌ | NULL | 面向专业（从班级名解析） |
| created_at | DATETIME | ✅ | CURRENT_TIMESTAMP | — |

索引：`UNIQUE(course_code, semester_id)`，`INDEX(teacher_id)`

#### 1.2.7 experiment（实验项目表）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | — |
| experiment_code | VARCHAR(32) | ✅ | — | 实验编号（自动生成） |
| course_id | BIGINT | ✅ | — | FK → course.id |
| experiment_name | VARCHAR(128) | ✅ | — | 实验名称 |
| category | VARCHAR(32) | ✅ | — | 实验类别（字典值） |
| type | VARCHAR(32) | ✅ | — | 实验类型（字典值） |
| subject | VARCHAR(64) | ✅ | — | 实验所属学科（字典值） |
| requirement | VARCHAR(16) | ✅ | — | 实验要求（字典值） |
| experimenter_type | VARCHAR(16) | ✅ | — | 实验者类别（字典值） |
| group_size | INT | ✅ | 1 | 每组人数 |
| hours | DECIMAL(4,1) | ✅ | — | 实验学时数 |
| sort_order | INT | ✅ | 0 | 排序序号 |
| created_at | DATETIME | ✅ | CURRENT_TIMESTAMP | — |
| updated_at | DATETIME | ✅ | ON UPDATE | — |

索引：`UNIQUE(experiment_code)`，`INDEX(course_id)`

#### 1.2.8 dictionary（字典表）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | — |
| dict_type | VARCHAR(32) | ✅ | — | 字典类型（category/type/subject/requirement/experimenter_type） |
| dict_label | VARCHAR(64) | ✅ | — | 显示值（如"基础实验"） |
| dict_value | VARCHAR(64) | ✅ | — | 存储值 |
| sort_order | INT | ✅ | 0 | 排序 |
| status | TINYINT | ✅ | 1 | 0=禁用 1=启用 |

索引：`INDEX(dict_type)`

#### 1.2.9 query_history（查询历史表）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | — |
| user_id | BIGINT | ✅ | — | FK → user.id |
| question | TEXT | ✅ | — | 用户输入的自然语言问题 |
| generated_sql | TEXT | ❌ | NULL | 生成的SQL |
| result_summary | VARCHAR(256) | ❌ | NULL | 结果摘要（如"返回8条记录"） |
| success | TINYINT | ✅ | 1 | 0=失败 1=成功 |
| role | VARCHAR(16) | ✅ | — | 执行时用户角色（ADMIN/TEACHER，排查权限问题必需） |
| created_at | DATETIME | ✅ | CURRENT_TIMESTAMP | — |

索引：`INDEX(user_id, created_at)`

#### 1.2.10 notification（站内通知表，P0 修复：PRD US-05 落地）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | — |
| user_id | BIGINT | ❌ | NULL | 接收人；NULL=全体广播（如学期提醒发给管理员组） |
| title | VARCHAR(128) | ✅ | — | 通知标题 |
| content | VARCHAR(512) | ✅ | — | 通知正文 |
| type | VARCHAR(32) | ✅ | — | SEMESTER_REMIND / SYSTEM |
| ref_url | VARCHAR(128) | ❌ | NULL | 点击跳转前端路由（如 /semesters/create） |
| is_read | TINYINT | ✅ | 0 | 0=未读 1=已读 |
| created_at | DATETIME | ✅ | CURRENT_TIMESTAMP | — |

索引：`INDEX(user_id, is_read)`，`INDEX(type, created_at)`

**定时任务**：每年 8月15日 / 1月15日 `@Scheduled` 扫描触发，向全体管理员写入 SEMESTER_REMIND 通知（仅提醒，不自动创建学期——已拍板决策）。

#### 1.2.11 operation_log（操作审计日志表，P0 修复）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | — |
| user_id | BIGINT | ✅ | — | 操作人 FK → user.id |
| action | VARCHAR(64) | ✅ | — | 操作类型（SCHEDULE_IMPORT / EXPERIMENT_CREATE / PASSWORD_RESET ...） |
| target_type | VARCHAR(32) | ✅ | — | 目标实体（course/experiment/schedule/user...） |
| target_id | BIGINT | ❌ | NULL | 目标记录ID |
| detail | JSON | ❌ | NULL | 变更明细（导入结果统计、修改前后快照等） |
| ip | VARCHAR(45) | ✅ | — | 客户端IP（兼容IPv6） |
| created_at | DATETIME | ✅ | CURRENT_TIMESTAMP | — |

索引：`INDEX(user_id, created_at)`，`INDEX(target_type, target_id)`

**记录方式**：自定义注解 `@OperationLog` + AOP 切面自动拦截，业务代码零侵入。

#### 1.2.12 holiday（节假日表，V1.2 新增）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | — |
| holiday_date | DATE | ✅ | — | 停课日（逐日一行，UI 按日期区间批量展开写入） |
| name | VARCHAR(64) | ✅ | — | 名称（如 国庆节） |
| created_at | DATETIME | ✅ | CURRENT_TIMESTAMP | — |

索引：`UNIQUE(holiday_date)`。按学期过滤 = `holiday_date ∈ [semester.start_date, semester.end_date]`；重复区间新增时拒绝（400）。

#### 1.2.13 lab_open_time（实验室开放时段表，V1.2 新增）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | — |
| lab_code | VARCHAR(32) | ✅ | — | FK → laboratory.lab_code（逻辑引用） |
| semester_id | BIGINT | ✅ | — | FK → semester.id |
| day_of_week | TINYINT | ✅ | — | 1=周一 … 7=周日 |
| start_section | TINYINT | ✅ | — | 开始节次 |
| end_section | TINYINT | ✅ | — | 结束节次 |
| week_range | VARCHAR(32) | ✅ | — | 生效周次范围（如 1-16） |
| week_type | ENUM('ALL','ODD','EVEN') | ✅ | 'ALL' | 全周/单周/双周 |
| audience | VARCHAR(16) | ✅ | '全校' | 开放对象（全校/本院/指定专业/教师专用，代码内固定枚举，不进字典表） |
| enabled | TINYINT | ✅ | 1 | 0=停用 1=启用（全停用视为未开放） |
| remark | VARCHAR(255) | ❌ | NULL | 备注 |
| created_at | DATETIME | ✅ | CURRENT_TIMESTAMP | — |
| updated_at | DATETIME | ✅ | ON UPDATE | — |

索引：`INDEX(semester_id)`，`INDEX(lab_code, semester_id)`。与 `schedule_time` 同构（星期+节次+周范围+单双周），占用比对为同字段直接可比。

#### 1.2.14 lab_open_exception（开放例外日表，V1.2 新增）

| 字段名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| id | BIGINT | ✅ | 自增主键 | — |
| lab_code | VARCHAR(32) | ✅ | — | FK → laboratory.lab_code（逻辑引用） |
| semester_id | BIGINT | ✅ | — | FK → semester.id |
| start_date | DATE | ✅ | — | 闭馆开始日期 |
| end_date | DATE | ✅ | — | 闭馆结束日期 |
| reason | VARCHAR(128) | ❌ | NULL | 事由（如 设备检修） |
| created_at | DATETIME | ✅ | CURRENT_TIMESTAMP | — |

索引：`INDEX(lab_code, semester_id)`。例外日与节假日同样渲染为「闭馆」；不参与课表矛盾判定。

> 三张新表与 `semester.week1_start_date` 的 DDL 以 `database/init.sql`（权威）为准，`backend/database/init.sql`（副本）同步；存量库走增量脚本 `database/upgrade_20260914_semester_open.sql`。种子数据由外置脚本 `scripts/seed_lab_openings.py` 生成，**不进 init.sql**。

---

## 2. API接口定义

### 2.1 接口规范

- 基础路径：`/api/v1`
- 认证方式：Header `Authorization: Bearer <jwt_token>`
- 响应格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

- 错误响应：

```json
{
  "code": 400,
  "message": "错误描述",
  "data": null
}
```

- 分页响应：

```json
{
  "code": 200,
  "data": {
    "records": [...],
    "total": 100,
    "page": 1,
    "size": 20
  }
}
```

**错误码枚举（P1 修复：ErrorCode.java 落地定义）**：

| code | 含义 | 典型场景 |
|------|------|---------|
| 200 | 成功 | — |
| 400 | 参数错误 | 字段校验失败、格式错误 |
| 401 | 未认证 | Token 缺失/过期/无效 |
| 403 | 无权限 | 角色不匹配、账号锁定、NL2SQL 内部令牌校验失败 |
| 404 | 资源不存在 | — |
| 429 | 请求过频 | 触发限流（每用户每分钟10次） |
| 500 | 服务器内部错误 | 未预期异常 |
| 41001 | 账号已锁定 | 剩余锁定时间见 message |
| 42001 | 课表导入-教师工号不存在 | 错误行号见 message |
| 42002 | 课表导入-实验室编号不存在 | 错误行号见 message |
| 42003 | 课表导入-同一课程号存在多位教师 | 整组拒绝 |
| 43001 | 上学期无实验项目 | 复制功能兜底（前端按钮置灰） |
| 44001 | NL2SQL 问题无法安全转换 | 含写操作/危险函数 |
| 44002 | NL2SQL 无法建立数据权限 | 注入失败拒绝执行 |
| 44003 | NL2SQL 外部模型超时/不可用 | 降级提示 |

**RESTful 规范（V1.1 统一）**：部分更新用 `PATCH`；批量删除用 `POST .../batch-delete`；路径不以 `/` 结尾；调试接口 `/schema` 仅 ADMIN 角色可用且生产环境可配置禁用。

### 2.2 认证模块 `/api/v1/auth`

| 方法 | 路径 | 说明 | 请求体 | 响应 |
|------|------|------|--------|------|
| POST | `/login` | 用户登录 | `{username, password}` | `{token, user: {id, username, realName, role, isDefaultPwd}}` |
| POST | `/change-password` | 修改密码 | `{oldPassword, newPassword}` | `{success}` |
| GET | `/me` | 获取当前用户信息 | — | `{id, username, realName, role, department}` |

### 2.3 实验室模块 `/api/v1/laboratories`（管理员）

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `` | 分页查询实验室列表 | `?page=1&size=20&keyword=36栋` |
| POST | `` | 新增实验室 | `{labCode, labName, manager, deviceCount}` |
| PUT | `/{id}` | 编辑实验室 | `{labName, manager, deviceCount, status}` |
| DELETE | `/{id}` | 删除实验室（有关联数据时拒绝） | — |
| POST | `/import` | Excel批量导入 | `multipart/form-data` (file) |
| GET | `/import/template` | 下载导入模板 | — 返回Excel文件 |

### 2.4 学期模块 `/api/v1/semesters`（管理员）

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `` | 查询所有学期 | `?status=ACTIVE` |
| GET | `/current` | 获取当前学期 | — 按日期动态匹配（寒暑假空档返回最近结束的学期） |
| POST | `` | 创建新学期 | `{academicYear, semesterNum, startDate, endDate}` |
| PUT | `/{id}` | 编辑学期（仅未开始的） | `{startDate, endDate}` |

### 2.5 课表模块 `/api/v1/schedules`（管理员）

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `` | 分页查询课表 | `?semesterId=1&page=1&size=20` |
| POST | `/import` | Excel导入课表 | `multipart/form-data` (file, semesterId) |
| GET | `/import/template` | 下载课表导入模板 | — |
| GET | `/import/preview` | 导入预览（先解析不入库） | `file` |
| POST | `/batch-delete` | 批量删除课表（RESTful 规范修正） | `{ids: [1,2,3]}` |

### 2.6 教师账号模块 `/api/v1/teachers`（管理员）

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `` | 分页查询教师列表 | `?page=1&size=20&keyword=张三` |
| POST | `/import` | Excel批量导入教师 | `multipart/form-data` (file) |
| POST | `/{id}/reset-password` | 重置教师密码 | — 重置为默认密码 `Ltpms@{工号}` |
| PATCH | `/{id}/status` | 启用/禁用教师（部分更新用 PATCH，规范修正） | `{status: 0或1}` |

### 2.7 实验课程模块 `/api/v1/courses`

| 方法 | 路径 | 说明 | 角色 | 参数 |
|------|------|------|------|------|
| GET | `` | 查询课程列表 | 管理员:全部 教师:自己的 | `?semesterId=1` |
| GET | `/{id}` | 课程详情 | 管理员/教师 | — |
| GET | `/{id}/experiments` | 课程的实验项目列表 | 管理员/教师 | — |

### 2.8 实验项目模块 `/api/v1/experiments`

| 方法 | 路径 | 说明 | 角色 | 参数 |
|------|------|------|------|------|
| GET | `` | 查询实验项目列表 | 教师(自己的) | `?courseId=1` |
| POST | `` | 新增实验项目 | 教师 | `{courseId, experimentName, category, type, ...}` |
| PUT | `/{id}` | 编辑实验项目 | 教师(当前学期) | `{experimentName, category, ...}` |
| DELETE | `/{id}` | 删除实验项目 | 教师(当前学期) | — |
| POST | `/import` | Excel批量导入 | 教师 | `multipart/form-data` (file, courseId) |
| GET | `/import/template` | 下载导入模板 | 教师 | — |
| POST | `/copy-from-previous` | 从上学期复制 | 教师 | `{courseId}` |

### 2.9 报表模块 `/api/v1/reports`

> **P0 修复：分层可见（2026-09-07 拍板，选项 B）**——教师能看全校汇总行 + 自己任教明细行，看不到其他教师明细行。管理员看全量。

| 方法 | 路径 | 说明 | 角色 | 参数 |
|------|------|------|------|------|
| GET | `/course-project-stats` | 实验室课程项目统计 | 管理员/教师 | `?academicYear=2026-2027&aggregate_only=false` |
| GET | `/person-hours` | 人时数统计 | 管理员/教师 | `?academicYear=2026-2027&aggregate_only=false` |
| GET | `/course-project-stats/export` | 导出课程项目统计 | 管理员/教师 | `?format=excel/pdf&aggregate_only=false` |
| GET | `/person-hours/export` | 导出人时数统计 | 管理员/教师 | `?format=excel/pdf&aggregate_only=false` |

**`aggregate_only` 参数语义**：

| 调用者 | 默认值 | 行为 |
|--------|--------|------|
| ADMIN | false | 返回全量明细 |
| TEACHER | **true** | 返回 `summary`（全校汇总行：课程总数、项目总数、人时数合计）+ `detail`（仅本人任教明细行） |

教师显式传 `aggregate_only=false` 时服务端**强制忽略并按 true 处理**（不能通过参数越权）。响应结构：

```json
{
  "code": 200,
  "data": {
    "summary": { "courseCount": 186, "experimentCount": 1423, "totalPersonHours": 50000 },
    "detail": [ { "labCode": "36-401", "courseName": "...", "personHours": 320 } ],
    "detailScope": "SELF"
  }
}
```

NL2SQL 同理：教师问"全校共开设多少门课" → 允许执行，返回全校汇总数字（零明细行）。

### 2.10 自然语言查询模块 `/api/v1/nlq`

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| POST | `/query` | 自然语言查询 | `{question: "显示2024-2025学年..."}`（用户身份由JWT解析注入，请求体不含身份字段） |
| GET | `/schema` | 获取Schema信息 | **仅 ADMIN，生产环境可配置禁用**（调试接口，规范修正） |
| GET | `/history` | 查询历史列表 | `?page=1&size=20` |
| DELETE | `/history/{id}` | 删除查询历史 | — |

> 注：NL2SQL 请求链路为 前端 → Spring Boot（JWT鉴权）→ FastAPI（内部令牌）→ MySQL（只读账号）。`/query` 结果强制 LIMIT 500 保护。

**阶段3 新增接口（智能化升级 V3）：**

| 方法 | 路径 | 说明 | 角色 | 参数 |
|------|------|------|------|------|
| POST | `/api/v1/nlq/agent` | 多轮 Agent 对话（阿实） | ADMIN/TEACHER | `{question, history?}` → `{answer, steps, steps_used, error}` |
| POST | `/api/v1/nlq/recognize-schedule` | 课表照片识别（GLM-4V） | ADMIN/TEACHER | `{image_base64}` → `{success, rows, error}` |
| POST | `/api/v1/nlq/archive` | 实验台数字档案（AI 生成，缓存 24h） | ADMIN/TEACHER | `{entity_type: laboratory\|experiment, entity_id}` → `{success, text, cached, stats, error}` |
| POST | `/api/v1/nlq/narrative` | 叙事报告/教学总结 | ADMIN/TEACHER | `{kind: annual_report\|teaching_summary}` → `{success, text, numbers, scope, error}` |

> - `/archive`：统计数字由 Python 真实聚合，LLM 只做语言包装；生成失败时前端降级展示 `stats`。
> - `/narrative`：权限分层沿用 `aggregate_only` 语义——TEACHER 强制个人口径（kind 归一为 teaching_summary），ADMIN 全校口径。
> - **MCP Server（V3）**：独立进程 `python -m mcp_server.server`（在 nlq-service 目录下），stdio/SSE 两种暴露方式，只读工具 `query_laboratories / query_courses / query_experiments / query_schedules`；安全开关 `MCP_ENABLED`、SSE 令牌 `MCP_TOKEN`、行数上限 `MCP_MAX_ROWS`（默认 100）。`ltpms_readonly` 为全校级只读、未按角色隔离，MCP 仅用于本地演示。

**NL2SQL查询请求/响应详细定义：**

```
POST /api/v1/nlq/query

请求体：
{
  "question": "显示2024-2025学年第1学期36栋401实验室开设的实验课程"
}

成功响应：
{
  "code": 200,
  "data": {
    "success": true,
    "sql": "SELECT c.course_code, c.course_name FROM course c ...",
    "columns": ["课程号", "课程名", "授课教师", "实验项目数"],
    "rows": [
      ["CS101", "数据结构实验", "张三", 8],
      ["CS201", "操作系统实验", "李四", 6]
    ],
    "suggestions": [
      "统计这些课程的实验人时数",
      "列出CS101的所有实验项目"
    ],
    "rowCount": 2,
    "executionTime": 3200
  }
}

失败响应：
{
  "code": 200,
  "data": {
    "success": false,
    "error": "您的问题涉及数据修改操作，本系统仅支持查询",
    "sql": null,
    "suggestions": ["您可以试试：显示所有实验室的列表"]
  }
}
```

### 2.11 字典模块 `/api/v1/dicts`（管理员）

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `/type/{dictType}` | 按类型查字典列表 | — （教师亦可调用：导入模板/下拉框需要，公开只读） |
| POST | `` | 新增字典项 | `{dictType, dictLabel, dictValue, sortOrder}` |
| PUT | `/{id}` | 编辑字典项 | `{dictLabel, dictValue, sortOrder, status}` |
| DELETE | `/{id}` | 删除字典项 | — |

### 2.12 通知模块 `/api/v1/notifications`（V1.1 新增，P0 修复）

| 方法 | 路径 | 说明 | 角色 | 参数 |
|------|------|------|------|------|
| GET | `` | 我的通知列表（分页） | 所有登录用户 | `?page=1&size=20&isRead=false` |
| GET | `/unread-count` | 未读数量（顶栏红点） | 所有登录用户 | — |
| PUT | `/{id}/read` | 标记已读 | 所有登录用户 | — |
| PUT | `/read-all` | 全部标记已读 | 所有登录用户 | — |

> 学期提醒通知由后端定时任务（每年 8/15、1/15）自动写入，无对外创建接口。

### 2.13 统计概览 `/api/v1/dashboard`

| 方法 | 路径 | 说明 | 角色 |
|------|------|------|------|
| GET | `/admin` | 管理员首页概览 | 管理员 |
| GET | `/teacher` | 教师首页概览 | 教师 |

管理员概览数据：当前学期实验室总数、课程总数、实验项目总数、教师总数
教师概览数据：当前学期我的课程数、实验项目数、最近查询记录


### 2.14 学期与实验室开放管理模块（V1.2 新增）

> 权限边界（写死）：校历 / 节假日 / 开放计划 / 例外日**仅 ADMIN 可写**；GET 类接口所有登录用户可读（TEACHER 只读，含 NLQ 只读可问）。四个写接口组均挂 `@OperationLog` 审计注解。

| 方法 | 路径 | 说明 | 角色 | 参数 / 请求体 |
|------|------|------|------|---------------|
| GET | `/semesters/{id}/calendar` | 周历：week1StartDate + 周次↔日期表（周起始日=week1_start_date+(w-1)×7；空按 start_date） | 登录 | — |
| PUT | `/semesters/{id}/calendar` | 设置第 1 周起始日 | ADMIN | `{week1StartDate}`（非周一仅提示不强拦） |
| GET | `/holidays?semesterId=` | 该学期节假日列表 | 登录 | 过滤 = `holiday_date ∈ [start_date, end_date]` |
| POST | `/holidays` | 新增节假日（日期区间逐日展开） | ADMIN | `{startDate, endDate, name}` → data = 写入天数；区间已有任一重复 → 400 |
| DELETE | `/holidays/{id}` | 删除单日节假日 | ADMIN | — |
| GET | `/lab-openings?semesterId=&labCode=` | 开放时段列表 | 登录 | 两参数均可选 |
| POST | `/lab-openings` | 新增单条开放时段 | ADMIN | `{labCode, semesterId, dayOfWeek, startSection, endSection, weekRange, weekType, audience, enabled, remark}`（PUT 更新须传全量字段体） |
| PUT | `/lab-openings/{id}` | 编辑开放时段 | ADMIN | 同上（全量） |
| DELETE | `/lab-openings/{id}` | 删除开放时段 | ADMIN | — |
| POST | `/lab-openings/batch` | 批量**覆盖式**设置（选中实验室先删后插） | ADMIN | `{semesterId, labCodes[], slots[], audience?, remark?}` → data = 写入行数 |
| POST | `/lab-openings/copy` | 从上学期复制（覆盖式、**幂等**） | ADMIN | `{fromSemesterId, toSemesterId, labCodes?}` → data = 复制实验室数 |
| GET | `/lab-openings/overview?semesterId=` | 占用总览：每实验室 开放节时/占用节时/利用率/矛盾数/是否已配置 | 登录 | 口径见 D13（周加权；未配置 → rate=null） |
| GET | `/lab-openings/grid?semesterId=&labCode=&week?` | 单间周网格：7 天×12 节四色态（OCCUPIED/OPEN/NOT_OPEN/CLOSED）+ 学期闭馆日集合 | 登录 | week 缺省=当前周（课外自动夹取） |
| GET | `/lab-openings/today?semesterId?` | 今日开放实验室（仪表盘小块 / 教师端）：含 inSemester/weekNo/holiday 闭馆判定、相邻节次合并、开放对象聚合 | 登录 | 缺省当前学期 |
| GET | `/lab-open-exceptions?semesterId=&labCode=` | 例外日列表 | 登录 | — |
| POST | `/lab-open-exceptions` | 新增例外日 | ADMIN | `{labCode, semesterId, startDate, endDate, reason}` |
| DELETE | `/lab-open-exceptions/{id}` | 删除例外日 | ADMIN | — |

**既有接口口径扩展（回归要求）**：

| 接口 | 变更 |
|------|------|
| `/insight/utilization` | 返回结构扩展：新增 `openSlotHours` / `occupiedSlotHours` / `rate`（未配置为 null）/ `planned`；旧字段保留（利用率 = 占用节时 ÷ 开放节时，学期累计周加权，单/双周减半；**不含**节假日/例外日扣减；允许 >100%） |
| `/insight/anomalies` | 新增规则 `NOT_OPEN`「有课但实验室未开放」（与开放计划的覆盖校验同源） |
| `/insight/all` | 一站式聚合，与上述同源，回归须覆盖 |
| `/insight/readiness`、`/insight/conflicts` | 逻辑未变，纳入回归冒烟 |

**NL2SQL 权限白名单（V1.2）**：`holiday` / `lab_open_time` / `lab_open_exception` 加入公开只读白名单（教师查询不注入行级约束，同 laboratory/semester/dictionary）；Prompt 侧补充开放口径（audience 枚举、week_type 语义、enabled=1 生效、例外日=闭馆）与周历口径提示。

---

## 附录：V1.1 修订记录（2026-09-07）

| 编号 | 修复项 | 对应审查报告 |
|------|--------|-------------|
| 1 | 新增 §0 全局约定（字符集/外键/分页/时间/删除/ID 策略） | P2-6 / P2-7 / P2-8 |
| 2 | user 表新增 failed_attempts、locked_until 字段 | P0-5 |
| 3 | 新增 notification 表（§1.2.10）+ 通知 API（§2.12） | P0-3 |
| 4 | 新增 operation_log 表（§1.2.11） | P0-4 |
| 5 | query_history 新增 role 字段 | P2-9 |
| 6 | 错误码枚举落地（41001~44003 等） | P1-5 |
| 7 | RESTful 修正：PATCH /status、POST /batch-delete、去尾斜杠、/schema 限权 | P1-6 |
| 8 | 报表接口 aggregate_only 参数（分层可见落地） | P0-8 |
| 9 | 默认密码改 `Ltpms@{工号}` | P1-1 |
| 10 | /current 寒暑假判定语义补全 | P1-8 |
| 11 | 字典 GET 对教师开放只读（导入模板需要） | 权限澄清 |

---

## 附录：V1.2 修订记录（2026-09-14）

来源：《LTPMS_学期与实验室开放管理_需求拷问记录.md》V1.1（D17：增量同步，不全量重写）。

| 编号 | 修订项 | 落点 |
|------|--------|------|
| 1 | semester 新增 `week1_start_date`（周历字段，空按 start_date） | §1.2.3 |
| 2 | 新增 holiday / lab_open_time / lab_open_exception 三表 | §1.2.12~1.2.14 |
| 3 | ER 关系补开放管理三表挂靠关系 | §1.1 |
| 4 | 新增 §2.14 学期与实验室开放管理接口（周历 / 节假日 / 开放计划 CRUD+批量+复制 / 例外日 / 占用总览 / 周网格 / today） | §2.14 |
| 5 | `/insight/utilization` 结构扩展（openSlotHours / occupiedSlotHours / rate / planned）与 `/insight/anomalies` 新增 NOT_OPEN 规则，附回归要求 | §2.14 |
| 6 | NL2SQL 公开只读白名单补三表 + 开放/周历口径提示 | §2.14 |
| 7 | 种子数据外置（`scripts/seed_lab_openings.py`，不进 init.sql） | §1.2.14 注 |

---

*API/数据模型文档确认完毕后，进入阶段4：UI设计规范。*

**这份API和数据模型设计是否OK？有要调整的吗？**
