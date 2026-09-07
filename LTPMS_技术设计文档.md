# 基于AI大模型的实验教学项目管理系统 — 技术设计文档

> 版本：V1.1 | 日期：2026-09-07 | 状态：已修订（按后端设计审查报告修复 P0/P1/P2 项，修订清单见文末附录）

---

## 1. 技术选型

### 1.1 总览

| 层 | 技术 | 版本 | 选型理由 |
|----|------|------|---------|
| 前端框架 | Vue 3 + TypeScript | 3.4+ | 响应式SPA，组合式API，类型安全 |
| UI组件库 | Element Plus | 2.x | 中后台标配，表格/表单/分页/导出开箱即用 |
| 前端构建 | Vite | 5.x | 开发热更新快，构建产物小 |
| 后端框架 | Spring Boot | 3.3.x（锁定） | Java生态成熟，团队技术栈匹配；与 mybatis-plus-spring-boot3-starter 3.5.7+ 兼容 |
| ORM | MyBatis-Plus | 3.5+ | 简化CRUD，内置分页/代码生成 |
| NL2SQL服务 | Python FastAPI | 0.100+ | 异步高性能，Python生态便于集成AI工具链 |
| 数据库 | MySQL | 8.4 | 关系型，已安装，UTF8MB4支持中文 |
| AI模型 | DeepSeek API | — | 成本低，中文NL2SQL能力强 |
| 容器化 | Docker Compose | 2.x | 一键编排前端+后端+AI服务+数据库 |
| 版本控制 | Git | — | 分支管理策略（见开发指南） |

### 1.2 备选方案对比

| 决策点 | 选定方案 | 备选 | 否决理由 |
|--------|---------|------|---------|
| 后端语言 | Java Spring Boot | Python FastAPI全栈 | 团队Java强，后端业务逻辑复杂度需要Java生态支撑 |
| 前端框架 | Vue 3 | React | 团队Vue经验更多，Element Plus生态成熟 |
| NL2SQL架构 | 独立Python微服务 | 集成在Java后端中 | Python调用AI工具链更自然，解耦后可独立扩展 |
| 数据库 | MySQL | PostgreSQL | MySQL团队更熟，教务数据场景足够 |
| AI模型 | DeepSeek API | 本地部署开源模型 | 无需GPU，开箱即用，中文能力好 |

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                    用户浏览器                              │
│              Vue 3 + Element Plus SPA                    │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTPS / REST API (JSON)
                       ▼
┌──────────────────────────────────────────────────────────┐
│              Spring Boot 后端 (Java)                      │
│                                                          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────────┐ │
│  │ 认证模块  │ │ 业务模块  │ │ 报表模块  │ │ NL查询代理  │ │
│  │ (JWT)    │ │ (CRUD)   │ │ (统计)   │ │ (转发请求)  │ │
│  └──────────┘ └──────────┘ └──────────┘ └──────┬──────┘ │
│                                                 │        │
└─────────────────────────────────────────────────┼────────┘
                                                  │ HTTP
                                                  ▼
┌──────────────────────────────────────────────────────────┐
│           NL2SQL 微服务 (Python FastAPI)                  │
│                                                          │
│  ┌────────────┐ ┌────────────┐ ┌──────────┐ ┌────────┐ │
│  │ Prompt引擎  │ │ SQL校验器   │ │ 权限注入  │ │Schema库│ │
│  │ (模板+组装) │ │ (sqlparse) │ │ (WHERE)  │ │(元数据)│ │
│  └──────┬─────┘ └────────────┘ └──────────┘ └────────┘ │
│         │ HTTPS                                           │
│         ▼                                                │
│  ┌─────────────────┐                                     │
│  │  DeepSeek API    │                                     │
│  │  (云端大模型)     │                                     │
│  └─────────────────┘                                     │
└──────────────────────────────────────────────────────────┘
                                                  │
                                                  ▼
