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

export type TRole = 'ADMIN' | 'TEACHER' | 'STUDENT';

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
  /** 第 1 周起始日；空视为 start_date */
  week1StartDate?: string | null;
  status: TSemesterStatus;
}

/** 学期周历：周次↔日期映射 */
export interface ISemesterCalendarWeek {
  week: number;
  startDate: string;
  endDate: string;
  holidayNames: string[];
}

export interface ISemesterCalendar {
  semesterId: number;
  semesterName: string;
  startDate: string;
  endDate: string;
  week1StartDate: string;
  configured: boolean;
  totalWeeks: number;
  weeks: ISemesterCalendarWeek[];
}

/** 节假日（逐日一行） */
export interface IHoliday {
  id: number;
  holidayDate: string;
  name: string;
  createdAt?: string;
}

export type TWeekType = 'ALL' | 'ODD' | 'EVEN';

/** 实验室开放时段（与 schedule_time 同构） */
export interface ILabOpening {
  id: number;
  labCode: string;
  semesterId: number;
  dayOfWeek: number;
  startSection: number;
  endSection: number;
  weekRange: string;
  weekType: TWeekType;
  audience: string;
  enabled: number;
  remark?: string | null;
  createdAt?: string;
  updatedAt?: string;
}

/** 占用总览条目 */
export interface IOpenOverviewItem {
  labId: number;
  labCode: string;
  labName: string;
  planned: boolean;
  audience?: string | null;
  openSlotHours: number;
  occupiedSlotHours: number;
  rate: number | null;
  conflictCount: number;
}

export interface ILabOpeningOverview {
  semesterId: number;
  week1StartDate: string;
  totalWeeks: number;
  labs: IOpenOverviewItem[];
}

export type TOpenCellState = 'OCCUPIED' | 'OPEN' | 'NOT_OPEN' | 'CLOSED';

export interface IOpenGridCell {
  dayOfWeek: number;
  section: number;
  state: TOpenCellState;
}

export interface IOpenGridClosedDate {
  date: string;
  name: string;
  type: 'HOLIDAY' | 'EXCEPTION';
  week: number;
  dayOfWeek: number;
}

export interface ILabOpenGrid {
  semesterId: number;
  labCode: string;
  labName: string;
  week1StartDate: string;
  totalWeeks: number;
  week: number;
  weekStartDate: string;
  weekEndDate: string;
  cells: IOpenGridCell[];
  closedDates: IOpenGridClosedDate[];
}

export interface IOpenTodayLab {
  labCode: string;
  labName: string;
  audience?: string | null;
  sectionText: string;
}

export interface ILabOpenToday {
  semesterId: number | null;
  semesterName?: string | null;
  date?: string;
  dayOfWeek?: number;
  weekNo?: number | null;
  inSemester?: boolean;
  holiday?: boolean;
  holidayName?: string | null;
  labs: IOpenTodayLab[];
}

