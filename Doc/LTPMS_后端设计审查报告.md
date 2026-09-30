# LTPMS 后端设计审查报告

> 审查对象:技术设计文档、API/数据模型文档、开发指南(后端部分)
> 参照基准:PRD、需求分析文档、需求分析拷问记录、2026-09-07 拍板决策
> 日期:2026-09-07 | 审查人:AI 设计伙伴

---

## 一、总体结论

**架构方向正确,但存在 8 个阻塞性问题(P0),不修复不能开始编码。**

核心结论:

1. **整体架构合理**:Spring Boot 单体 + FastAPI 独立 NL2SQL 微服务 + MySQL,选型与团队技术栈、数据规模(<10万行)匹配,没有过度设计。
2. **最大的问题不是"设计错",而是"需求与设计断层"**:PRD/需求分析里的 6 项 P0 需求在后端设计里找不到落地点(账号锁定、站内通知、只读账号等)。
3. **安全设计存在一个高危漏洞**:NL2SQL 服务无服务间鉴权,三层防护可被整体绕过。

| 严重度 | 数量 | 说明 |
|--------|------|------|
| P0 阻塞 | 8 | 不修不能开发,或开发出来即带安全/功能缺陷 |
| P1 重要 | 11 | 开发中必须处理,否则验收过不了 |
| P2 建议 | 13 | 规范性/一致性改进,可在开发中顺手修 |

---

## 二、P0 阻塞项(必须先修)

### P0-1 🔴 NL2SQL 服务无服务间鉴权,三层防护可被整体绕过

**位置**:技术设计 §2.3 服务间通信、§7 Docker Compose

**问题**:Docker Compose 把 nlq 服务的 8001 端口映射到宿主机。FastAPI 接口(`POST /query`)接收 `{question, user_id, role}` —— **user_id 和 role 由调用方直接传入且无校验**。任何人绕过 Spring Boot 直接:

```
curl http://目标机:8001/query -d '{"question":"...","user_id":1,"role":"ADMIN"}'
```

即可伪造管理员身份,绕过 JWT 认证、绕过权限注入,拿到全库 SELECT 能力。JWT、三层防护、数据权限注入全部形同虚设。

**修复方案(三选一或组合)**:
1. Compose 中 **不映射 8001 到宿主机**,只允许 Docker 内网访问(expose 而非 ports);
2. FastAPI 增加内部服务令牌校验(Spring Boot → FastAPI 请求头携带 `X-Internal-Token`,双方环境变量共享);
3. 前端 Nginx 只反代 `/api/*` 到后端,8001 永不对外。

**推荐 1 + 2 组合**(网络层隔离 + 应用层令牌双保险)。

---

### P0-2 🔴 权限注入伪代码与表结构不符

**位置**:技术设计 §4.3

**问题**:伪代码 `permission_map = {'experiment': 'teacher_id', ...}`,但 API 文档 §1.2.7 的 **experiment 表根本没有 teacher_id 字段**(通过 course_id 间接关联教师)。伪代码照抄会直接 SQL 报错。

**修复**:按 2026-09-07 已拍板方案重写——白名单表 + 字段映射:

| 表 | 注入字段 | 兜底 |
|----|---------|------|
| experiment | experiment.teacher_id(不存在) | `JOIN course c ON e.course_id=c.id AND c.teacher_id=?` |
| course | course.teacher_id | — |
| schedule | schedule.teacher_id | — |
| laboratory / semester / dictionary | 不注入(公开) | — |

JOIN/子查询同规则;**注入失败/无 teacher 关联 → 拒绝执行**。映射配置独立为 `permission_rules`(表或 YAML),启动时加载。

---

### P0-3 🔴 缺表:站内通知(P0 需求无处落地)

**位置**:PRD US-05、需求分析 FR-SEM-001(每年 8月15日/1月15日 定时提醒)

**问题**:数据库 9 张表中没有 notification 表。"系统在预设日期发送站内通知,点击跳转到创建页面" —— 数据存哪?已读状态存哪?P0 用户故事在数据库层面不可实现。

