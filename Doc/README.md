# LTPMS — 基于 AI 大模型的实验教学项目管理系统

> 让实验教学数据「有人管、查得到、看得懂」。

LTPMS 面向高校教务管理人员与实验课教师，集中维护**实验室、学期与校历、实验课表、教师账号、实验项目**等核心数据，在此基础上提供统计报表、数据智能洞察，并内置 AI 实验助教「阿实」——支持自然语言查询（NL2SQL）、多轮对话 Agent、人员档案检索、课表拍照识别与叙事报告生成。

| | |
|---|---|
| 前端 | Vue 3.5 + TypeScript + Vite 5 + Element Plus 2.9 + Pinia + ECharts |
| 后端 | Spring Boot 3.3.3（Java 17）+ MyBatis-Plus 3.5.7 + Spring Security + JWT |
| AI 服务 | Python FastAPI 0.115（Agent 循环 / 工具注册表 / 技能框架 / NL2SQL） |
| 数据库 | MySQL 8.4（utf8mb4_unicode_ci，14 张业务表，逻辑外键） |
| 大模型 | DeepSeek API（对话/推理）+ 智谱 GLM-4V-Flash（课表照片识别） |
| 部署 | Docker Compose / Windows 本地一键脚本 |

---

## 1. 功能总览

### 管理员（ADMIN）

- **实验室管理**：单条新增/编辑/删除、批量删除、Excel 批量导入（含模板下载）、关联数据校验（有课表/开放计划时拒绝删除）。
- **学期与校历**：手动创建学期（起止日期），状态按日期动态计算（UPCOMING / ACTIVE / ENDED）；周历（第 1 周起始日 + 周次↔日期映射）、节假日管理、每年 8/15、1/15 站内提醒。
- **实验室开放管理**：开放计划（星期 + 节次 + 周次范围 + 单双周）单条 CRUD、批量覆盖设置、从上学期复制（幂等）、例外日；**占用视图**（总览列表 + 单间 7×12 周网格四色态）与课表矛盾标红（只读诊断，不阻断导入）。
- **课表管理**：Excel 导入（先解析预览 → 版本二次校验 → 确认覆盖落库）、**拍照导入**（GLM-4V 识别 → 草稿预览修正 → 走标准导入）、批量删除、开放注记列；导入后按（课程号、学期）自动聚合生成实验课程（同课程号多记录教师相同 → 人数累加、专业去重；教师不同 → 整组拒绝）。
- **教师账号管理**：单条新增/编辑、Excel 批量导入、重置密码、启用/停用、批量删除、头像（内置预设 + 本地上传）；默认密码 `Ltpms@{工号}`，首次登录强制改密。
- **字典管理**：实验类别/类型/学科/要求/实验者类别五类字典动态维护。
- **报表中心**：实验室·实验项目·人数统计、实验室人时数统计（人时数 = 学时 × 人数，标注「学生学时」口径），Excel / PDF 导出；AI 叙事报告导出（数字真实 + LLM 语言包装）。
- **数据智能**：课程就绪度体检、实验室利用率热力图（未配置开放计划为「未开放」灰态）、异常预警（超排/闲置/课时异常/有课未开放）、排课冲突检测（实验室·教师·时间三方）。
- **通知中心与操作审计**：顶栏通知面板；全部写操作经 AOP 切面记录 `operation_log`（操作人/动作/目标/明细/IP/时间）。

### 教师（TEACHER）

- 仅可见本人所授课程（服务端强制注入 `teacher_id`，无法参数越权）。
- **实验项目管理**：按学期与课程分组，新增/编辑/删除、Excel 导入导出、从上学期复制（编号重新生成、无数据返回 43001）、历史学期只读。
- **报表**：默认 `aggregate_only=true` —— 全校汇总行 + 本人明细行，显式传 false 被忽略。
- **智能查询「阿实」**：多轮对话 Agent（工具调用过程实时上屏）、NL2SQL 单轮查询 + 查询历史 + SQL 透明展示、人员档案（仅本人）、材料清单生成。
- **数据智能**：本人课程就绪度体检。

### 智能查询「阿实」（ADMIN + TEACHER）

