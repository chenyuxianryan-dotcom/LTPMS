"""实验项目全项目报告（T07/T08）：聚合课题全貌 + 材料文件画像 → LLM 生成六节报告。

复用档案的课题六节口径（archive_service 的聚合助手与缓存），叠加数据集清单与逐文件画像。
红线：
  - 所有统计（含完成度、应分组数、离群点）Python 侧算好，模型只引用不计算；
  - 学号 / 姓名（含教师）一律不进模型上下文，成员只给计数；
  - 容器课题缺课程 / 独立课题：目录、排课、分组节显式 None（区别于 0），与档案降级一致；
  - 模型不可用时返回确定性统计（stats）供前端降级，不白屏。
缓存：复用 ArchiveCache（entity_type=report），key = 聚合 hash（含数据集版本指纹），
      新版本上传 → 指纹变 → key 变 → 自动失效；force=True 绕过。
"""

from datetime import datetime

from app.services.archive_service import (
    ArchiveCache,
    _cache_key,
    _default_value,
    _first_row,
    _int,
    _rate,
    _stats_text,
)
from app.services.file_profile import profile_dataset_file
from app.services.text_generation import generate_text
from app.utils.db import db

# ---- 项目级聚合 SQL（真实数据，LLM 的唯一数字来源；deleted 仅加在有该列的表） ----

_SQL_PROJECT = (
    "SELECT id, project_code, project_name, description, status, source, "
    "course_id, semester_id, created_at FROM lab_project WHERE id = %s LIMIT 1"
)
_SQL_COURSE = (
    "SELECT c.id, c.course_code, c.course_name, s.semester_name "
    "FROM course c JOIN semester s ON c.semester_id = s.id WHERE c.id = %s LIMIT 1"
)
_SQL_MEMBER_COUNT = "SELECT COUNT(*) AS member_count FROM lab_project_member WHERE project_id = %s"
_SQL_TASK_STATUS = (
    "SELECT status, COUNT(*) AS cnt FROM lab_project_task "
    "WHERE project_id = %s AND deleted = 0 GROUP BY status"
)
_SQL_TASK_OVERDUE = (
    "SELECT COUNT(*) AS overdue_count FROM lab_project_task "
    "WHERE project_id = %s AND deleted = 0 AND deadline IS NOT NULL AND deadline < NOW() "
    "AND status IN ('NOT_STARTED', 'IN_PROGRESS')"
)
_SQL_TASK_COVERAGE = (
    "SELECT COUNT(DISTINCT assignee_student_id) AS assigned_student_count FROM lab_project_task "
    "WHERE project_id = %s AND deleted = 0 AND status <> 'CLOSED'"
)
_SQL_SUBMISSIONS = (
    "SELECT COUNT(*) AS submission_count, "
    "COALESCE(SUM(CASE WHEN s.reviewed_at IS NULL THEN 1 ELSE 0 END), 0) AS pending_review_count, "
    "COALESCE(SUM(CASE WHEN t.status = 'ACCEPTED' THEN 1 ELSE 0 END), 0) AS accepted_submission_count, "
    "COALESCE(SUM(CASE WHEN t.status = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejected_submission_count "
    "FROM lab_task_submission s JOIN lab_project_task t ON s.task_id = t.id "
    "WHERE t.project_id = %s AND t.deleted = 0"
)
_SQL_DATASETS = (
    "SELECT id, dataset_name, description, latest_version FROM lab_dataset "
    "WHERE project_id = %s AND deleted = 0 ORDER BY id"
)
_SQL_LATEST_VERSION = (
    "SELECT id, version_no, stored_path, original_filename, file_type, file_size "
    "FROM lab_dataset_version WHERE dataset_id = %s AND deleted = 0 "
    "ORDER BY version_no DESC, id DESC LIMIT 1"
)
_SQL_CATALOG = (
    "SELECT id, experiment_code, experiment_name, category, requirement, group_size, hours "
    "FROM experiment WHERE course_id = %s ORDER BY sort_order, id"
)
_SQL_USAGE = (
    "SELECT COUNT(sc.id) AS schedule_count, COALESCE(SUM(sc.student_count), 0) AS student_total "
    "FROM schedule sc WHERE sc.course_code = %s"
)
_SQL_GROUPING_BY_EXPERIMENT = (
    "SELECT e.id, e.experiment_code, e.experiment_name, e.group_size, "
    "COUNT(DISTINCT g.id) AS group_count, COUNT(DISTINCT pm.student_id) AS grouped_student_count "
    "FROM experiment e "
    "LEFT JOIN lab_project_group g ON g.experiment_id = e.id AND g.project_id = %s "
    "LEFT JOIN lab_project_group_member gm ON gm.group_id = g.id "
    "LEFT JOIN lab_project_member pm ON pm.project_id = %s AND pm.student_id = gm.student_id "
    "WHERE e.course_id = %s "
    "GROUP BY e.id, e.experiment_code, e.experiment_name, e.group_size "
    "ORDER BY e.sort_order, e.id"
)

