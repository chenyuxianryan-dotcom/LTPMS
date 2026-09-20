/** 全局常量：错误码、字典类型、角色等 */

/** 业务错误码（对应 API 文档 §2.1） */
export const ERROR_CODE = {
  ACCOUNT_LOCKED: 41001,
  SCHEDULE_TEACHER_NOT_FOUND: 42001,
  SCHEDULE_LAB_NOT_FOUND: 42002,
  SCHEDULE_MULTI_TEACHER: 42003,
  PREVIOUS_SEMESTER_EMPTY: 43001,
  NLQ_UNSAFE: 44001,
  NLQ_PERMISSION_FAILED: 44002,
  NLQ_MODEL_TIMEOUT: 44003,
  NLQ_SERVICE_UNAVAILABLE: 44004,
  /** 同名数据集已存在 → 前端弹框二选一（挂新版本 / 换名新建，r2-C4） */
  DATASET_NAME_CONFLICT: 46001,
} as const;

export const ROLE = {
  ADMIN: 'ADMIN',
  TEACHER: 'TEACHER',
  STUDENT: 'STUDENT',
} as const;

/** 字典类型（experiment 枚举字段引用） */
export const DICT_TYPES = [
  { value: 'category', label: '实验类别' },
  { value: 'type', label: '实验类型' },
  { value: 'subject', label: '所属学科' },
  { value: 'requirement', label: '实验要求' },
  { value: 'experimenter_type', label: '实验者类别' },
] as const;

export type TDictType = (typeof DICT_TYPES)[number]['value'];

/** 学期状态展示映射 */
export const SEMESTER_STATUS_MAP: Record<string, { label: string; tagType: 'success' | 'primary' | 'info' }> = {
  UPCOMING: { label: '未开始', tagType: 'info' },
  ACTIVE: { label: '进行中', tagType: 'success' },
  ENDED: { label: '已结束', tagType: 'primary' },
};

/** 星期标签（1=周一 .. 7=周日） */
export const WEEKDAY_LABELS = ['一', '二', '三', '四', '五', '六', '日'] as const;

/** 开放对象固定枚举（与后端一致） */
export const AUDIENCE_OPTIONS = ['全校', '本院', '指定专业', '教师专用'] as const;

/** 网格节次 1-12 */
export const SECTION_OPTIONS = Array.from({ length: 12 }, (_, i) => i + 1);

/** 周类型展示映射 */
export const WEEK_TYPE_MAP: Record<string, string> = {
  ALL: '全周',
  ODD: '单周',
  EVEN: '双周',
};

/** 修订字段展示映射（课表行 + 课程聚合行共用） */
export const REVISION_FIELD_LABELS: Record<string, string> = {
  labCode: '上课地点',
  classNames: '上课班级',
  studentCount: '选课人数',
  rawScheduleText: '上课时间',
  courseName: '课程名称',
  major: '专业',
  totalHours: '总学时',
};

/** NL2SQL 示例问题 */
export const NLQ_EXAMPLES = [
  '36-401 实验室本学期开设了哪些实验课程？',
  '统计各实验室的实验课程数量',
  '本学期我开设的实验项目有多少学时？',
] as const;

/** 课题任务状态展示映射（矩阵配色：绿=已验收 黄=进行中/待办 红=逾期 灰=关闭） */
export const TASK_STATUS_MAP: Record<string, { label: string; tagType: 'success' | 'warning' | 'danger' | 'info' | 'primary' }> = {
  NOT_STARTED: { label: '待领取', tagType: 'info' },
  IN_PROGRESS: { label: '进行中', tagType: 'warning' },
  SUBMITTED: { label: '待验收', tagType: 'primary' },
  REJECTED: { label: '被驳回', tagType: 'danger' },
  ACCEPTED: { label: '已验收', tagType: 'success' },
  CLOSED: { label: '已关闭', tagType: 'info' },
};

/** 课题状态展示映射 */
export const PROJECT_STATUS_MAP: Record<string, { label: string; tagType: 'success' | 'info' }> = {
  ACTIVE: { label: '进行中', tagType: 'success' },
  ARCHIVED: { label: '已归档', tagType: 'info' },
};

/** 课题成员来源展示映射 */
export const MEMBER_SOURCE_MAP: Record<string, string> = {
  CREATE: '导入建档',
  MANUAL: '学生库勾选',
  COURSE_SNAPSHOT: '课程名单快照',
  AUTO_SNAPSHOT: '课程名单带入',
};

/** 课题来源展示映射（M-Q4 Segmented：课程课题 / 独立课题） */
export const PROJECT_SOURCE_MAP: Record<string, { label: string; tagType: 'primary' | 'info' }> = {
  SYSTEM: { label: '课程课题', tagType: 'primary' },
  USER: { label: '独立课题', tagType: 'info' },
};

/** 任务修订字段展示映射（lab_task 留痕） */
export const TASK_REVISION_FIELD_LABELS: Record<string, string> = {
  title: '任务标题',
  description: '任务说明',
  deadline: '截止时间',
  datasetId: '关联数据集',
};

/** 学生角色可见路由（守卫默认拒绝的白名单；页面 name 口径）
 *  合并后学生可见 /experiments（列表仅本人课程容器）与容器详情 */
export const STUDENT_ROUTE_NAMES = ['experiments', 'experiment-detail', 'my-tasks'] as const;