- **NL2SQL 全链路**：Prompt 约束 → sqlparse 白名单校验（仅 SELECT）→ 数据权限注入 → `ltpms_readonly` 只读账号执行 → 行数上限 500。
- **Agent 多轮**：计划 → 调工具 → 观察 → 回答循环（max_steps=6），工具统一四元组注册（名称/描述/参数 Schema/执行器），5 个 YAML 技能包（统计/体检/档案/材料清单/叙事）。
- **人员档案检索**：输入人名 → 八维档案卡（身份账号/任课课程/排课记录/课节明细/实验项目/管理的实验室/行为足迹/派生洞察）；双份载荷——敏感字段只上屏不进 LLM 上下文；「我的档案」锁定入口由服务端从 JWT 反查用户。
- **长期记忆**：按用户隔离（SQLite），跨会话召回偏好与上下文摘要。
- **MCP 只读服务**：独立进程 `python -m mcp_server.server` 向外部 AI 客户端（WorkBuddy / Claude 等）只读暴露数据工具。

### 通用

登录（JWT 2 小时、失败 5 次锁定 30 分钟）、首次登录强制改密、头像上传、通知中心、操作审计、复古终端视觉（落地首页 / 检索终端 / 悬浮阿实）。

---

## 2. 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│ 浏览器 ── Vue 3 + TS + Element Plus SPA（复古终端视觉）        │
└──────────────────────────┬──────────────────────────────────┘
                           │ HTTP/REST · JWT Bearer
┌──────────────────────────▼──────────────────────────────────┐
│ Spring Boot 后端（唯一对外入口，:8080）                        │
│  认证(JWT/锁定) · 业务(实验室/学期/课表/课程/实验/报表/通知/审计)│
│  开放管理 · 数据智能 · NLQ 代理(注入身份) · 导出(Excel/PDF)     │
└──────┬────────────────────────────┬─────────────────────────┘
       │ TCP                        │ HTTP + X-Internal-Token
┌──────▼───────┐    ┌───────────────▼─────────────────────────┐
│ MySQL 8.4    │    │ nlq-service（FastAPI，:8001）            │
│ ltpms_db     │◄───│  Agent 循环 · 工具/技能 · 人员档案         │
│ 14 张业务表   │    │  权限注入 · SQL 校验 · Schema 缓存 · 记忆  │
│ 只读账号:     │    │  视觉(GLM-4V) · 数字档案/叙事              │
│ ltpms_readonly│    └───────────────┬─────────────────────────┘
└──────────────┘                    │ HTTPS
                        ┌───────────▼───────────┐
                        │ DeepSeek API          │
                        │ GLM-4V-Flash          │
                        └───────────────────────┘