┌──────────────────────────────────────────────────────────┐
│                    MySQL 8.4 数据库                       │
│                                                          │
│  业务库: ltpms_db                                         │
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐│
│  │用户表  │ │实验室表│ │学期表  │ │课表    │ │课程表  ││
│  └────────┘ └────────┘ └────────┘ └────────┘ └────────┘│
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐           │
│  │项目表  │ │字典表  │ │查询历史│ │课表明细│           │
│  └────────┘ └────────┘ └────────┘ └────────┘           │
│                                                          │
│  NL2SQL专用账号: 只读权限 (SELECT only)                    │
└──────────────────────────────────────────────────────────┘
```

### 2.2 分层职责

| 层 | 职责 | 技术实现 |
|----|------|---------|
| **表示层** | 页面渲染、用户交互、表单校验、数据可视化 | Vue 3 SFC + Element Plus 组件 |
| **网关层** | 路由转发、JWT认证、跨域处理、限流 | Spring Security + JWT Filter |
| **业务层** | 业务逻辑、数据校验、权限控制、事务管理 | Spring Boot Service 层 |
| **数据层** | 数据持久化、SQL映射、分页查询 | MyBatis-Plus Mapper |
| **AI服务层** | NL2SQL核心逻辑：Prompt组装、模型调用、SQL校验、权限注入 | FastAPI + sqlparse + httpx |
| **存储层** | 关系数据存储 | MySQL 8.4 |

### 2.3 服务间通信

```
浏览器 ←──HTTPS──→ Spring Boot (端口 8080)
                       │
                       └──HTTP──→ FastAPI NL2SQL服务 (端口 8001)
                                       │
                                       └──HTTPS──→ DeepSeek API (云端)
                       │
                       └──TCP──→ MySQL (端口 3306)
```

| 通信链路 | 协议 | 端口 | 数据格式 | 鉴权 |
|----------|------|------|---------|------|
| 前端 ↔ 后端 | HTTPS/HTTP | 8080 | JSON (REST) | JWT Bearer Token |
| 后端 ↔ NL2SQL服务 | HTTP（仅Docker内网） | 8001 | JSON (REST) | 内部服务令牌 `X-Internal-Token` |
| NL2SQL服务 ↔ DeepSeek | HTTPS | 443 | JSON | Bearer API Key |
| 后端 ↔ MySQL | TCP | 3306 | MySQL Protocol | 账号密码（业务账号 / NL2SQL只读账号分离） |

**服务间鉴权（P0 修复）**：NL2SQL 服务**不暴露到宿主机网络**（Docker Compose 中使用 `expose` 而非 `ports`），仅接受来自 Spring Boot 的内网请求。FastAPI 校验每个请求头中的 `X-Internal-Token`（双方通过环境变量 `NLQ_INTERNAL_TOKEN` 共享，格式为 ≥32 字节随机串）。**用户身份（user_id / role）一律由 Spring Boot 从 JWT 解析后注入转发请求，NL2SQL 服务不再信任请求体中由外部传入的身份字段**——直接访问 8001 端口伪造 ADMIN 身份的路径被完全封死。

---

## 3. 核心数据流

### 3.1 课表导入 → 自动生成课程

```
管理员上传Excel
    ↓
Spring Boot接收文件 → 解析Excel (Apache POI)
    ↓
逐行校验：
  ├── 教师工号 → 查user表是否存在
  ├── 教室编号 → 查laboratory表是否存在
  └── 字段格式 → 非空、类型匹配
    ↓
校验通过 → 批量插入schedule表（课表记录）
    ↓
按 课程号+学期 分组 → 生成course表记录
    ↓
解析"上课班级"字段 → 提取专业 → 填入course.major
    ↓
返回导入结果（成功N条，失败M条，失败原因列表）
```

**多班/合班分组规则（P0 修复，生成 course 时执行）**：

| 场景 | 处理规则 |
|------|---------|
| 同一课程号多条记录、teacher_id 相同 | 正常合并：student_count **累加**；major 去重后逗号拼接（如"计算机科学与技术,软件工程"） |
| 同一课程号多条记录、teacher_id **不同** | **导入校验失败，整组标红**，提示"课程 {course_code} 存在多位授课教师，请人工核实课表"，不静默合并 |
| 同一课程号多班、上课地点不同 | 仅取第一条记录的 lab_code 存入 schedule 各条；course 不存实验室字段，不受影响 |
| class_names 解析专业失败 | course.major 留空，教师后续手动补充 |

### 3.2 实验项目复制

```
教师点击"从上学期复制"
    ↓
后端查询：当前课程（课程号+当前学期ID）→ 找到上学期同课程号的实验项目
    ↓
如无数据 → 前端按钮**置灰并提示**"上学期无实验项目"（前端预判）；
             若绕过前端直接调接口，后端返回业务错误（错误码 43001，见 API 文档错误码表）
    ↓
批量复制实验项目 → 重新生成实验编号（课程号-当前学期编码-流水号）
    ↓
插入experiment表 → 返回成功
```

### 3.3 NL2SQL查询全流程

```
用户输入问题
    ↓
