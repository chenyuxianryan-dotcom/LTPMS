# 基于AI大模型的实验教学项目管理系统 — 开发指南

> 版本：V1.1 | 日期：2026-09-07 | 状态：已修订（按后端设计审查报告修复 P0/P1/P2 项）

---

## 1. 开发环境要求

### 1.1 必装软件

| 软件 | 版本 | 用途 |
|------|------|------|
| JDK | 17（LTS，锁定） | Spring Boot 3.3.x 要求 |
| Maven | 3.8+ | Java 构建工具 |
| Spring Boot | 3.3.x（锁定） | 后端框架，与 mybatis-plus-spring-boot3-starter 3.5.7+ 兼容 |
| Node.js | 18+ (LTS) | 前端构建 |
| pnpm | 8+ | 前端包管理（推荐） |
| Python | 3.10+ | NL2SQL 微服务 |
| pip / uv | — | Python 包管理 |
| MySQL | 8.0+ | 数据库（已安装 8.4.6） |
| Git | 2.x | 版本控制 |
| Docker Desktop | 4.x | 容器化部署（可选） |
| IDE | IntelliJ IDEA / VS Code | 开发工具 |

### 1.2 环境变量

```bash
# 数据库（业务账号：读写）
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=ltpms_db
DB_USER=ltpms
DB_PASSWORD=<数据库密码>

# 数据库（NL2SQL 专用只读账号，P0 修复：第三/四层防护落地）
NLQ_DB_USER=ltpms_readonly
NLQ_DB_PASSWORD=<只读账号密码>

# DeepSeek API
DEEPSEEK_API_KEY=<你的API Key>
DEEPSEEK_API_URL=https://api.deepseek.com/chat/completions
DEEPSEEK_MODEL=deepseek-chat
DEEPSEEK_TIMEOUT=15  # 秒，硬上限，超时降级不重试

# JWT（生成方式：openssl rand -base64 48，禁止硬编码入库/入git）
JWT_SECRET=<openssl rand -base64 48 生成>
JWT_EXPIRATION=7200000  # 2小时(毫秒)，过期重新登录（V1无refresh token）

# NL2SQL 服务
NLQ_SERVICE_URL=http://localhost:8001

# 服务间鉴权令牌（P0 修复：backend 与 nlq-service 共享，≥32字节随机串）
# 生成方式：openssl rand -hex 32
NLQ_INTERNAL_TOKEN=<openssl rand -hex 32 生成>
```

---

## 2. 项目目录结构

### 2.1 整体结构

```
LTPMS/
├── frontend/                  # 前端 Vue 3 项目
├── backend/                   # 后端 Spring Boot 项目
├── nlq-service/               # NL2SQL Python 微服务
├── database/                  # 数据库脚本
│   ├── init.sql               # 建库建表+初始数据
│   └── dict_data.sql          # 字典初始数据
├── docker/                    # Docker 配置
│   ├── docker-compose.yml
│   ├── backend/Dockerfile
│   ├── frontend/Dockerfile
│   └── nlq-service/Dockerfile
├── docs/                      # 设计文档
├── .gitignore
└── README.md
```

### 2.2 前端目录

```
frontend/
├── public/
├── src/
│   ├── api/                   # 接口封装
│   │   ├── auth.ts
│   │   ├── laboratory.ts
│   │   ├── semester.ts
│   │   ├── schedule.ts
│   │   ├── teacher.ts
│   │   ├── course.ts
│   │   ├── experiment.ts
│   │   ├── report.ts
│   │   ├── nlq.ts
│   │   └── dict.ts
│   ├── assets/                # 静态资源
│   ├── components/            # 通用组件
│   │   ├── DataTable.vue
│   │   ├── ImportUploader.vue
│   │   ├── ExportButton.vue
│   │   ├── SemesterSelector.vue
│   │   ├── DictSelect.vue
│   │   ├── ConfirmDelete.vue
│   │   ├── EmptyState.vue
│   │   ├── PageHeader.vue
│   │   └── PermissionButton.vue
│   ├── composables/           # 组合式函数
│   │   ├── useAuth.ts
│   │   ├── usePagination.ts
│   │   ├── useImport.ts
│   │   ├── useExport.ts
│   │   └── useDict.ts
│   ├── layouts/               # 布局组件
│   │   ├── DefaultLayout.vue
│   │   └── BlankLayout.vue
│   ├── router/                # 路由配置
│   │   └── index.ts
│   ├── stores/                # Pinia 状态管理
│   │   ├── user.ts
│   │   ├── app.ts
│   │   └── dict.ts
│   ├── styles/                # 全局样式
│   │   ├── variables.scss
│   │   └── global.scss
│   ├── utils/                 # 工具函数
│   │   ├── request.ts         # Axios 封装
│   │   ├── auth.ts            # Token 管理
│   │   └── format.ts          # 格式化工具
│   ├── views/                 # 页面
│   │   ├── Login.vue
│   │   ├── dashboard/
│   │   ├── laboratory/
│   │   ├── semester/
│   │   ├── schedule/
│   │   ├── teacher/
│   │   ├── course/
│   │   ├── experiment/
│   │   ├── report/
│   │   ├── nlq/
│   │   └── profile/
│   ├── App.vue
│   └── main.ts
├── .env.development
├── .env.production
├── index.html
├── vite.config.ts
├── tsconfig.json
└── package.json
```

