# LTPMS 后端开发文档（Spring Boot 实施版）

> 版本：V1.0 | 日期：2026-09-07 | 状态：实施依据（编码前必读）
> 定位：本文档是 **实施态** 文档。设计态文档共三份，本文只做「翻译 + 裁决」，不重复其内容：
> - `LTPMS_技术设计文档.md` V1.1 —— 架构、三层/四层防护、时序图
> - `LTPMS_API设计文档.md` V1.1 —— 表结构、接口契约、错误码（**接口唯一权威来源**）
> - `LTPMS_开发指南.md` V1.1 —— 目录树、开发规范、FAQ
>
> 冲突裁决规则：API 文档 > 本文档 > 技术设计文档。发现新冲突时先改文档再写代码。

---

## 1. 技术栈与版本锁定（含本机环境实测结论）

| 项 | 锁定版本 | 本机实测状态 |
|----|---------|-------------|
| JDK | 17 LTS | ✅ 17.0.16（`C:\Users\ZhuanZ\.jdks\ms-17.0.16`） |
| Spring Boot | **3.3.x**（工程实际 3.3.3，2026-09-07 已编译通过并成功启动） | ✅ 实测 |
| 构建工具 | **Maven Wrapper (mvnw)** | ✅ 本机 Maven 3.9.11 本体完好；但 `bin/mvn` shell 脚本在 Git Bash 下因 MSYS 路径转换 bug 不可用 → **一律用 mvnw，不修系统 mvn**（本会话用 launcher 直调验证编译，等价） |
| 数据库 | MySQL 8.4，`utf8mb4_unicode_ci` | ✅ 8.4.6；LTPMS 专用实例跑在 **127.0.0.1:3307**（data 目录 `.tools/mysql-data`），与用户原有 3306 实例隔离 |
| ORM | MyBatis-Plus 3.5.7 | ✅ 实测 |
| itext | **8.0.5**（工程实际；本文初稿写 7.2.6，以能编译的工程为准） | ✅ 实测 |
| poi-ooxml | 5.2.5（工程实际） | ✅ |
| NLQ 服务 | FastAPI + DeepSeek（独立进程，8001） | 未实现（M5 里程碑） |
| 前端 | Vue3 工程 `frontend/`（已完成） | ✅ dev 5173，代理 `/api → 8080` |

**版本假设声明**：以上第三方版本均为编写时经查证的稳定版；若拉取时坐标不存在，以 Maven Central 实际最新 patch 为准替换，**只允许升 patch，不允许跨 minor**。

---

## 2. 工程结构（backend/）

```
backend/
├── mvnw / mvnw.cmd / .mvn/wrapper/       # Maven Wrapper（自带，不依赖系统 mvn）
├── pom.xml
├── database/
│   ├── init.sql                          # 建库建表（utf8mb4_unicode_ci）+ 业务账号 + 只读账号
│   └── dict_data.sql                     # 字典种子数据
└── src/main/
    ├── java/com/ltpms/
    │   ├── LtpmsApplication.java
    │   ├── common/                       # 通用基建层（§5）
    │   │   ├── Result.java  PageResult.java  ErrorCode.java
    │   │   ├── BusinessException.java  GlobalExceptionHandler.java
    │   │   └── PageQuery.java            # page/size 入参基类
    │   ├── config/                       # SecurityConfig、MybatisPlusConfig、
    │   │                                 # CorsConfig、SchedulingConfig、RateLimitConfig
    │   ├── security/                     # JwtUtil、JwtAuthFilter、UserContext(ThreadLocal)、RoleAspect
    │   ├── annotation/                   # @RequireRole、@OperationLog
    │   ├── aspect/                       # OperationLogAspect
    │   ├── entity/                       # 11 张表实体（§6）
    │   ├── mapper/                       # MyBatis-Plus Mapper
    │   ├── dto/                          # 请求/响应 DTO（按模块分包 auth/dto、report/dto…）
    │   ├── service/  service/impl/       # 业务逻辑（Controller 零业务逻辑）
    │   └── controller/                   # 12 个 Controller（§7 接口索引）
    └── resources/
        ├── application.yml               # 公共配置
        ├── application-dev.yml           # 本地开发（本机 MySQL）
        ├── fonts/                        # PDF 中文字体（SourceHanSansSC-Regular.otf）
        └── sql/                          # 与 database/ 同步的 classpath 备份
```

