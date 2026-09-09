/** 后端 API 通用与实体类型定义（对应 LTPMS_API设计文档 V1.1） */

/** 统一响应包装 */
export interface IResult<T> {
  code: number;
  message: string;
  data: T;
}

/** 分页响应 */
export interface IPageResult<T> {
  records: T[];
  total: number;
  page: number;
  size: number;
}

export type TRole = 'ADMIN' | 'TEACHER';

export interface IUserInfo {
  id: number;
  username: string;
  realName: string;
  role: TRole;
  department?: string;
  isDefaultPwd?: boolean;
  /** 头像：preset:avatar-XX（内置）或 /api/v1/files/avatar/x.png（上传）；空=按用户 ID 取默认 */
  avatar?: string | null;
}

export interface ILoginResult {
  token: string;
  user: IUserInfo;
}

export interface ILaboratory {
  id: number;
  labCode: string;
  labName: string;
  manager?: string;
  deviceCount?: number;
  status: number;
}

export type TSemesterStatus = 'UPCOMING' | 'ACTIVE' | 'ENDED';

export interface ISemester {
  id: number;
  academicYear: string;
  semesterNum: number;
  semesterName: string;
  startDate: string;
  endDate: string;
  status: TSemesterStatus;
}

export interface ISchedule {
  id: number;
  semesterId: number;
  courseCode: string;
  courseName: string;
  totalHours: number;
  teacherId: number;
  teacherName?: string;
  labCode: string;
  classNames: string;
  studentCount: number;
  rawScheduleText?: string;
}

export interface ITeacher {
  id: number;
  username: string;
  realName: string;
  department?: string;
  phone?: string;
  status: number;
  isDefaultPwd: number;
  createdAt: string;
  /** 头像：preset:avatar-XX（内置）或 /api/v1/files/avatar/x.png（上传）；空=按用户 ID 取默认 */
  avatar?: string | null;
}

export interface ICourse {
  id: number;
  courseCode: string;
  courseName: string;
  semesterId: number;
  semesterName?: string;
  teacherId: number;
  teacherName?: string;
  totalHours: number;
  major?: string;
}

export interface IExperiment {
  id: number;
  experimentCode: string;
  courseId: number;
  courseName?: string;
  experimentName: string;
  category: string;
  type: string;
  subject: string;
  requirement: string;
  experimenterType: string;
  groupSize: number;
  hours: number;
  sortOrder: number;
}

export interface IDictItem {
  id: number;
  dictType: string;
  dictLabel: string;
  dictValue: string;
  sortOrder: number;
  status: number;
}

export interface INotification {
  id: number;
  title: string;
  content: string;
  type: string;
  refUrl?: string;
  isRead: number;
  createdAt: string;
}

export interface IQueryHistory {
  id: number;
  question: string;
  generatedSql?: string;
  resultSummary?: string;
  success: number;
  role: string;
  createdAt: string;
}

/** NL2SQL 查询结果 */
export interface INlqResult {
  success: boolean;
  sql?: string;
  columns?: string[];
  rows?: unknown[][];
  suggestions?: string[];
  rowCount?: number;
  executionTime?: number;
  error?: string;
}

/** 报表：分层可见（教师=全校汇总 + 本人明细） */
export interface IReportSummary {
  courseCount: number;
  experimentCount: number;
  totalPersonHours: number;
}

export interface IReportRow {
  labCode?: string;
  labName?: string;
  courseCode?: string;
  courseName?: string;
  teacherName?: string;
  experimentCount?: number;
  personHours?: number;
  [key: string]: unknown;
}

export interface IReportData {
  summary?: IReportSummary;
  detail?: IReportRow[];
  detailScope?: 'ALL' | 'SELF';
}

/** 概览统计 */
/** 方格图单元：一个实体对应一格（used=true 蓝色 / false 灰色） */
export interface IStatCell {
  id?: number;
  code?: string;
  name?: string;
  used?: boolean;
  extra?: string;
}

/** 排课周历单元：一周一格 */
export interface IWeekCell {
  week?: number;
  used?: boolean;
}

export interface IAdminOverview {
  labCount?: number;
  courseCount?: number;
  experimentCount?: number;
  teacherCount?: number;
  semesterName?: string;
  labCells?: IStatCell[];
  courseCells?: IStatCell[];
  experimentCells?: IStatCell[];
  teacherCells?: IStatCell[];
  [key: string]: unknown;
}

export interface ITeacherOverview {
  courseCount?: number;
  experimentCount?: number;
  recentQueries?: IRecentQuery[];
  semesterName?: string;
  myCourseCells?: IStatCell[];
  myExperimentCells?: IStatCell[];
  weekCells?: IWeekCell[];
  [key: string]: unknown;
}

/** 概览-最近查询条目 */
export interface IRecentQuery {
  question: string;
  time: string;
  success?: number;
}