### 2.3 后端目录

```
backend/
├── src/main/java/com/ltpms/
│   ├── config/                # 配置类
│   │   ├── SecurityConfig.java
│   │   ├── JwtConfig.java
│   │   ├── MybatisPlusConfig.java
│   │   ├── CorsConfig.java    # dev放行5173 / prod同源，按profile区分
│   │   ├── SchedulingConfig.java   # 定时任务开关（学期提醒 8/15、1/15）
│   │   └── WebMvcConfig.java
│   ├── controller/            # 控制器
│   │   ├── AuthController.java
│   │   ├── LaboratoryController.java
│   │   ├── SemesterController.java
│   │   ├── ScheduleController.java
│   │   ├── TeacherController.java
│   │   ├── CourseController.java
│   │   ├── ExperimentController.java
│   │   ├── ReportController.java
│   │   ├── NlqController.java
│   │   ├── DictController.java
│   │   ├── NotificationController.java   # V1.1 新增：站内通知
│   │   └── DashboardController.java
│   ├── entity/                # 实体类
│   │   ├── User.java          # 含 failed_attempts / locked_until（账号锁定）
│   │   ├── Laboratory.java
│   │   ├── Semester.java
│   │   ├── Schedule.java
│   │   ├── ScheduleTime.java
│   │   ├── Course.java
│   │   ├── Experiment.java
│   │   ├── Dictionary.java
│   │   ├── Notification.java           # V1.1 新增
│   │   ├── OperationLog.java           # V1.1 新增：操作审计
│   │   └── QueryHistory.java
│   ├── mapper/                # MyBatis Mapper
│   │   ├── UserMapper.java
│   │   ├── LaboratoryMapper.java
│   │   ├── SemesterMapper.java
│   │   ├── ScheduleMapper.java
│   │   ├── CourseMapper.java
│   │   ├── ExperimentMapper.java
│   │   ├── DictionaryMapper.java
│   │   └── QueryHistoryMapper.java
│   ├── service/               # 业务逻辑
│   │   ├── AuthService.java   # 含账号锁定逻辑（5次/30分钟）
│   │   ├── LaboratoryService.java
│   │   ├── SemesterService.java    # 状态查询时动态计算
│   │   ├── ScheduleService.java
│   │   ├── TeacherService.java
│   │   ├── CourseService.java      # 含课表→课程合班分组规则
│   │   ├── ExperimentService.java
│   │   ├── ReportService.java      # 含 aggregate_only 分层可见
│   │   ├── NlqService.java         # 转发时携带 X-Internal-Token
│   │   ├── DictService.java
│   │   ├── NotificationService.java    # V1.1 新增
│   │   └── DashboardService.java
│   ├── dto/                   # 数据传输对象
│   │   ├── LoginRequest.java
│   │   ├── LoginResponse.java
│   │   ├── NlqRequest.java
│   │   ├── NlqResponse.java
│   │   └── ...
│   ├── common/                # 公共类
│   │   ├── Result.java        # 统一响应封装
│   │   ├── PageResult.java    # 分页响应
│   │   ├── ErrorCode.java     # 错误码枚举（41001~44003，定义见API文档§2.1）
│   │   ├── BusinessException.java
│   │   └── GlobalExceptionHandler.java  # V1.1 新增：全局异常处理器（P2 修复）
│   ├── filter/                # 过滤器
│   │   └── JwtAuthFilter.java
│   ├── annotation/            # 自定义注解
│   │   ├── RequireRole.java
│   │   └── OperationLog.java       # V1.1 新增：操作审计注解
│   ├── aspect/                # 切面
│   │   ├── RoleAspect.java
│   │   └── OperationLogAspect.java # V1.1 新增：审计日志切面
│   └── util/                  # 工具类
│       ├── JwtUtil.java
│       ├── ExcelUtil.java
│       ├── PdfUtil.java       # V1.1 明确：注册中文字体（Noto Sans SC），防乱码
│       └── CodeGenerator.java # 实验编号生成器
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── mapper/                # XML映射文件（复杂SQL）
└── pom.xml
```