---

## 3. 依赖清单（pom.xml 锁定表）

| 依赖 | 版本 | 用途 |
|------|------|------|
| spring-boot-starter-web | 3.3.5 | MVC |
| spring-boot-starter-validation | 3.3.5 | 参数校验（@Valid） |
| spring-boot-starter-aop | 3.3.5 | @OperationLog / RoleAspect |
| mybatis-plus-spring-boot3-starter | 3.5.7 | ORM（**注意 spring-boot3 专用 starter**） |
| mysql-connector-j | 8.4.0 | 驱动 |
| jjwt-api / impl / jackson | 0.12.6 | JWT（API 与 runtime 分离） |
| poi-ooxml | 5.3.0 | Excel 导入导出 |
| itext7-core / kernel / layout | 7.2.6 | PDF 导出（**必须自带中文字体**，见 §7.8） |
| hutool-all | 5.8.32 | 工具（JSON、BCrypt 备选） |
| spring-security-crypto | 3.3.5 | **只用 BCryptPasswordEncoder，不引入全量 Security 过滤链** |
| lombok | — | 简化实体 |

> 明确**不引入**：spring-boot-starter-security（自研 JwtAuthFilter 足够，避免 Security 链与自定义 401/403 语义打架——开发指南已拍板）。

---

## 4. 配置管理

### 4.1 application.yml（公共）

```yaml
spring:
  profiles:
    active: dev
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
mybatis-plus:
  global-config:
    db-config:
      id-type: auto
  configuration:
    map-underscore-to-camel-case: true
ltpms:
  jwt:
    secret: ${JWT_SECRET}          # ≥32 字节，禁止硬编码
    expiration-hours: 2
  nlq:
    base-url: ${NLQ_SERVICE_URL:http://localhost:8001}
    internal-token: ${NLQ_INTERNAL_TOKEN}   # 服务间鉴权（P0-1 修复项）
    timeout-ms: 15000                       # 15s 超时，不重试
    row-limit: 500                          # 强制 LIMIT 保护
  rate-limit:
    nlq-per-minute: 10
  upload:
    max-rows: 2000                          # 导入上限行数
```

### 4.2 环境变量表（部署时注入，缺一拒绝启动）

| 变量 | 说明 |
|------|------|
| `DB_URL / DB_USER / DB_PASSWORD` | 业务账号（ALL PRIVILEGES） |
| `JWT_SECRET` | ≥32 字节随机 |
| `NLQ_INTERNAL_TOKEN` | 与 NLQ 服务共享的内部令牌 |
| `NLQ_READONLY_PASSWORD` | 只读账号密码（配置在 NLQ 服务侧，Spring 不持有） |

启动校验：`@PostConstruct` 校验以上变量非空，缺失即 fail-fast（防止裸奔上线）。

---

## 5. 通用基建层设计