**修复**:新增表:

```sql
CREATE TABLE notification (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,           -- 接收人(NULL=广播)
  title VARCHAR(128) NOT NULL,
  content VARCHAR(512),
  type VARCHAR(32) NOT NULL,          -- SEMESTER_REMIND / SYSTEM
  ref_url VARCHAR(128),               -- 点击跳转地址
  is_read TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_user_read (user_id, is_read)
);
```

配套:`@Scheduled` 定时任务(每年 8/15、1/15 触发)+ 需求分析已确认"仅提醒不自动创建"。

---

### P0-4 🔴 缺表:操作审计日志

**问题**:技术设计 §4.4 的日志策略只覆盖 NL2SQL 查询(query_history 表)。课表导入、实验项目增删改、教师账号导入、密码重置等敏感操作**没有任何审计记录**。教务系统出争议时要能回答"谁在什么时候改了什么"。

**修复**:新增 `operation_log` 表(user_id, action, target_type, target_id, detail JSON, ip, created_at),用 AOP 切面拦截写操作。若确定 V1 不做,必须在文档中显式声明并写入风险清单,不能静默缺失。

---

### P0-5 🔴 账号锁定需求无实现方案

**位置**:需求分析 FR-AUTH-001"密码错误超过5次,锁定账号30分钟"

**问题**:user 表没有 `failed_attempts`、`locked_until` 字段;技术设计无任何计数方案。需求与技术设计完全断层。

**修复**(单机部署,无需 Redis):

```sql
ALTER TABLE user ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0,
                  ADD COLUMN locked_until DATETIME NULL;
```

AuthService 登录失败时 `failed_attempts+1`,达到 5 设置 `locked_until=NOW()+30min`;登录成功清零。

---

### P0-6 🔴 MySQL 只读账号在部署脚本中未创建,第三层防护名存实亡

**位置**:技术设计 §4.2 第3层 vs 开发指南 §3.1

**问题**:技术设计承诺"NL2SQL 专用账号仅 GRANT SELECT",但开发指南的数据库初始化脚本**只创建了 ltpms 用户且 `GRANT ALL PRIVILEGES`**。照着开发指南部署,NL2SQL 服务用全权限账号连库,三层防护的第三层不存在。

**修复**:开发指南 §3.1 初始化脚本补充:

```sql
CREATE USER 'ltpms_readonly'@'%' IDENTIFIED BY '<强密码>';
GRANT SELECT ON ltpms_db.* TO 'ltpms_readonly'@'%';
FLUSH PRIVILEGES;
```

NLQ_SERVICE 的 `.env` 中数据库连接改用该账号。

---

### P0-7 🔴 课表→课程生成逻辑不完整(多班/合班场景)

**位置**:技术设计 §3.1"按课程号+学期分组→生成 course 表记录"

**问题**:同一门课在同一学期可能有多条 schedule 记录(多班/合班/一周多次排课)。分组生成 course 时以下问题未定义:

1. 两条记录 teacher_id 不同 → 冲突(约束说一门课一学期一位教师,但原始 Excel 可能违反)
2. `student_count` 合并规则:累加?取最大?
3. `major` 多个班不同专业:course.major 只有一个 VARCHAR(128),怎么合并?

**修复**(建议规则,需确认):
- teacher 冲突 → **导入校验失败,标红该行**,提示管理员人工处理(不静默合并)
- student_count:合班累加
- major:去重后逗号拼接(如"计算机科学与技术,软件工程")

---

### P0-8 🔴 报表"分层可见"决策未落到 API(2026-09-07 拍板)

**位置**:API 设计 §2.9 报表模块

**问题**:今日拍板的"选项 B 分层可见"(教师看全校汇总行 + 自己的明细行,看不到其他教师明细)在报表接口中没有任何体现。

**修复**:

```
GET /api/v1/reports/course-project-stats?academicYear=2026-2027&aggregate_only=true
```

