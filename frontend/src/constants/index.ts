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
} as const;

export const ROLE = {
  ADMIN: 'ADMIN',
  TEACHER: 'TEACHER',
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

/** NL2SQL 示例问题 */
export const NLQ_EXAMPLES = [
  '36-401 实验室本学期开设了哪些实验课程？',
  '统计各实验室的实验课程数量',
  '本学期我开设的实验项目有多少学时？',
] as const;