前端 POST /api/nlq/query {question, user_id, role}
    ↓
Spring Boot 验证JWT → 转发到 FastAPI
    ↓
FastAPI 处理：
  1. 加载Schema知识库（启动时缓存，定期刷新）
  2. 组装Prompt：
     [系统角色] 你是一个SQL生成助手，只能生成SELECT语句...
     [数据库Schema] CREATE TABLE ... / 字段注释 / 字典值映射
     [数据权限] 教师角色时附加权限约束说明
     [用户问题] {question}
     [输出要求] 只输出SQL，不要解释
  3. 调用 DeepSeek API (POST https://api.deepseek.com/chat/completions)
  4. 提取返回的SQL文本
  5. SQL安全校验：
     - sqlparse解析AST
     - 校验语句类型 = SELECT
     - 检查无危险函数（LOAD_FILE, INTO OUTFILE等）
     - 检查无注释（--, /* */）
     - 校验不通过 → 返回友好错误提示
  6. 数据权限注入（详见 4.3 节）：
     - admin → 不修改SQL
     - teacher → 白名单表+字段映射强制注入（experiment 经 course.teacher_id 兜底）
     - 注入失败/找不到 teacher 关联 → 拒绝执行
  7. 行数保护（P1 修复）：SQL 无 LIMIT 时强制追加 LIMIT 500；已有 LIMIT 且 >500 时改写为 500
     超限返回结果时附带提示"结果已截断至前500行，请缩小查询范围"
  8. 执行SQL（使用 NL2SQL 专用只读数据库账号，见 4.2 第4层）
  9. 格式化结果 → 生成相关问题推荐
    ↓
返回 {success, sql, columns, rows, suggestions}
    ↓
前端渲染为表格 + 显示推荐问题
```

### 3.4 学期状态与"当前学期"判定（P1 修复）

**状态计算机制**：semester.status **不依赖定时任务刷新**（避免服务器假期关机错过窗口），采用**查询时动态计算**：

```
每次查询学期列表时：
  当前日期 < start_date   → UPCOMING
  start_date ≤ 当前日期 ≤ end_date → ACTIVE
  当前日期 > end_date     → ENDED
计算结果回写数据库（作为缓存，供报表/导出离线查询使用）
```

**"当前学期"判定规则**（教师登录后默认展示的学期）：

| 当前日期 | 判定结果 |
|---------|---------|
| 落在某一学期的 [start_date, end_date] 内 | 该学期 |
| 落在两学期之间（寒暑假空档） | **最近结束的学期**（ENDED 中 end_date 最大者） |
| 所有学期均未开始 | 最近创建的 UPCOMING 学期 |

页面顶部始终提供学期选择器，教师可手动切换；**当前学期可编辑，历史学期只读**。

---

## 4. 安全设计

### 4.1 认证与授权

| 机制 | 实现 |
|------|------|
| 认证方式 | JWT (JSON Web Token) |
| Token有效期 | 2小时，过期重新登录（V1 **不做** refresh token，避免半吊子实现；单校场景可接受） |
| 密码存储 | BCrypt 哈希加盐 |
| 账号锁定 | 登录失败 `failed_attempts+1`，达 5 次 → `locked_until = NOW()+30分钟`；期间登录直接拒绝（提示剩余锁定时间）；登录成功清零。字段存 user 表（见 API 文档 §1.2.1） |
| 接口保护 | Spring Security Filter 链，未登录返回401 |
| 权限控制 | 自定义注解 `@RequireRole(ADMIN)` 做方法级拦截 |
| 默认密码 | 教师导入默认密码为 `Ltpms@{工号}`（8位以上含字母数字，满足复杂度策略）；首次登录强制改密（is_default_pwd=1） |

### 4.2 NL2SQL四层防护（P0 修复：新增第0层服务间鉴权）

| 层 | 位置 | 措施 | 失败处理 |
|----|------|------|---------|
| 第0层 | 网络与服务边界 | NL2SQL 服务不映射宿主机端口（仅 Docker 内网）+ `X-Internal-Token` 校验；用户身份由 Spring Boot 从 JWT 解析后注入 | 直接返回 403，不进入查询流程 |
| 第1层 | Prompt | 系统提示词限定只能SELECT + few-shot示例 | — |
| 第2层 | Python代码 | sqlparse解析 + 白名单校验 + 禁止危险函数/注释 | 返回"该问题无法安全执行" |
| 第3层 | 权限注入 | 白名单表+字段映射强制注入（见4.3） | 注入失败 → 拒绝执行 |
| 第4层 | MySQL | NL2SQL专用账号仅GRANT SELECT（`ltpms_readonly`，初始化脚本见开发指南§3.1） | 数据库拒绝执行 |

### 4.3 数据权限注入逻辑（P0 修复：重写为白名单表+字段映射，2026-09-07 拍板方案）

**配置载体**：`permission_rules`（YAML 或数据库表，启动时加载到内存，支持热更新）。

**映射规则**：

| SQL 中出现的表 | 注入字段 | 找不到字段时的兜底 |
|---------------|---------|------------------|
| `experiment` | experiment.teacher_id（当前表结构中不存在） | `JOIN course c ON e.course_id = c.id`，注入 `c.teacher_id = ?` |
| `course` | course.teacher_id | — |
| `schedule` | schedule.teacher_id | — |
| `laboratory` / `semester` / `dictionary` | **不注入**（对教师公开） | — |

**注入算法**：

```python
def inject_permission(sql: str, user: User) -> str:
    if user.role == 'ADMIN':
        return sql  # 管理员不修改

    tables = extract_tables(sql)  # sqlparse 提取全部表名（含 JOIN / 子查询）

    conditions = []
    for table in tables:
        rule = permission_rules.get(table)
        if rule is None:
            continue  # 公开表，不注入
        col = resolve_column(sql, table, rule.column)
        if col is None:
            col = rule.fallback(sql, table)  # 经 JOIN 关联表解析 teacher 字段
        if col:
            conditions.append(f"{col} = :current_user_id")

    if not conditions:
        # 涉及受控表却无法建立任何 teacher 关联 → 拒绝执行（不放过零权限查询）
        raise PermissionRejectError("无法为该查询建立数据权限约束，已拒绝执行")

    return add_where_conditions(sql, conditions)  # 参数化绑定，杜绝拼接注入
```

**关键规则**：
1. JOIN 路径允许引用**任一关联表**的 teacher 字段（如 `e JOIN c` 时用 `c.teacher_id` 兜底）
2. 子查询（`WHERE id IN (SELECT ...)`）同样提取表名并注入
3. 注入的 teacher_id 一律使用**参数化绑定**，不字符串拼接
4. 汇总类查询（COUNT/SUM 等无明细行的）允许教师执行，返回全校汇总数字（对应"分层可见"决策，见报表设计）

### 4.4 其他安全措施

| 措施 | 说明 |
|------|------|
| CORS | 仅允许前端域名跨域访问；dev profile 放行 `http://localhost:5173`，prod 走 Nginx 反代同源（免跨域），按 Spring profile 区分配置 |
| SQL注入 | MyBatis-Plus参数化查询，天然防注入；NL2SQL 注入条件同样参数化绑定 |
| XSS | 前端输入输出转义，Content-Security-Policy |
| 限流 | NL2SQL接口限流：每用户每分钟10次；**单机内存实现**（Guava RateLimiter / Bucket4j，ConcurrentHashMap<userId, RateLimiter>），单校 ≤100 并发无需 Redis |
| 查询日志 | 所有NL2SQL查询记录完整日志（问题、SQL、执行角色、用户、结果行数，见 query_history 表） |
| 操作审计 | **新增 operation_log 表**：课表导入、实验项目增删改、教师账号导入、密码重置等写操作，通过 AOP 切面自动记录（user_id, action, target, detail, ip, created_at），见 API 文档 §1.2.11 |
| 敏感数据 | 密码BCrypt加密，日志不记录密码；JWT_SECRET 通过 `openssl rand -base64 48` 生成，禁止硬编码入库/入git |

---

## 5. 性能策略

### 5.1 数据量评估

| 数据 | 预估规模 | 说明 |
|------|---------|------|
| 实验室 | ~50条 | 单校规模 |
| 学期 | ~20条 | 10年×2学期 |
| 课表/学期 | ~200条 | 每学期约200门实验课 |
| 实验项目/课程 | ~10条 | 每门课约10个实验 |
| 教师 | ~100人 | 单校规模 |
| 总数据量 | <10万行 | MySQL轻松应对 |

### 5.2 性能优化策略

| 场景 | 策略 |
|------|------|
| 列表查询 | MyBatis-Plus分页插件，默认每页20条（page/size 入参映射为 current/size） |
| 报表统计 | 预计算+索引，学年字段建索引 |
| NL2SQL响应 | Schema知识库启动时缓存到内存；**字典/表结构变更事件主动刷新 + TTL 5分钟兜底**（管理员改字典选项后最多5分钟生效） |
| NL2SQL外部调用 | httpx **timeout=15s，不自动重试**（10s总预算不允许重试；超时走降级提示）；连接池 max_connections=20 |
| NL2SQL行数保护 | 无 LIMIT 强制追加 LIMIT 500，超限截断并提示 |
| Excel导入 | 批量插入（batch insert），100条/批 |
| 前端加载 | Vite代码分割，路由懒加载，Element Plus按需引入 |

### 5.3 NL2SQL响应时间预算

| 阶段 | 预估耗时 |
|------|---------|
| Prompt组装 | <50ms |
| DeepSeek API调用 | 2-8秒（取决于问题复杂度和API负载）；**timeout=15s 硬上限，超时即降级提示，不重试** |
| SQL校验 | <100ms |
| 数据库执行 | <500ms（数据量小，且强制 LIMIT 500） |
| 结果格式化 | <50ms |
| **总计** | **<10秒（正常路径），最坏 15.5秒触发降级** |

---

## 6. 依赖清单

### 6.1 Java后端依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| spring-boot-starter-web | 3.x | Web框架 |
| spring-boot-starter-security | 3.x | 安全框架 |
| mybatis-plus-spring-boot3-starter | 3.5+ | ORM |
| mysql-connector-j | 8.x | MySQL驱动 |
| jjwt | 0.12+ | JWT生成/解析 |
| apache-poi-ooxml | 5.x | Excel读写 |
| itext7-core | 8.x | PDF生成；**中文字体**：需引入 Noto Sans SC / 思源黑体字体文件并注册 `FontProgramFactory`，否则中文空白乱码（P1 修复） |
| lombok | — | 简化代码 |
| hutool | 5.x | 工具库（JSON、加密等） |
| guava | 33.x | 单机内存限流（RateLimiter） |
| spring-boot-starter-test + mockito | — | 单元测试（JUnit 5） |

### 6.2 Python NL2SQL服务依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| fastapi | 0.100+ | Web框架 |
| uvicorn | 0.25+ | ASGI服务器 |
| httpx | 0.25+ | 异步HTTP客户端（调DeepSeek API） |
| sqlparse | 0.5+ | SQL解析与安全校验 |
| pymysql | 1.1+ | MySQL连接（执行查询） |
| pydantic | 2.x | 数据校验 |

### 6.3 前端依赖

| 依赖 | 版本 | 用途 |
|------|------|------|
| vue | 3.4+ | 前端框架 |
| element-plus | 2.x | UI组件库 |
| vue-router | 4.x | 路由管理 |
| pinia | 2.x | 状态管理 |
| axios | 1.x | HTTP客户端 |
| echarts | 5.x | 图表（报表可视化，如暂不实现图表页则移除该依赖，见组件规范） |

> 注：前端 `xlsx` 依赖已移除（P2 修复）——Excel/PDF 导出统一由后端接口 `/export` 完成（API 文档 §2.9），前端只做文件下载，避免前后端双重导出逻辑不一致。

### 6.4 外部服务

| 服务 | 用途 | SLA |
|------|------|-----|
| DeepSeek API | NL2SQL大模型推理 | 依赖DeepSeek官方SLA，需准备降级方案 |

---

## 7. Docker Compose 部署架构

```yaml
# 服务编排示意
services:
  mysql:          # MySQL 8.4 - 数据库
    ports: 3306
    healthcheck:  # P2 修复：与开发指南 Q5 对齐
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]

  ltpms-backend:  # Spring Boot - 后端（唯一对外入口）
    ports: 8080
    depends_on:
      mysql:
        condition: service_healthy

  ltpms-nlq:      # FastAPI - NL2SQL服务
    expose: 8001   # P0 修复：expose 不映射宿主机端口，仅 Docker 内网可达
    depends_on:
      mysql:
        condition: service_healthy

  ltpms-frontend: # Nginx - 前端静态资源 + 反向代理
    ports: 80
    proxy_pass:
      /api/*     → ltpms-backend:8080   # 前端所有请求经 Nginx → 后端 → NL2SQL
      # 注意：不直接反代 /api/nlq/* 到 ltpms-nlq，统一由后端转发（保证 JWT 鉴权 + 身份注入）
```

**部署要点**：8001 端口**永不对外暴露**；`NLQ_INTERNAL_TOKEN` 通过 `.env` 注入 backend 与 nlq 两个服务。

---

## 8. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| DeepSeek API不可用 | NL2SQL功能瘫痪 | ① 15s超时+降级提示（不重试） ② 常见查询结果本地缓存 ③ 明确提示用户稍后重试 |
| NL2SQL准确率不够 | 用户体验差 | ① 持续优化Prompt ② 展示生成的SQL供用户校验 ③ 提供"问题修正建议" |
| Excel格式不统一 | 导入失败 | ① 提供标准模板下载 ② 模糊匹配+容错 ③ 错误行标红提示 |
| 课表重复导入 | 数据不一致 | **覆盖语义已明确（P1 修复）**：确认覆盖 = 删除该学期全部 schedule + schedule_time，**已生成的 course 不动、已录入的 experiment 不动**；重新导入后按"存在即跳过"原则处理 course（course_code+semester_id 唯一约束兜底）。预览与确认之间服务端二次校验该学期数据版本（导入时间戳），不一致则要求重新预览 |
| 合班教师冲突 | 导入数据错误 | 同课程号多教师 → 整组标红拒绝导入（见 3.1 分组规则） |
| 服务器假期关机错过学期状态刷新 | 学期状态过期 | 查询时动态计算，不依赖定时任务（见 3.4） |

---

## 9. 测试策略（P2 修复：新增）

### 9.1 单元测试

| 范围 | 工具 | 覆盖率目标 |
|------|------|-----------|
| Spring Boot Service 层 | JUnit 5 + Mockito | ≥70% 行覆盖 |
| 权限注入 / SQL校验 / 编号生成等纯逻辑 | JUnit 5 | ≥90% 分支覆盖 |
| FastAPI 各 service | pytest + pytest-asyncio | ≥70% |

### 9.2 集成测试

- Spring Boot：`@SpringBootTest` + Testcontainers（MySQL 容器），覆盖课表导入→课程生成、复制、报表统计主链路
- NL2SQL：pytest 起本地 FastAPI + mock DeepSeek 返回，覆盖校验/注入/行数保护

### 9.3 NL2SQL 准确率验收（PRD ≥90% 指标的证明方式）

- **建评测集**：≥100 条真实场景问题（查询类40/统计类30/筛选类20/权限类10），由需求方与开发者共同确认标准答案 SQL
- **评测方式**：对评测集逐条执行，比对结果集（非 SQL 文本比对）；准确率 = 结果正确条数 / 总条数
- **回归**：每次修改 Prompt 后重跑全量评测集，防退化
- 性能验收：评测集全量压测，P95 ≤10s

---

## 附录：V1.1 修订记录（2026-09-07）

| 编号 | 修复项 | 对应审查报告 |
|------|--------|-------------|
| 1 | 服务间鉴权 `X-Internal-Token` + 8001 不暴露宿主机 | P0-1 |
| 2 | 权限注入重写为白名单表+字段映射（experiment 经 course 兜底，失败拒绝） | P0-2 |
| 3 | 课表→课程合班分组规则（教师冲突标红、人数累加、专业拼接） | P0-7 |
| 4 | 账号锁定方案（failed_attempts/locked_until） | P0-5 |
| 5 | NL2SQL 三层防护 → 四层（新增服务间鉴权层） | P0-1 |
| 6 | 学期状态查询时动态计算 + 寒暑假判定规则 | P1-8 |
| 7 | "从上学期复制"无数据：前端置灰 + 后端错误码兜底 | 文档矛盾 |
| 8 | NL2SQL 行数保护 LIMIT 500 | P1-3 |
| 9 | DeepSeek timeout=15s 不重试 | P1-4 |
| 10 | JWT 砍掉 refresh token | P1-7 |
| 11 | Schema 缓存事件刷新 + TTL 5min | P1-10 |
| 12 | 限流单机内存实现（Guava） | P1-11 |
| 13 | itext7 中文字体方案 | P1-2 |
| 14 | Spring Boot 锁定 3.3.x | P2-5 |
| 15 | Docker Compose healthcheck + expose 8001 | P2-10 |
| 16 | 课表重复导入覆盖语义 | P1-9 |
| 17 | 前端 xlsx 依赖移除 | P2-1 |
| 18 | 新增测试策略章节 | P2-11 |
| 19 | 默认密码改为 `Ltpms@{工号}` | P1-1 |

---

*技术设计文档确认完毕后，进入阶段3：API/数据模型文档。*

**这份技术设计是否OK？有要调整的地方吗？确认后进入阶段3。**