- 教师角色默认 `aggregate_only=true`:返回汇总行 + 过滤后的自己明细
- 管理员默认 false:全量明细
- 响应结构增加 `summary` 区块(汇总行)与 `detail` 区块(明细行)

NL2SQL 同理:教师问"全校共开设多少门课" → 返回全校数字(零明细行)。

---

## 三、P1 重要项(开发中必须处理)

### P1-1 默认密码策略自相矛盾

需求:"新密码≥8位,需包含字母和数字" vs 教师导入:"默认密码=工号后6位"(6位纯数字,双违反)。且工号不足6位/含字母时未定义。
**修复**:默认密码定义为固定前缀+工号(如 `Ltpms@{工号}`),并在文档声明"默认密码豁免复杂度校验,仅用于首次登录强制改密场景"。

### P1-2 PDF 导出中文字体缺失

iText 7 默认字体不含 CJK 字形,导出中文会空白/乱码。需引入字体文件(思源黑体/Noto Sans SC)并注册 `FontProgramFactory`。技术选型依赖表未提字体方案。

### P1-3 NL2SQL 结果无行数保护

`SELECT * FROM experiment` 全表返回,前端渲染卡死。**修复**:执行前强制追加 `LIMIT 500`(或检测已有 LIMIT),超限返回"结果超过500行,请缩小查询范围"提示。

### P1-4 DeepSeek 调用无超时/重试定义

性能预算 2-8s,但 httpx 未配置 timeout,无重试策略。10s 总预算下盲目重试必超时。
**修复**:`httpx.AsyncClient(timeout=httpx.Timeout(15.0))`,失败不自动重试(预算不允许),直接走降级提示;或 timeout=8s + 至多1次重试,需在文档明确。

### P1-5 错误码体系只有占位

`ErrorCode.java` 在目录结构中,但 API 文档只定义了 200/400 两个 code。需要完整枚举(如 40101 Token失效、40301 无权限、42001 课表导入教师不存在、42002 实验室编号缺失…),前端按 code 做差异化提示。

### P1-6 RESTful 规范问题

| 问题 | 现状 | 建议 |
|------|------|------|
| 部分更新 | `PUT /{id}/status` | 改 `PATCH` |
| 批量删除 | `DELETE /schedules/batch` + body | 改 `POST /schedules/batch-delete` |
| 路径尾斜杠 | `/laboratories/` | 统一去掉尾斜杠 |
| 调试接口 | `GET /api/nlq/schema` 对外暴露 | 生产环境禁用或加 ADMIN 权限 |

### P1-7 JWT 刷新机制未定义

技术设计提"可通过 refresh token 续期",但 API 无 `/refresh` 接口、无轮换设计。**建议**:V1 砍掉 refresh token(2小时过期重新登录,单校场景可接受),文档同步删掉该表述,避免开发时实现半吊子。

### P1-8 学期状态"自动计算"机制不明

`status ENUM('UPCOMING','ACTIVE','ENDED') 自动计算`——定时刷新(服务器假期关机错过窗口)还是查询时计算?**建议**:查询时动态计算,写库作为缓存兜底;烤问已确认"落在两学期之间默认显示最近结束的学期",此规则需写进 SemesterService 设计。

### P1-9 课表重复导入的"覆盖"语义不明

"同一学期重复导入时提示覆盖确认"——覆盖粒度是什么?整学期删除重导(级联影响 schedule_time、已生成 course?已录入 experiment 是否受影响?)还是按课程号 upsert?**必须明确并写入技术设计**,这是数据一致性的关键路径。

### P1-10 Schema 知识库缓存刷新机制不明

"启动时缓存,定期刷新"——周期未定。管理员在字典后台改了选项(如新增"虚拟仿真实验"类别),NL2SQL 的 Prompt 字典映射何时生效?**建议**:字典/表结构变更时主动刷新 + TTL 5分钟兜底。

### P1-11 限流实现方案缺失