### 2.4 NL2SQL服务目录

```
nlq-service/
├── app/
│   ├── __init__.py
│   ├── main.py                # FastAPI 入口
│   ├── config.py              # 配置（API Key、数据库连接）
│   ├── routers/
│   │   └── nlq.py             # NLQ 路由
│   ├── services/
│   │   ├── prompt_builder.py  # Prompt 组装
│   │   ├── deepseek_client.py # DeepSeek API 调用（timeout=15s，不重试）
│   │   ├── sql_validator.py   # SQL 安全校验
│   │   ├── permission.py      # 数据权限注入（白名单表+字段映射，见技术设计§4.3）
│   │   ├── schema_cache.py    # Schema 知识库缓存（事件刷新+TTL 5min）
│   │   └── row_limiter.py     # V1.1 新增：行数保护（强制 LIMIT 500）
│   ├── models/
│   │   └── schemas.py         # Pydantic 数据模型
│   ├── middleware/
│   │   └── internal_auth.py   # V1.1 新增：X-Internal-Token 校验中间件
│   └── utils/
│       ├── db.py              # 数据库连接（使用 ltpms_readonly 只读账号）
│       └── logger.py          # 日志工具
├── prompts/                   # Prompt 模板文件
│   ├── system_prompt.txt      # 系统提示词
│   └── few_shot_examples.json # Few-shot 示例
├── config/
│   └── permission_rules.yaml  # V1.1 新增：数据权限白名单映射配置
├── requirements.txt
├── Dockerfile
└── .env
```

---

## 3. 快速启动

### 3.1 数据库初始化

```bash
# 1. 登录 MySQL
mysql -u root -p

# 2. 创建数据库（字符集锁定 utf8mb4_unicode_ci，V1.1 修复）
CREATE DATABASE ltpms_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# 3. 创建业务账号（读写，供 Spring Boot 使用）
CREATE USER 'ltpms'@'localhost' IDENTIFIED BY 'your_password';
GRANT ALL PRIVILEGES ON ltpms_db.* TO 'ltpms'@'localhost';

# 4. 创建 NL2SQL 专用只读账号（P0 修复：第四层防护落地，供 FastAPI 使用）
CREATE USER 'ltpms_readonly'@'localhost' IDENTIFIED BY 'readonly_password';
GRANT SELECT ON ltpms_db.* TO 'ltpms_readonly'@'localhost';

FLUSH PRIVILEGES;

# 5. 导入初始化脚本
mysql -u ltpms -p ltpms_db < database/init.sql
mysql -u ltpms -p ltpms_db < database/dict_data.sql
```

初始管理员账号：`admin` / `admin123`（首次登录强制改密码）

> ⚠️ **安全提醒**：nlq-service 的 `.env` 中数据库连接必须使用 `ltpms_readonly`，**禁止**使用业务账号 `ltpms`——否则四层防护的第四层（数据库只读兜底）失效。

### 3.2 启动后端

```bash
cd backend

# 修改数据库连接配置
vim src/main/resources/application-dev.yml

# 启动
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# 后端运行在 http://localhost:8080
```

### 3.3 启动 NL2SQL 服务

```bash
cd nlq-service

# 创建虚拟环境
python -m venv .venv
source .venv/bin/activate  # Windows: .venv\Scripts\activate

# 安装依赖
pip install -r requirements.txt

# 配置环境变量
cp .env.example .env
# 编辑 .env 填入：
#   DEEPSEEK_API_KEY
#   NLQ_DB_USER=ltpms_readonly + NLQ_DB_PASSWORD（只读账号！）
#   NLQ_INTERNAL_TOKEN（与后端共享的内部令牌，openssl rand -hex 32）

# 启动
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
# NL2SQL服务运行在 http://localhost:8001
# ⚠️ 开发环境本机可访问；生产（Docker）中 8001 只用 expose 不映射宿主机
```

> 开发期本机直连 8001 时，内部令牌校验中间件同样生效——用 curl 调试需带 `-H "X-Internal-Token: <token>"`。

### 3.4 启动前端

```bash
cd frontend

# 安装依赖
pnpm install

# 启动开发服务器
pnpm dev
# 前端运行在 http://localhost:5173
```

### 3.5 Docker Compose 一键启动（部署时）