/** 实验室开放例外日（临时闭馆） */
export interface ILabOpenException {
  id: number;
  labCode: string;
  semesterId: number;
  startDate: string;
  endDate: string;
  reason?: string | null;
  createdAt?: string;
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

/** 课表时间槽（后端 SlotView：周次范围 + 实际生效周次） */
export interface IScheduleSlot {
  dayOfWeek: number;
  startSection: number;
  endSection: number;
  weekRange: string;
  weekType: TWeekType;
  weeks: number[];
}

/** 教师课表项（/schedules/mine）：课程信息 + 时间槽 + 修订标记 */
export interface IMyScheduleItem {
  id: number;
  courseCode: string;
  courseName: string;
  totalHours: number;
  teacherId: number;
  teacherName?: string | null;
  labCode: string;
  labName?: string | null;
  classNames: string;
  studentCount: number;
  rawScheduleText?: string | null;
  revisionCount: number;
  slots: IScheduleSlot[];
  createdAt?: string;
}

/** 字段级修订明细 */
export interface IRevisionView {
  fieldName: string;
  oldValue?: string | null;
  newValue?: string | null;
  sourceRole: TRole;
  operatorName: string;
  createdAt: string;
}

/** 课表导入解析预览（含修订冲突统计） */
export interface IImportPreview {
  totalCount: number;
  validCount: number;
  errors: { rowNum: number; message: string }[];
  revisionConflicts: number;
  conflictCourses: string[];
}

/** 课表导入结果 */
export interface IScheduleImportResult {
  successCount: number;
  failedCount: number;
  errors: { rowNum: number; message: string }[];
}

/** 教师课表上传进度（管理员审校页） */
export interface ITeacherUploadStatus {
  teacherId: number;
  username: string;
  realName?: string | null;
  uploaded: boolean;
  scheduleCount: number;
  latestUploadAt?: string | null;
  /** 该教师本学期数据的修订条数（行级 + 课程级） */
  revisionCount: number;
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

/** 通知类型常量（晨报/预警/提醒） */
export type TNotificationType =
  | 'MORNING_BRIEF'
  | 'DEADLINE_WARNING'
  | 'STAGNATION_ALERT'
  | 'REVIEW_PENDING'
  | 'SUBMISSION_ARRIVED'
  | 'DATASET_NEW_VERSION';

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
  /** nlq-service(FastAPI) 实际返回 snake_case；rowCount/executionTime 为历史遗留别名 */
  row_count?: number;
  execution_time?: number;
  rowCount?: number;
  executionTime?: number;
  error?: string;
  /** 非数据问题（制度/流程/资料）：服务端接力顾问的作答与出处轨迹 */
  consult?: IConsultResult | null;
}

/** 顾问接力作答（问题不是数据问题时由服务端同请求转阿实） */
export interface IConsultResult {
  answer: string;
  steps: IAgentStep[];
}

/** Agent 单步工具调用轨迹 */
export interface IAgentStep {
  tool: string;
  arguments?: Record<string, unknown>;
  result?: Record<string, unknown>;
}

/** 档案工具返回的分区块：columns/rows 为原始行，直接上屏不经模型转写 */
export interface IDossierBlock {
  title: string;
  columns: string[];
  rows: unknown[][];
  empty: boolean;
  note?: string | null;
}

/** 档案工具返回结构（双份载荷：display 上屏，model_view 回喂模型） */
export interface IDossierToolResult {
  ok: boolean;
  dimension?: string;
  title?: string;
  count?: number;
  /** 人员解析状态：exact / ambiguous / manager_only / not_found / denied */
  status?: string;
  error?: string;
  display?: { blocks: IDossierBlock[] };
  model_view?: { dimension?: string; count?: number; digest?: string; person_ref?: string };
}

/** knowledge_search 命中的段落：带出处字段，上屏只读不改写 */
export interface IKnowledgeBlock {
  documentId: number;
  title: string;
  heading: string | null;
  /** 来源分类中文名：制度与流程 / 课程资料 */
  source: string;
  courseName: string | null;
  content: string;
  /** 检索得分（余弦相似度 + 关键词加权），仅用于排查，不上屏 */
  score: number;
}

/** 知识库检索工具返回结构（双份载荷：display 带原文上屏，model_view 摘要回喂模型） */
export interface IKnowledgeToolResult {
  ok: boolean;
  count?: number;
  error?: string;
  display?: { blocks: IKnowledgeBlock[] };
  model_view?: { count?: number; matches?: unknown[]; note?: string };
}

/** Agent 多轮对话结果（nlq-service 返回 snake_case） */
export interface IAgentResult {
  answer: string;
  steps: IAgentStep[];
  steps_used?: number;
  error?: string | null;
  /** 完成度断言二次仍缺的维度（工具名），前端把对应区块标灰 */
  missing_dimensions?: string[];
  /** 人名意图路由命中的姓名；「我的档案」入口为当前用户姓名 */
  routed_person_name?: string | null;
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

/* ============================== 课题项目（lab project） ============================== */

/** 课题列表行（行内汇总与矩阵同源 r2-B1） */
export interface IProject {
  id: number;
  projectCode: string;
  projectName: string;
  teacherId: number;
  teacherName?: string;
  courseId?: number | null;
  semesterId?: number | null;
  /** SYSTEM=课程课题（课程容器，承载实验目录）/ USER=独立课题（自建） */
  source: 'SYSTEM' | 'USER';
  courseName?: string | null;
  description?: string | null;
  status: 'ACTIVE' | 'ARCHIVED';
  createdAt: string;
  projectRate: number;
  overdueCount: number;
  memberCount: number;
}

/** 课题成员行 */
export interface IProjectMember {
  studentId: number;
  studentNo: string;
  realName: string;
  className?: string | null;
  major?: string | null;
  source: 'CREATE' | 'MANUAL' | 'COURSE_SNAPSHOT' | 'AUTO_SNAPSHOT';
  joinedAt: string;
}

/** 学生档案检索行 */
export interface IStudent {
  id: number;
  userId: number;
  studentNo: string;
  realName: string;
  className?: string | null;
  major?: string | null;
  status: number;
}

/** 学生建档导入回执 */
export interface IStudentImportResult {
  successCount: number;
  createdAccounts: number;
  errors: { row: number; reason: string }[];
}

/** 任务视图（教师/学生同字段） */
export interface IProjectTask {
  id: number;
  projectId: number;
  title: string;
  description?: string | null;
  assigneeStudentId: number;
  assigneeName?: string | null;
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'SUBMITTED' | 'REJECTED' | 'ACCEPTED' | 'CLOSED';
  deadline?: string | null;
  overdue: boolean;
  datasetId?: number | null;
  createdAt: string;
  updatedAt?: string | null;
}

/** 任务提交记录（半透明：仅本人/教师可见） */
export interface ITaskSubmission {
  id: number;
  taskId: number;
  submitterStudentId: number;
  submitterName?: string | null;
  content: string;
  datasetVersionId?: number | null;
  submittedAt: string;
  reviewComment?: string | null;
  reviewedAt?: string | null;
}

/** 学生「我的任务」按课题分组 */
export interface IMyTaskGroup {
  projectId: number;
  projectName: string;
  tasks: IProjectTask[];
}

/** 数据集视图 */
export interface IDataset {
  id: number;
  projectId: number;
  datasetName: string;
  description?: string | null;
  latestVersion: number;
  createdById: number;
  createdByName?: string | null;
  createdAt: string;
}

/** 数据集版本视图 */
export interface IDatasetVersion {
  id: number;
  datasetId: number;
  versionNo: number;
  originalFilename: string;
  fileSize?: number | null;
  fileType?: string | null;
  uploaderId: number;
  uploaderName?: string | null;
  note?: string | null;
  createdAt: string;
}

/** 版本预览载荷（csv/txt/xlsx） */
export interface IDatasetPreview {
  type: string;
  rows: string[][];
  truncated: boolean;
  message?: string | null;
}

/** 进度矩阵格子 */
export interface IProgressCell {
  taskId: number;
  taskTitle: string;
  status: IProjectTask['status'];
  overdue: boolean;
}

/** 进度矩阵学生行 */
export interface IProgressStudentRow {
  studentId: number;
  name: string;
  rate: number;
  cells: IProgressCell[];
}

/** 进度矩阵任务列 */
export interface IProgressTaskColumn {
  taskId: number;
  title: string;
  status: IProjectTask['status'];
  deadline?: string | null;
  overdue: boolean;
}

/** 单课题完整统计（列表/详情/矩阵同源 r2-B1） */
export interface IProjectStats {
  projectRate: number;
  total: number;
  accepted: number;
  overdueCount: number;
  memberCount: number;
  students: IProgressStudentRow[];
  tasks: IProgressTaskColumn[];
}