| 类 | 职责 | 关键决策 |
|----|------|---------|
| `Result<T>` | `{code, message, data}` 统一包装 | 与 API 文档 §2.1 完全一致 |
| `ErrorCode` | 枚举 200/400/401/403/404/429/500 + **41001~44003** | 唯一来源，禁止魔法数字 |
| `BusinessException` | 业务异常，携带 ErrorCode | Service 层唯一异常出口 |
| `GlobalExceptionHandler` | @RestControllerAdvice | 校验失败→400；Business→其 code；兜底→500（日志 ERROR，不泄栈） |
| `JwtUtil` | HMAC-SHA256 签发/解析 | payload: `{uid, role, exp}` |
| `JwtAuthFilter` | OncePerRequestFilter | 放行 `/auth/login`；401 写 Result JSON |
| `UserContext` | ThreadLocal<用户> | Filter 写入，拦截器 afterCompletion 清除（防线程池污染） |
| `RoleAspect` | @RequireRole 切面 | 读 UserContext，不匹配→403 |
| `OperationLogAspect` | @OperationLog 切面 | 异步写 operation_log（JSON detail），失败不影响主流程 |
| `RateLimiter`（NLQ） | 每用户每分钟 10 次 | 单机内存滑动窗口（V1 拍板，不引入 Redis） |

---

## 6. 数据模型与 Mapper

11 张表 → 实体（camelCase ↔ snake_case 自动映射）。**逻辑外键，不建物理约束**（API 文档 §0 拍板）。

| 表 | 实体 | 关键约束/索引 |
|----|------|--------------|
| user | User | UNIQUE(username)；failed_attempts/locked_until |
| laboratory | Laboratory | UNIQUE(lab_code)；删除前检查 schedule 引用 |
| semester | Semester | UNIQUE(semester_name)；status **动态计算**不落库 |
| schedule | Schedule | INDEX(semester_id, teacher_id, course_code) |
| schedule_time | ScheduleTime | INDEX(schedule_id)；week_type ENUM |
| course | Course | UNIQUE(course_code, semester_id) ← 课程归并锚点 |
| experiment | Experiment | UNIQUE(experiment_code)（§7.3 生成规则） |
| dictionary | Dictionary | INDEX(dict_type) |
| query_history | QueryHistory | INDEX(user_id, created_at)；**role 字段必填** |
| notification | Notification | INDEX(user_id, is_read)；user_id NULL=广播 |
| operation_log | OperationLog | INDEX(user_id, created_at)；detail 为 JSON 字符串 |

**semester.status 不落库**：实体不映射 status 列，查询时按 `NOW() vs start_date/end_date` 计算返回（技术设计拍板：寒暑假空档“当前学期”=最近结束的学期）。

---

## 7. 核心业务实现要点（按风险排序）

### 7.1 认证与账号锁定（auth）

1. 登录：查 username → 若 `locked_until > NOW()` → **41001**（message 带剩余分钟）。
2. 密码 BCrypt 校验失败 → `failed_attempts + 1`；达到 **5** 次 → `locked_until = NOW() + 30min`。
3. 成功 → 清零计数、签发 JWT；返回体含 `isDefaultPwd`（前端据此强制改密）。
4. `POST /auth/change-password`：校验旧密码 + 新密码强度（≥8 位）→ 更新 BCrypt 哈希 + `is_default_pwd=0`。
5. 默认密码规则：`Ltpms@{工号}`（教师导入时生成，is_default_pwd=1）。

### 7.2 课表导入（风险最高，含课程归并）

流程（一个 `@Transactional`）：

```
解析 Excel → 行级校验（教师工号存在? 42001 / 实验室编号存在? 42002）
  → 同一 (course_code, semester) 出现多位教师? 42003 整组拒绝
  → schedule 逐行插入（同事务）
  → 课程归并：按 (course_code, semester_id) 分组
      ├ 已有 course → 校验 teacher_id 一致，更新 total_hours
      └ 无 → 新建 course（major 从 class_names 解析：取班级名去数字后缀）
  → schedule_time 解析 raw_schedule_text（正则：第X-Y节 / 周次 / 单双周）
  → @OperationLog(SCHEDULE_IMPORT, detail={成功N, 失败M, 失败行})
```

- 预览接口（`/import/preview`，POST）走同一解析器，**只校验不入库**，返回 `{totalCount, validCount, errorRows[]}`。
- 重复导入语义：同学期+同课表行（course_code+lab_code+class_names 相同）→ 覆盖更新（避免重复行）。
- 任何一行 42001/42002 → 该行记入 errorRows 继续；42003 → 该课程号整组拒绝。