"每用户每分钟10次"用什么实现?单校 ≤100 并发,**建议单机内存限流**(Guava RateLimiter 或 Bucket4j),无需引入 Redis,但方案要写进技术设计。

---

## 四、P2 建议项

| # | 问题 | 建议 |
|---|------|------|
| P2-1 | 前端 `xlsx` 依赖与后端 POI 导出职责重复 | 统一后端导出(API 已有 /export 接口),前端删 xlsx |
| P2-2 | `echarts` 已入依赖,但所有文档无图表页设计 | 删依赖或补设计(报表可视化是 PRD 未提的隐性范围) |
| P2-3 | 目录结构缺 `GlobalExceptionHandler` | common/ 下补全局异常处理器(开发指南 5.1 已要求统一捕获) |
| P2-4 | 通信链路写 HTTPS,部署 Nginx 80 无 TLS | 校内系统可 HTTP,但文档需自洽 |
| P2-5 | Spring Boot 版本只写 3.x | 锁定 3.3.x,并核对 mybatis-plus-spring-boot3-starter 兼容矩阵 |
| P2-6 | 字符集未锁定 | `utf8mb4_unicode_ci`(MySQL 8 默认,排序更准),写死进 init.sql |
| P2-7 | 外键策略未说明 | 明确"逻辑外键,应用层保证完整性"(教务系统惯例,便于导入容错) |
| P2-8 | 分页参数 | page/size 与 MyBatis-Plus current/size 的映射封装说明 |
| P2-9 | query_history 缺 role 字段 | 排查问题时需知道执行时角色 |
| P2-10 | Compose 无 healthcheck | 技术设计 §7 示意与开发指南 Q5 对齐,补 healthcheck |
| P2-11 | 测试策略完全空白 | 单测覆盖率目标(建议 Service 层 ≥70%)、NL2SQL 准确率评测集建设方案(≥90% 的验收证明方式) |
| P2-12 | CORS 按环境配置 | dev 放行 5173,prod 同源免跨域,用 profile 区分 |
| P2-13 | JWT_SECRET 生成方式 | 写明 `openssl rand -base64 48`,禁止硬编码进 git |

---

## 五、需求 ↔ 后端设计断层对照表

| 需求来源 | 需求内容 | 后端设计现状 | 状态 |
|---------|---------|-------------|------|
| FR-AUTH-001 | 密码错5次锁30分钟 | 无字段无方案 | ❌ P0-5 |
| US-05 / FR-SEM-001 | 站内提醒通知 | 无通知表 | ❌ P0-3 |
| 2026-09-07 拍板 | 报表分层可见 | API 无参数 | ❌ P0-8 |
| 技术设计 §4.2 | NL2SQL 只读账号 | 初始化脚本未创建 | ❌ P0-6 |
| 技术设计 §8 | 课表导入乐观锁 | schedule 表无 version 字段 | ❌ P1-9 |
| 需求分析 | PDF 导出 | 无中文字体方案 | ❌ P1-2 |
| FR-NLQ-001 | 查询历史/推荐 | query_history 已有 | ✅ |
| FR-PRJ-003 | 从上学期复制 | 流程已设计 | ✅ |
| 技术设计 §4.1 | JWT 认证 | 方案完整 | ✅ |

---

## 六、修复优先级路线

```
第一步(开发前,文档修订):
  P0-1 鉴权方案、P0-2 权限注入重写、P0-6 只读账号脚本
  P0-3 通知表、P0-4 审计表(或显式声明不做)、P0-5 锁定字段
  P0-7 合班规则、P0-8 报表 API 参数
  ↓
第二步(建库脚本):
  锁定 Spring Boot 版本、字符集、外键策略(P2-5/6/7)
  ↓
第三步(编码中处理):
  P1 全部 11 项
  ↓
第四步(验收前):
  P2-11 测试策略、其余 P2 顺手修
```

---

*审查完毕。建议先修 8 个 P0 再动第一行代码。*
