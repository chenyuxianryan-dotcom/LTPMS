# LTPMS 大量测试数据 — 使用说明

## 产出清单（`outputs/test_data/` + `scripts/`）

| 文件 | 数量 | 说明 |
|---|---|---|
| `data_lab_30.xlsx` | 30 条 | 实验室 3 栋楼×5 层×2 间 |
| `data_teacher_20.xlsx` | 20 条 | 教师 T001~T020 跨 10 个院系 |
| `data_schedule_120.xlsx` | 120 条 | 课表，跨 46 门课程 |
| `data_experiment_*.xlsx` | 46 份 | 实验项目，每门课 4-6 条 |
| `data_seed.sql` | — | 历史学期 2 个 + 200 query_history + 30 notification |
| `clear.sql` | — | 可选：清空业务表（破坏性！） |
| `../scripts/parse_schedule_time.py` | — | 课表导入后回填 schedule_time |
| `../scripts/import_all_experiments.py` | — | 批量上传 46 份实验 xlsx |
| `../scripts/gen_test_data.py` | — | 重新生成所有数据（可调规模） |

## 字段约束

- **实验室编号**：形如 `36-101`（栋-房号），3 栋×10 间=30 个
- **教师工号**：`T001`~`T020`，默认密码规则 `Ltpms@{工号}`（如 T003 → `Ltpms@T003`）
- **课表"教师工号"**：必须是已导入的 T001~T020 之一
- **课表"实验室编号"**：必须是已导入的 36-101~38-502 之一
- **课表"课程号"**：形如 `CS101` `AI277`，导入时自动建 `course` 表
- **实验"所属学科"**：用 dict_value 英文（`cs` `se` `network` `ai` `data_science` `iot` `security` `electronics`）
- **实验"实验类别/类型/要求/者类别"**：英文 value（`basic` `comprehensive` `required` `undergraduate` 等）

完整字典值见 MySQL `dictionary` 表，5 类共 20+ 选项。

## ⚠️ 导入前必读

**当前数据库已存在数据**（7 实验室 / 5 教师 / 6 课程 / 6 排课），与本批次数据会冲突：
- 实验室冲突 5 个（36-401, 36-402, 37-201, 37-202, 38-105）
- 教师冲突 5 个（T001~T005）
- 课表里引用 T001~T005 的条目会因"教师工号不存在"失败

**两种处理方式**（二选一）：

### 方式 A：先清空再导入（推荐用于压测）

```bash
# 1. 清空业务表，保留字典
mysql -h127.0.0.1 -P3307 -ultpms -pltpms ltpms_db < outputs/test_data/clear.sql

# ⚠️ clear.sql 会把 user 表清空，admin 也没了！
#    跑完后 admin 密码需要重建：用浏览器注册一个新 admin，或手动重置
#    最简：clear.sql 里加一段重建 admin（参考 clear.sql 注释）
```

### 方式 B：避开冲突、仅导入新增数据

直接走下面"正常导入流程"，冲突的会失败但新增的会成功。
- 实验室 30 → 25 新增 / 5 失败
- 教师 20 → 15 新增 / 5 失败
- 课表 120 → 部分会因教师工号/实验室编号冲突失败
- 实验 → 课程若未生成会全失败

**只建议在"想保留现有数据 + 仅扩量"时用此方式。**

---

## 正常导入流程（按顺序！）

### 1. 实验室（浏览器手动）

浏览器 → 登录 admin → `实验室管理` → 导入按钮 → 选 `data_lab_30.xlsx` → 上传

### 2. 教师（浏览器手动）

`教师管理` → 导入按钮 → 选 `data_teacher_20.xlsx` → 上传
**注意**：导入的教师默认密码为 `Ltpms@{工号}`，可登录后改密

### 3. 课表（浏览器手动）

`课表管理` → 导入按钮 → 选 `data_schedule_120.xlsx` → 上传
- 导入时会要求选学期，选 `2026-2027-1`（当前学期）
- 导入后自动建 ~46 门 course

### 4. 实验项目（用脚本批量，省 46 次手动）

```bash
python scripts/import_all_experiments.py
```

脚本会自动：
1. 登录拿 token
2. 拉取当前学期所有课程
3. 匹配 46 份 xlsx → 对应 courseId
4. 批量 POST 上传
5. 输出成功/失败/跳过统计

### 5. 回填 schedule_time

```bash
pip install pymysql   # 首次需要
python scripts/parse_schedule_time.py
```

解析 `schedule.raw_schedule_text` 写入 `schedule_time` 表
（让教师排课周历功能正常显示蓝色周次）

### 6. SQL 辅助数据

```bash
mysql -h127.0.0.1 -P3307 -ultpms -pltpms ltpms_db < outputs/test_data/data_seed.sql
```

包含：
- 2 个历史学期（让"上学期复制"功能有数据可拷）
- 200 条 query_history（教师/管理员"最近查询"卡片有数据）
- 30 条 notification（顶栏铃铛测试）

### 7. 验收

- 概览页（管理员）：实验室 30/课程 46/实验 230+/教师 20，方格图全部填充
- 概览页（教师 T003）：我的课程 1 门 + 我的实验 + 排课周历（应有 1-16 周蓝色）
- 实验项目页：每门课有 4-6 条实验

---

## 调整数据规模

修改 `scripts/gen_test_data.py` 顶部的参数：

```python
gen_teachers(20)            # 改 40 = 大型
gen_schedules(120, ...)     # 改 300 = 大型
# gen_experiments_by_schedule 内 n = random.randint(4, 6) 改 6-10
```

然后重跑：

```bash
python scripts/gen_test_data.py
```

**注意**：gen 出的课程数 = schedule 引用的唯一 code 数。如改 300 条 schedule 但仍想 50 门课程，可手动调 `n_courses = 50`（在 `gen_schedules` 函数里）。

## 数据生成特性

- **真实感**：实验室按栋-层-房号规则命名；教师姓名从姓氏+名字库随机；课程号按学科前缀（CS/SE/AI/...）
- **跨表引用一致**：教师工号 T001~T020 贯穿教师/课表两张表；实验室编号 36-101~38-502 贯穿实验室/课表
- **时间格式规范**：`周X第A-B节(C-D周)`，支持单双周（`(1-16单周)`）；parse_schedule_time.py 能正确解析所有 8 种 wp 模板
- **可复现**：脚本用 `random.seed(42)`，同一脚本每次产出数据完全一致

## 文件位置速查

```
E:\projects\AI LTPMS\
├── outputs/test_data/
│   ├── data_lab_30.xlsx
│   ├── data_teacher_20.xlsx
│   ├── data_schedule_120.xlsx
│   ├── data_experiment_{CODE}.xlsx   (46 份)
│   ├── data_seed.sql
│   ├── clear.sql
│   └── README.md                     (本文件)
└── scripts/
    ├── gen_test_data.py              (生成器)
    ├── import_all_experiments.py     (批量实验导入)
    └── parse_schedule_time.py        (回填脚本)
```