### 7.3 实验编号生成

规则（拷问拍板）：`{课程号}-{学期编码}-{三位流水号}`，示例 `CS101-20261-001`。

- 学期编码 = `semester_name` 去掉"-"后两位年份段压缩：`2026-2027-1` → `20261`（世纪前两位去重）。
- 流水号 = `SELECT MAX(后缀) WHERE course_id=?` + 1，**同一事务内 SELECT ... FOR UPDATE** 防并发重号；唯一索引兜底。

### 7.4 从上学期复制（43001 兜底）

```
POST /experiments/copy-from-previous {courseId}
→ 找当前 course → 同 course_code 上一学期（end_date 最近且 < 本学期 start_date）
→ 无上学期课程或其 0 个实验 → 43001（前端置灰兜底）
→ 逐条复制（experiment_code 按 §7.3 重新生成），sort_order 保持
```

### 7.5 报表分层可见（aggregate_only，P0-8 拍板）

```java
boolean aggregateOnly = (currentUser.role == TEACHER);   // 教师强制 true，忽略入参
```

- SQL：明细行 `WHERE course.teacher_id = ?`（教师）；汇总行全校 COUNT/SUM 无过滤。
- 响应：`{summary, detail, detailScope: ALL|SELF}`，结构与 API 文档 §2.9 完全一致。
- 导出接口同参数语义；PDF 导出走 §7.8。

### 7.6 NLQ 转发链路（Spring 侧职责）

```
前端 → POST /nlq/query（JWT 鉴权）
  → 限流检查（429）
  → 组装内部请求：{question, userId, role} + Header X-Internal-Token
  → HTTP POST {nlq.base-url}/query，超时 15s，**不重试**
  → 成功：写 query_history（含 role、generated_sql、result_summary）
  → 44001/44002/44003 透传 NLQ 服务的错误码；连接失败→44003
```

- **数据权限注入发生在 NLQ 服务（Python），Spring 不改 SQL**；Spring 只负责身份传递与审计。
- `/nlq/schema`：仅 ADMIN + 生产可禁用（配置开关 `ltpms.nlq.schema-enabled`）。

### 7.7 通知定时任务

`@Scheduled(cron = "0 0 9 15 8,1 ?")`（8/15、1/15 09:00）：
- 查所有 ADMIN → 逐人插入 `SEMESTER_REMIND` 通知（ref_url=/semesters）。
- 幂等：同 type 同日已发则跳过（查当天已有记录）。

### 7.8 PDF 中文导出

- itext7 + `resources/fonts/SourceHanSansSC-Regular.otf`（思源黑体，OFL 开源，**随工程打包**）。
- `PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H)`；禁止依赖系统字体。

### 7.9 Excel 导入导出（POI）

- 统一 `ExcelUtil`：读（表头行校验→数据行 Map 列表）、写（模板下载 + 报表导出）。
- 上传校验：扩展名 .xlsx/.xls、大小 ≤10MB、行数 ≤2000，违规→400。

---

## 8. 安全设计落地边界（Spring 侧 vs NLQ 侧）

| 防护 | 实施方 | 说明 |
|------|--------|------|
| JWT 签发/校验 | Spring | 2h 有效期 |
| 角色拦截 | Spring | @RequireRole + RoleAspect |
| 账号锁定 | Spring | 5 次锁 30min |
| NLQ 四层防护之 L1~L2 | NLQ 服务 | Prompt 限定 + sqlparse 白名单 |
| **数据权限注入** | **NLQ 服务** | 白名单表+字段映射，失败拒绝（2026-09-07 拍板） |
| 只读账号 | NLQ 服务持有 | Spring 不持有只读凭据 |
| 服务间鉴权 | 双方 | `X-Internal-Token`（P0-1 修复项） |
| 限流 | Spring（入口侧） | 10 次/分/用户，429 |
| 审计 | Spring | operation_log + query_history.role |