```bash
# 在项目根目录
docker compose up -d --build

# 服务映射：
# http://localhost:80   → 前端（Nginx）
# http://localhost:8080 → 后端
# http://localhost:8001 → NL2SQL服务
# localhost:3306        → MySQL
```

---

## 4. 开发流程

### 4.1 Git 分支策略

```
main                    # 生产分支，只接受 merge
├── develop             # 开发主线
│   ├── feature/xxx     # 功能分支（如 feature/nlq-module）
│   ├── fix/xxx         # 修复分支
│   └── refactor/xxx    # 重构分支
```

- 功能开发从 `develop` 切出 feature 分支
- 完成后通过 PR 合并回 `develop`
- `develop` 稳定后合并到 `main`

### 4.2 新增一个功能模块的步骤（以后端为例）

```
1. 在 entity/ 下新建实体类（对应数据库表）
2. 在 mapper/ 下新建 Mapper 接口（继承 BaseMapper）
3. 在 dto/ 下新建请求/响应 DTO
4. 在 service/ 下新建 Service 类，写业务逻辑
5. 在 controller/ 下新建 Controller，定义 REST 接口
6. 编写单元测试
7. 前端对接：在 api/ 下新建接口文件，在 views/ 下新建页面
```

### 4.3 新增一个字典类型

```
1. 在 database/dict_data.sql 中插入字典数据
2. 前端使用 <DictSelect dictType="xxx" v-model="form.xxx" /> 即可自动加载
```

---

## 5. 编码规范

### 5.1 Java 后端

| 规范 | 说明 |
|------|------|
| 命名 | 类名 PascalCase，方法/变量 camelCase，常量 UPPER_SNAKE |
| 分层 | Controller 不写业务逻辑，只做参数校验和调用 |
| 异常 | 统一抛出 BusinessException，**由 GlobalExceptionHandler 统一捕获** |
| 响应 | 所有接口返回 Result<T> 包装 |
| 日志 | 使用 Slf4j，Service 层关键步骤打 INFO，异常打 ERROR；MDC 携带 userId/requestId 便于排查 |
| 注释 | 公共方法必须写 Javadoc |
| 测试 | Service 层 ≥70% 行覆盖（JUnit 5 + Mockito）；权限注入/SQL 校验/编号生成等纯逻辑 ≥90% 分支覆盖 |

### 5.2 Vue 前端

| 规范 | 说明 |
|------|------|
| 组件名 | PascalCase（如 `DataTable.vue`） |
| 组合式API | 统一使用 `<script setup lang="ts">` |
| 样式 | `<style scoped>` 避免全局污染 |
| 接口 | 统一在 `api/` 目录封装，不在组件中直接调 axios |
| 类型 | 关键数据结构必须定义 TypeScript 接口 |

### 5.3 Python NL2SQL 服务

| 规范 | 说明 |
|------|------|
| 代码风格 | PEP 8，使用 type hints |
| 异步 | 数据库操作和 API 调用使用 async/await |
| 配置 | 敏感信息通过 .env 文件注入，不硬编码 |
| 日志 | 使用 Python logging 模块，结构化日志 |

---

## 6. 常见问题与解决方案

### Q1：启动后端报 `Access denied for user 'ltpms'`

**原因**：数据库用户名密码不对，或用户未授权。

**解决**：
```sql
-- 确认用户存在
SELECT user, host FROM mysql.user WHERE user='ltpms';
-- 重新授权
GRANT ALL PRIVILEGES ON ltpms_db.* TO 'ltpms'@'localhost';
FLUSH PRIVILEGES;
```

### Q2：NL2SQL 查询超时或返回空

**原因**：DeepSeek API 网络不通，或 API Key 无效。

**解决**：
```bash
# 测试API连通性
curl -X POST https://api.deepseek.com/chat/completions \
  -H "Authorization: Bearer YOUR_KEY" \
  -H "Content-Type: application/json" \
  -d '{"model":"deepseek-chat","messages":[{"role":"user","content":"hello"}]}'
```
- 检查 API Key 是否正确
- 检查网络是否需要代理
- 确认账户余额是否充足

### Q3：前端请求后端 403 Forbidden

**原因**：CORS 配置未放行前端域名，或 JWT Token 过期。

**解决**：
- 检查 `CorsConfig.java` 中 `allowedOrigins` 是否包含 `http://localhost:5173`
- 前端检查 Token 是否过期（浏览器 DevTools → Application → Local Storage）

### Q4：Excel 导入中文乱码

**原因**：文件编码或 Apache POI 版本问题。

**解决**：
- 确保 Excel 文件为 `.xlsx` 格式（Office 2007+）
- 后端使用 `XSSFWorkbook` 而非 `HSSFWorkbook`