_REPORT_PROMPT = (
    "你是高校实验教学中心的项目报告撰写人，为下面这个实验项目生成一份客观、克制的全项目报告。\n"
    "严格按以下六个小标题输出，缺数据的小标题写「暂无数据」，不要把原始字段名罗列进正文：\n"
    "一、项目总体概况（项目编号/名称/状态/来源、所属课程与学期、课题描述要点）\n"
    "二、成员与分组（成员总数、各实验分组进度、已分组/未分组人数、分组是否与目录基准一致）\n"
    "三、任务进展与完成度（任务总数与各状态数量、完成度、逾期任务数、已派发/未派发学生数）\n"
    "四、材料与数据画像【本节为重心】（数据集数量与版本总数、提交条数与待验收/已验收/被驳回，"
    "逐个数据集的文件画像：类型/大小/行列数/字段/缺失/数值列分布与离群点/样例行要点，"
    "客观描述数据质量与规模，不评价学生）\n"
    "五、提交与验收情况（提交总数、待验收积压、验收通过/驳回情况、排课与使用统计若有）\n"
    "六、总结与建议【本节为重心】（仅依据上述数字给 3-5 条客观建议，如未分组人数、逾期任务、"
    "待验收积压、数据缺失或离群；语气客观，不对任何学生做评价性结论）\n\n"
    "【严格要求：所有数字只能来自提供的数据，不得编造、推算或引入数据之外的数字；"
    "完成度、应分组数、离群点等派生值已在数据中算好，直接引用，不要自行计算或改写口径；"
    "正文不得出现任何学生的学号或姓名】\n"
    "篇幅：整篇 1200-1800 字，信息密度优先，语言克制客观。\n\n"
    "项目数据：\n{stats}"
)


def _catalog_overview(rows: list[dict]) -> dict:
    sizes = [_int(row, "group_size") for row in rows]
    return {
        "experiment_count": len(rows),
        "group_size_min": min(sizes) if sizes else None,
        "group_size_max": max(sizes) if sizes else None,
        "experiments": [
            {
                "experiment_code": row.get("experiment_code"),
                "experiment_name": row.get("experiment_name"),
                "group_size": _int(row, "group_size"),
                "hours": _default_value(row.get("hours")),
            }
            for row in rows
        ],
    }


async def _collect_datasets(project_id: int) -> tuple[list[dict], list[str]]:
    """数据集清单 + 逐文件画像；同时产出「版本指纹」（dataset_id:latest_version）供缓存 key。"""
    dataset_rows = await db.execute(_SQL_DATASETS, (project_id,))
    datasets: list[dict] = []
    fingerprint: list[str] = []
    for row in dataset_rows:
        dataset_id = row["id"]
        latest_version = _int(row, "latest_version")
        fingerprint.append(f"{dataset_id}:v{latest_version}")
        version_row = _first_row(await db.execute(_SQL_LATEST_VERSION, (dataset_id,)))
        entry = {
            "dataset_name": row.get("dataset_name"),
            "description": row.get("description"),
            "latest_version": latest_version,
        }
        if version_row:
            entry["file_type"] = version_row.get("file_type")
            entry["original_filename"] = version_row.get("original_filename")
            entry["file_size"] = _default_value(version_row.get("file_size"))
            entry["profile"] = profile_dataset_file(
                version_row.get("stored_path"),
                version_row.get("file_type"),
                version_row.get("original_filename"),
                _int(version_row, "file_size") or None,
            )
        else:
            entry["profile"] = {"profiled": False, "reason": "该数据集暂无文件版本"}
        datasets.append(entry)
    return datasets, sorted(fingerprint)