---

## 9. 数据库初始化

- `database/init.sql`：建库（utf8mb4_unicode_ci）→ 建表 → 业务账号 `ltpms`（ALL PRIVILEGES）→ **只读账号 `nlq_readonly`（仅 SELECT）** → 种子 admin（密码 BCrypt 预哈希，`is_default_pwd=0`）。
- `database/dict_data.sql`：5 类字典种子值。
- 执行顺序文档化；初始化脚本**必须幂等**（CREATE IF NOT EXISTS / INSERT ... ON DUPLICATE KEY）。

---

## 10. 构建与运行手册

```bash
# 首次（自动下载 Maven distribution，走阿里镜像）
cd backend
./mvnw -Dmaven.repo.remote=https://maven.aliyun.com/repository/public clean package -DskipTests

# 本地运行（需先起 MySQL 并执行 init.sql）
./mvnw spring-boot:run          # 监听 8080

# 前端联调
cd ../frontend && npm run dev    # 5173，/api 代理 → 8080
```

**本机已知坑（2026-09-07 实测踩过）**：
1. 系统 `mvn`（Git Bash）shell 脚本路径转换 bug → **不用系统 mvn，一律 mvnw**。
2. MySQL 8.4 服务端需先启动；连接失败先查服务与 `DB_*` 环境变量。
3. `Public Key Retrieval is not allowed` → dev JDBC URL 已追加 `allowPublicKeyRetrieval=true`（application-dev.yml，2026-09-07 修复）。
4. **JWT_SECRET 必须是 Base64 且解码后 ≥64 字节（512 位）**——jjwt HS512 强制要求；376 位密钥直接 `WeakKeyException`。本机 Maven 本地仓库在 `D:\apache-maven-3.9.8-bin\MAVEN—local repository`（settings.xml 配置），不在默认 `~/.m2`。
5. **repackage 前必须停掉运行中的 jar 进程**——Windows 下 target/*.jar 被锁定导致 spring-boot-maven-plugin 失败。
6. dev 启动命令（完整环境变量模板，PowerShell/bash 通用改写）：
   ```
   DB_PORT=3307 DB_USER=ltpms DB_PASSWORD=ltpms \
   JWT_SECRET=<Base64,解码后≥64字节> NLQ_INTERNAL_TOKEN=<dev-internal-token> \
   java -jar target/ltpms-backend-0.0.1-SNAPSHOT.jar --server.port=8080
   ```

---

## 11. 测试策略（对齐技术设计 §9）

| 层 | 覆盖点 |
|----|--------|
| 单元 | 实验编号生成、学期状态计算、课表解析正则、报表 aggregate 分支 |
| 集成（Testcontainers-MySQL） | 登录锁定流程、导入事务回滚（42003 整组拒绝）、复制 43001 |
| 验收 | NLQ 评测集 ≥100 条（≥90% 准确率、≤10s）——**评测集由业务方共建，M5 前置** |

---

## 12. 实施里程碑（编码顺序）

| 里程碑 | 内容 | 验收 |
|--------|------|------|
| **M1 骨架+基建** | pom/yml/mvnw、通用层（§5 全部）、User 实体+auth 三接口 | `mvnw package` 通过；登录/锁定/改密可用 curl 验证 |
| M2 主数据 | 实验室/学期/字典/教师（含导入、重置密码、启停） | 前端对应页面全部可用 |
| M3 课表+课程+实验 | 导入归并（§7.2）、实验 CRUD/编号/复制 | 前端实验项目页全流程 |
| M4 报表+通知 | aggregate_only、导出 excel/pdf、定时任务、概览统计 | 报表页分层可见正确 |
| M5 NLQ 接入 | 转发链路、限流、历史；FastAPI 服务另建 | 评测集 ≥90% |

> 当前会话先交付 **M1**（骨架 + 基建 + 认证），后续里程碑按用户指令推进。