### Q5：Docker Compose 启动后 MySQL 连接失败

**原因**：容器间网络不通，或 MySQL 未初始化完成就启动了后端。

**解决**：
- docker-compose.yml 中后端服务添加 `depends_on` + healthcheck（yml 示意见技术设计§7）
- 确认 `DB_HOST` 使用 Docker 服务名（如 `mysql`）而非 `localhost`

```yaml
backend:
  depends_on:
    mysql:
      condition: service_healthy
```

### Q6：NL2SQL 服务报 403 "内部令牌校验失败"

**原因**：后端与 NL2SQL 服务 `.env` 中的 `NLQ_INTERNAL_TOKEN` 不一致，或调用方未带 `X-Internal-Token` 请求头。

**解决**：
```bash
# 两端用同一命令生成
openssl rand -hex 32
# 同步写入 backend/.env 与 nlq-service/.env
```

### Q7：报表导出 PDF 中文显示空白/方块

**原因**：iText 7 默认字体不含 CJK 字形。

**解决**：
- 下载 Noto Sans SC（或思源黑体）字体文件放入 `backend/src/main/resources/fonts/`
- `PdfUtil.java` 启动时执行 `FontProgramFactory.registerFont()` 注册
- 导出时使用注册后的字体创建 `PdfFont`

### Q8：用户反馈"按钮怎么没反应"——管理员点击"导入课表"无反应

排查路径：
1. DevTools Network 看 `/api/v1/schedules/import` 返回码
2. 401 → Token 过期或未携带；3. 413 → 文件超限（默认 5MB，需调大 `spring.servlet.multipart.max-file-size`）；4. 500 → 看后端日志 stacktrace
5. Excel 解析失败 → 看返回的 `errors` 列表（每行号 + 失败原因）

---

## 8. 修订记录

### V1.1（2026-09-07）按后端设计审查报告修复

| 编号 | 修复项 | 对应审查报告 |
|------|--------|-------------|
| 1 | JDK 锁定 17 LTS、Spring Boot 锁定 3.3.x | P2-5 |
| 2 | 环境变量：NLQ_DB_USER（只读账号）、DEEPSEEK_TIMEOUT、NLQ_INTERNAL_TOKEN、JWT_SECRET 生成方式 | P0-1 / P0-6 / P1-4 / P2-13 |
| 3 | 后端目录：新增 GlobalExceptionHandler、NotificationController/Service、Notification/OperationLog 实体 | P0-3 / P0-4 / P2-3 |
| 4 | AuthService 加账号锁定逻辑注释 | P0-5 |
| 5 | SemesterService 注释：状态查询时动态计算 | P1-8 |
| 6 | CourseService 注释：合班分组规则 | P0-7 |
| 7 | ReportService 注释：aggregate_only 分层可见 | P0-8 |
| 8 | NlqService 注释：转发时携带 X-Internal-Token | P0-1 |
| 9 | util/ 新增 PdfUtil.java（中文字体注册） | P1-2 |
| 10 | nlq-service 目录：新增 middleware/internal_auth.py、services/row_limiter.py、config/permission_rules.yaml | P0-1 / P0-2 / P1-3 |
| 11 | 数据库初始化脚本：新增 ltpms_readonly 只读账号 + 字符集锁定注释 + 安全提醒 | P0-6 / P2-6 |
| 12 | NL2SQL 启动：.env 填写说明 + 内部令牌提示 | P0-1 |
| 13 | 编码规范：测试覆盖率要求 | P2-11 |
| 14 | 新增 Q6/Q7/Q8 常见问题 | 综合 |
| 15 | SchedulingConfig 配置类（学期提醒定时任务） | P0-3 |

---

*文档修订完毕。后续可在 V1.1 基础上继续迭代。*

---

## 7. 打包发布

### 7.1 前端打包

```bash
cd frontend
pnpm build
# 输出到 dist/ 目录
# Nginx 部署时将 dist/ 内容复制到 nginx/html/
```

### 7.2 后端打包

```bash
cd backend
mvn clean package -DskipTests
# 输出 target/ltpms-0.0.1-SNAPSHOT.jar
# 运行：java -jar target/ltpms-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

### 7.3 NL2SQL 服务打包

```bash
# Docker 打包
cd nlq-service
docker build -t ltpms-nlq:latest .
docker run -d -p 8001:8001 --env-file .env ltpms-nlq:latest
```

---

*6份文档全部完成。现在可以开始写代码了——每一步都有文档参照。*

**开发指南确认完毕。6份文档齐了，有什么要调整的吗？确认后即可进入开发阶段。**