async def collect_project_stats(project_id: int) -> dict:
    """聚合全项目报告数据：项目本体 + 成员 + 任务 + 材料 + 数据集画像 + 目录/排课/分组。

    独立课题（无 course_id）：catalog / usage / grouping 节显式 None。找不到项目抛 ValueError。
    """
    rows = await db.execute(_SQL_PROJECT, (project_id,))
    if not rows:
        raise ValueError(f"实验项目不存在: id={project_id}")
    project = rows[0]
    course_id = project.get("course_id")

    stats: dict = {
        "project": {k: _default_value(v) for k, v in project.items()},
        "course": None,
        "catalog": None,
        "usage": None,
        "grouping": None,
    }

    member_count = _int(_first_row(await db.execute(_SQL_MEMBER_COUNT, (project_id,))), "member_count")
    stats["members"] = {"member_count": member_count}

    by_status = {row["status"]: _int(row, "cnt") for row in await db.execute(_SQL_TASK_STATUS, (project_id,))}
    total = sum(by_status.values())
    closed = by_status.get("CLOSED", 0)
    effective = total - closed
    accepted = by_status.get("ACCEPTED", 0)
    assigned = _int(_first_row(await db.execute(_SQL_TASK_COVERAGE, (project_id,))), "assigned_student_count")
    stats["tasks"] = {
        "total": total,
        "effective_excluding_closed": effective,
        "by_status": by_status,
        "accepted": accepted,
        "completion_rate": _rate(accepted, effective),
        "overdue": _int(_first_row(await db.execute(_SQL_TASK_OVERDUE, (project_id,))), "overdue_count"),
        "assigned_student_count": assigned,
        "unassigned_student_count": max(member_count - assigned, 0),
    }

    submission_row = _first_row(await db.execute(_SQL_SUBMISSIONS, (project_id,)))
    datasets, fingerprint = await _collect_datasets(project_id)
    stats["materials"] = {
        "submission_count": _int(submission_row, "submission_count"),
        "pending_review_count": _int(submission_row, "pending_review_count"),
        "accepted_submission_count": _int(submission_row, "accepted_submission_count"),
        "rejected_submission_count": _int(submission_row, "rejected_submission_count"),
        "dataset_count": len(datasets),
        "version_count": sum(d["latest_version"] for d in datasets),
    }
    stats["datasets"] = datasets
    stats["dataset_fingerprint"] = fingerprint

    if course_id is not None:
        course_row = _first_row(await db.execute(_SQL_COURSE, (course_id,)))
        if course_row:
            stats["course"] = {k: _default_value(v) for k, v in course_row.items()}
            stats["catalog"] = _catalog_overview(await db.execute(_SQL_CATALOG, (course_id,)))
            stats["usage"] = {
                k: _default_value(_first_row(await db.execute(_SQL_USAGE, (course_row["course_code"],))).get(k))
                for k in ("schedule_count", "student_total")
            }
            per_experiment = await db.execute(
                _SQL_GROUPING_BY_EXPERIMENT, (project_id, project_id, course_id)
            )
            grouped_total = sum(_int(row, "grouped_student_count") for row in per_experiment)
            stats["grouping"] = {
                "member_count": member_count,
                "grouped_student_count": grouped_total,
                "ungrouped_student_count": max(member_count - grouped_total, 0),
                "by_experiment": [
                    {
                        "experiment_code": row.get("experiment_code"),
                        "experiment_name": row.get("experiment_name"),
                        "group_size": _int(row, "group_size"),
                        "group_count": _int(row, "group_count"),
                        "grouped_student_count": _int(row, "grouped_student_count"),
                    }
                    for row in per_experiment
                ],
            }
    return stats


async def generate_report(project_id: int, force: bool = False, cache: ArchiveCache | None = None) -> dict:
    """生成全项目报告：聚合真实数据（含文件画像）→ LLM 六节包装 → 缓存。

    返回 {success, text, cached, stats, generated_at, error}。模型不可用时 success=False
    但 stats 仍返回（前端降级展示确定性统计）。
    """
    try:
        stats = await collect_project_stats(project_id)
    except ValueError as exc:
        return {"success": False, "text": "", "cached": False, "stats": None,
                "generated_at": None, "error": str(exc)}

    key = _cache_key("report", project_id, stats)
    if cache is not None and not force:
        cached_text = cache.get(key)
        if cached_text is not None:
            return {"success": True, "text": cached_text, "cached": True, "stats": stats,
                    "generated_at": cache.get_created_at(key), "error": None}

    from app.config import settings

    result = await generate_text(_REPORT_PROMPT.format(stats=_stats_text(stats)),
                                 timeout=settings.report_timeout)
    generated_at = datetime.now().isoformat(sep=" ", timespec="seconds")
    if not result["success"]:
        return {"success": False, "text": "", "cached": False, "stats": stats,
                "generated_at": None, "error": result["error"]}

    if cache is not None:
        cache.set(key, result["text"])
    return {"success": True, "text": result["text"], "cached": False, "stats": stats,
            "generated_at": generated_at, "error": None}