```

### 端口一览

| 服务 | 本地脚本模式 | Docker 模式 | 说明 |
|------|------------|------------|------|
| 前端 | **8000**（`vite preview` 服务构建产物） | **80**（Nginx） | 本地脚本跑的是 `dist`，改代码需 `npm run build` 后重启 |
| 后端 | **8080** | 8080 | 唯一对外入口，NLQ 请求由后端代理转发 |
| nlq-service | **8001** | 8001（仅 `expose`，不映射宿主机） | 需要 `X-Internal-Token` 头 |
| MySQL | **3307**（本地实例，数据目录 `.tools/mysql-data`） | 3306 | 本地脚本特意用 3307 避免与系统 MySQL 冲突 |
| MCP Server | 8800（可选，`python -m mcp_server.server`） | — | SSE/stdio，只读 |

> 前端开发模式（`npm run dev`）默认 **5173** 并代理 `/api` 到 8080；本地一键脚本用的是构建产物 + preview(8000)。

### 服务间安全模型

| 链路 | 鉴权 |
|------|------|
| 前端 ↔ 后端 | JWT Bearer Token（2 小时过期，无 refresh token） |
| 后端 ↔ nlq-service | 内部令牌 `X-Internal-Token`（≥32 字节随机串）；用户身份由后端从 JWT 解析后注入，**服务不信任外部传入的身份字段** |
| nlq-service ↔ MySQL | **专用只读账号 `ltpms_readonly`**（第四层防护，禁止使用业务账号） |
| MCP ↔ 外部客户端 | SSE 经 `MCP_TOKEN` 校验；只读账号 |

---

## 3. 目录结构

```
AI LTPMS/
├── frontend/                  # Vue 3 前端
│   ├── src/
│   │   ├── api/               # 接口封装（不在组件里直接调 axios）
│   │   ├── components/        # 通用组件（ImportDialog / PersonDossier / NlqWidget / schedule/ 等）
│   │   ├── pages/             # 页面：landing/login/dashboard/laboratory/semester/schedule/
│   │   │                      #       teacher/course/experiment/report/nlq/insight/dict
│   │   ├── layouts/           # BasicLayout（挂载悬浮阿实）等
│   │   ├── router/            # 路由 + 守卫（public + roles 双层控制）
│   │   ├── store/             # Pinia（token 存 sessionStorage：ltpms_token）
│   │   ├── styles/            # 全局样式 / unit-skin（机台皮肤）
│   │   └── utils/             # request.ts（axios 封装+拦截器）/ unitLed.ts 等
│   └── vite.config.ts         # dev 5173 / preview 8000 / 代理 8080
│
├── backend/                   # Spring Boot 后端
│   ├── src/main/java/com/ltpms/
│   │   ├── controller/        # 18 个 Controller（含 FileController/InsightController/HolidayController 等）
│   │   ├── service/           # 业务逻辑
│   │   ├── mapper/            # MyBatis-Plus Mapper
│   │   ├── entity/ dto/       # 实体与传输对象（列表接口返回脱敏 VO）
│   │   ├── common/            # Result / PageResult / ErrorCode / 全局异常处理
│   │   ├── filter/ aspect/    # JwtAuthFilter / RoleAspect / OperationLogAspect
│   │   └── util/              # JwtUtil / ExcelUtil / PdfUtil（中文字体注册）
│   ├── src/main/resources/    # application.yml / application-dev.yml / mapper XML
│   └── pom.xml
│
├── nlq-service/               # Python AI 微服务（阿实）
│   ├── app/
│   │   ├── main.py            # FastAPI 入口：注册工具 + 内部令牌中间件
│   │   ├── routers/nlq.py     # /query /schema /agent /recognize-schedule /archive /narrative
│   │   ├── middleware/        # internal_auth.py
│   │   └── services/          # agent_loop / tool_registry / permission / sql_validator /
│   │                          # person_tools / intent_router / skill_loader / memory_store /
│   │                          # vision / narrative_service / text_generation ...
│   ├── mcp_server/            # MCP 只读服务（独立进程）
│   ├── skills/                # 5 个 YAML 技能包
│   ├── tests/                 # 16 个 pytest 测试文件
│   └── requirements.txt
│
├── database/                  # 建库建表 + 初始数据 + 增量升级脚本
│   ├── init.sql               # schema 权威来源
│   ├── dict_data.sql          # 字典初始数据
│   └── upgrade_*.sql          # 存量库增量升级（头像 / 学期开放 / 课程修订）
│
├── docker/                    # Docker Compose + 三个服务的 Dockerfile
├── scripts/                   # 本地一键脚本（*-run.bat 幂等后台启动）+ 测试/造数 Python 脚本
├── start-all.bat / stop-all.bat   # 本地四服务一键启停（Windows）
├── .env.example               # Docker / 环境变量模板
└── LTPMS_*.md                 # 设计文档集（PRD / 技术设计 / API / 设计方案 V2.0 等）
```

---

## 4. 快速开始

### 方式 A：Windows 本地一键脚本（推荐日常开发）

前置：本机已装 JDK 17、Node.js、Python 环境与 MySQL 8.4 二进制（脚本内硬编码了工具路径，换机需修改 `scripts/*-run.bat` 顶部的 `JAVA` / `NODE` / `PY` / mysqld 路径）。

```bash
# 在项目根目录双击或命令行执行：
start-all.bat     # 依次拉起 MySQL(3307) → 后端(8080) → nlq-service(8001) → 前端(8000)
stop-all.bat      # 按端口停止全部服务
```

- 脚本**幂等**：端口已在监听则跳过；各服务在隐藏窗口后台运行，日志分别写入 `.tools\*-console.log`。
- 首次使用前需初始化数据库（见第 5 节），并确保 `backend/target/ltpms-backend-0.0.1-SNAPSHOT.jar` 与 `frontend/dist/` 已构建。
- 访问 <http://localhost:8000>，账号 `admin` / `admin123`（首次登录强制改密）。

单个服务也可独立启动：

```bash
scripts\mysql-run.bat      # MySQL 3307
scripts\backend-run.bat    # Spring Boot 8080
scripts\nlq-run.bat        # FastAPI 8001（python -m app.main）
scripts\frontend-run.bat   # vite preview 8000（服务 dist 构建产物）
```

### 方式 B：手动分步启动（前端热更新开发）

```bash
# 1) 数据库（本机 MySQL 或 Docker 均可，连接参数见第 5 节）
mysql -u root -p < database/init.sql
mysql -u root -p ltpms_db < database/dict_data.sql

# 2) 后端（端口 8080）
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# 或：mvn -DskipTests package && java -jar target/ltpms-backend-0.0.1-SNAPSHOT.jar

# 3) nlq-service（端口 8001）
cd nlq-service
python -m venv .venv && .venv\Scripts\activate     # Windows
pip install -r requirements.txt
cp .env.example .env                                # 填入 DeepSeek Key 等（见第 6 节）
python -m app.main                                  # 或 uvicorn app.main:app --port 8001 --reload

# 4) 前端开发服务器（端口 5173，代理 /api → 8080，支持热更新）
cd frontend
npm install
npm run dev
```

### 方式 C：Docker Compose（部署）

```bash
cp .env.example .env    # 填写 MYSQL_ROOT_PASSWORD / DB_PASSWORD / NLQ_DB_PASSWORD /
                        #      JWT_SECRET / DEEPSEEK_API_KEY / NLQ_INTERNAL_TOKEN
cd docker
docker compose up -d --build
```

访问 <http://localhost:80>（前端 Nginx）；后端 8080；nlq-service 仅容器内暴露（不映射宿主机）；MySQL 3306。后端与 nlq 均 `depends_on: mysql (service_healthy)`。

---

## 5. 数据库

- **字符集**：`utf8mb4` / `utf8mb4_unicode_ci`；**逻辑外键**（不建物理外键，应用层保证完整性，教务导入容错需要）。
- **14 张业务表**：`user`、`laboratory`、`semester`、`schedule`、`schedule_time`、`course`、`experiment`、`dictionary`、`query_history`、`notification`、`operation_log`、`holiday`、`lab_open_time`、`lab_open_exception`。
- `database/init.sql` 为 **schema 权威来源**；存量库升级走 `upgrade_*.sql` 增量脚本（当前有：`upgrade_20260909_user_avatar.sql`、`upgrade_20260914_semester_open.sql`、`upgrade_20260916_course_revision.sql`、`upgrade_20260922_knowledge_base.sql` 等，完整清单见 `database/` 目录）。

**账号模型**（本地脚本默认：库 `ltpms_db` @127.0.0.1:3307，业务账号 `ltpms/ltpms`）：

| 账号 | 权限 | 用途 |
|------|------|------|
| `ltpms` | 读写 | Spring Boot 业务 |
| `ltpms_readonly` | **仅 SELECT** | nlq-service 专用（四层 SQL 防护的第四层） |

> ⚠️ nlq-service 的 `.env` 必须使用 `ltpms_readonly`，**禁止**使用业务账号，否则数据库只读兜底失效。

初始管理员：`admin` / `admin123`（首次登录强制改密）。

---

## 6. 配置与环境变量

### 后端（`backend/src/main/resources/application.yml` + 环境变量）

| 变量 | 默认/说明 |
|------|----------|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | 数据源（dev 默认 `127.0.0.1:3306/ltpms_db`；本地脚本注入 3307） |
| `JWT_SECRET` | **必须替换**（生成：`openssl rand -base64 48`，禁止硬编码入库/入 git） |
| `JWT_EXPIRATION` | 7200000（2 小时，毫秒） |
| `NLQ_SERVICE_URL` | `http://localhost:8001` |
| `NLQ_INTERNAL_TOKEN` | 与 nlq-service 共享（生成：`openssl rand -hex 32`） |
| `UPLOAD_AVATAR_DIR` | 头像存储目录（默认 `./uploads/avatar`） |

其他关键配置：文件上传 10MB / 请求 50MB；逻辑删除字段 `deleted`；NLQ 限流 10 次/分钟/用户；CORS 白名单在 `application-dev.yml`。

### nlq-service（`nlq-service/.env`）

```ini
DEEPSEEK_API_KEY=...          # 必填
DEEPSEEK_API_URL=https://api.deepseek.com/chat/completions
DEEPSEEK_MODEL=deepseek-chat
DEEPSEEK_TIMEOUT=15           # 秒，硬上限，超时降级不重试（对应错误码 44003）
ZHIPU_API_KEY=...             # 可选：GLM-4V 课表照片识别；为空时视觉能力优雅降级
DB_HOST=127.0.0.1
DB_PORT=3307                  # 本地脚本模式；Docker 内为 3306
DB_NAME=ltpms_db
DB_USER=ltpms_readonly        # 只读账号！
DB_PASSWORD=...
NLQ_INTERNAL_TOKEN=...        # 与后端一致
PORT=8001
# MCP（V3）：MCP_ENABLED=false 拒绝启动；MCP_PORT=8800；MCP_TOKEN SSE 校验；MCP_MAX_ROWS=100
```

### 前端

`frontend/.env.development`：`VITE_API_BASE=/api/v1`（请求经 Vite 代理到 8080，代理层静态覆盖 `Origin` 以避开 CORS 白名单）。

---

## 7. API 约定

- **前缀**：`/api/v1`；**响应信封**：`{ "code": 200, "message": "success", "data": ... }`，分页为 `{ records, total, page, size }`。
- **认证**：`Authorization: Bearer <token>`；角色控制注解 `@RequireRole("ADMIN")`。
- **分页入参**：`page` / `size`（映射 MyBatis-Plus `current` / `size`）。

**模块入口**：`/auth`、`/laboratories`、`/semesters`（含 `/holidays`、`/openings`）、`/schedules`、`/teachers`、`/courses`、`/experiments`、`/reports`、`/nlq`、`/dicts`、`/notifications`、`/dashboard`、`/insight`、`/files/avatar`。

**错误码（节选，完整见 `ErrorCode.java` 与《API 设计文档》）**：

| 码 | 含义 |
|----|------|
| 200 / 400 / 401 / 403 / 404 / 429 / 500 | 成功 / 参数错误 / 未认证 / 无权限 / 不存在 / 限流 / 服务器错误 |
| 41001~41003 | 账号锁定 / 原密码错误 / 新密码过简（≥8 位含字母数字） |
| 42001~42004 | 课表导入：教师工号不存在 / 实验室编号不存在 / 同课程号多位教师 / 存在修订需确认 |
| 43001 | 上学期无实验项目（复制失败） |
| 44001~44004 | NLQ：无法安全转换 / 无法建立数据权限 / 模型超时 / 智能查询服务不可达 |
| 45001 | 功能尚未实现 |

---

## 8. 开发与构建

```bash
# 后端：编译打包（运行中的后端会锁 jar，先停服务）
cd backend && mvn -DskipTests package
mvn test                                   # JUnit 5

# 前端：类型检查 / 构建 / 预览
cd frontend
npm run typecheck                          # vue-tsc --noEmit
npm run build                              # 产物到 dist/（本地脚本服务的就是它，改代码必须重新构建）
npm run preview                            # 8000，等价于 scripts\frontend-run.bat

# nlq-service：测试
cd nlq-service && pytest                   # 16 个测试文件（agent/permission/sql_validator/vision...）

# MCP 只读服务（可选，独立进程）
cd nlq-service && python -m mcp_server.server
```

**编码约定**：后端分层（Controller 只做参数校验与调用）、统一 `Result<T>` 响应、`BusinessException` + 全局异常处理器、Service 层关键日志带 userId/requestId；前端 `<script setup lang="ts">`、样式 `<style scoped>`、接口统一封装在 `src/api/`；Python PEP 8 + type hints，敏感配置走 `.env`。

---

## 9. 辅助脚本（`scripts/`）

| 脚本 | 用途 |
|------|------|
| `mysql-run.bat` / `backend-run.bat` / `nlq-run.bat` / `frontend-run.bat` | 单服务后台启动（幂等、隐藏窗口、日志到 `.tools/`） |
| `ltpms-autostart.bat` | 开机自启入口 |
| `gen_test_data.py` / `import_all_experiments.py` | 生成与导入演示数据 |
| `seed_lab_openings.py` / `seed_t015_hanyan.sql` | 开放计划种子数据 |
| `parse_schedule_time.py` | 课表时间文本解析辅助 |
| `mcp_smoke.py` / `mcp_sse_check.py` | MCP 服务连通性冒烟 |
| `stage3_live_check.py` / `stage3_proxy_check.py` | AI 阶段联通性/代理链路检查 |

---

## 10. 设计文档索引（根目录）

| 文档 | 内容 |
|------|------|
| `LTPMS_PRD.md` | 产品需求（用户故事、功能分层、边界、非功能指标） |
| `LTPMS_项目设计方案_V2.0.md` | **与当前实现基线一致**的总设计（功能架构、技术实现、变更说明） |
| `LTPMS_技术设计文档.md` | 详细技术设计与关键实现（SQL 防护、权限注入、导出等） |
| `LTPMS_API设计文档.md` | 全局约定、表结构、全部接口定义、修订记录 |
| `LTPMS_开发指南.md` | 环境要求、目录结构、启动步骤、编码规范、FAQ |
| `LTPMS_后端开发文档_SpringBoot.md` / `LTPMS_后端设计审查报告.md` | 后端实现细则与审查修复项 |
| `LTPMS_UI设计系统_V1.1.md` / `LTPMS_UI设计规范.md` / `LTPMS_组件规范.md` / `LTPMS_Ardot画布落地蓝图.md` | 视觉与组件规范 |
| `LTPMS_智能化升级路线图.md` / `智能化升级任务清单.md` / `特色功能创意池.md` | AI 升级规划与落地清单 |
| `LTPMS_*_需求拷问记录.md` | 三次关键需求评审的决策留痕（人员档案 / 学期开放 / 教师课程管理） |

---

## 11. FAQ

**Q：为什么本地是 3307/8000，文档里写 3306/5173？**
本地脚本刻意用 3307 避开系统 MySQL，并用 `vite preview`(8000) 服务构建产物；`npm run dev` 开发模式才是 5173。两套口径都可用，按需选择。

**Q：改了前端代码，8000 端口没变化？**
8000 服务的是 `dist` 构建产物，需 `npm run build` 后重启 `scripts\frontend-run.bat`（或改用 `npm run dev` 热更新开发）。

**Q：后端 `mvn package` 报 “Unable to rename ... .jar.original”？**
运行中的后端 JVM 锁住了 jar。先停后端（`stop-all.bat` 或按端口结束进程）再打包。

**Q：nlq-service 返回 44003 / 44004 有什么区别？**
44003 = DeepSeek 模型超时/不可用（检查 Key、网络、余额）；44004 = 服务未启动或连接失败（检查 8001 端口与 `NLQ_INTERNAL_TOKEN` 是否两端一致）。

**Q：直接 curl nlq-service 报 403「内部令牌校验失败」？**
除 `/health` 外均需 `-H "X-Internal-Token: <token>"`，且该 token 必须与后端 `NLQ_INTERNAL_TOKEN` 一致。

**Q：PDF 导出中文变方块？**
iText 需注册 CJK 字体：字体文件放 `backend/src/main/resources/fonts/`（Noto Sans SC），由 `PdfUtil` 启动时注册。

**Q：Excel 导入报错怎么看？**
接口返回的 `errors` 列表含行号 + 失败原因；课表导入的教师工号/实验室编号需先存在（42001/42002），同课程号多教师会整组拒绝（42003）。

---

## 12. 安全须知

- `JWT_SECRET`、`NLQ_INTERNAL_TOKEN`、各类密码/API Key 一律通过环境变量或 `.env` 注入，**严禁入库入 git**（`.gitignore` 已排除 `.env` 与运行数据）。
- nlq-service 连库必须用 `ltpms_readonly`；AI 通道**永不直接写库**——写意图止于「草稿 → 人工确认 → 落库」。
- 教师数据边界为服务端强制注入，任何前端参数无法越权；人员档案敏感字段（密码/手机号）不进 LLM 上下文。
- MCP Server 未做角色隔离，仅限本地演示；面向多用户需在 MCP 层补权限注入。
