<template>
  <div class="nlq-page">
    <!-- 主内容区 -->
    <div class="nlq-page__main" :class="{ 'nlq-page__main--active': result || dossier || imageResult || consult || busy }">
      <!-- 查询台（空态居中 hero；查询中/有结果时收起，输入框沉底） -->
      <div class="asy-terminal">
        <!-- 终端机头 -->
        <div class="asy-terminal__head">
          <span class="asy-terminal__lamp" aria-hidden="true"></span>
          <span class="asy-terminal__code">LTPMS · ASY-01 智能查询终端</span>
          <span class="asy-terminal__scope" :title="isAdmin ? '管理员：可查询全校数据' : '教师：自动注入权限，仅可见本人任教数据'">
            {{ isAdmin ? 'SCOPE:ALL' : 'SCOPE:SELF' }}
          </span>
        </div>

        <!-- 问候语 -->
        <div class="asy-terminal__greeting">{{ greeting }}，我是阿实</div>
        <div class="asy-terminal__sub">实验室数据随口问 · 系统自动注入数据权限（NL2SQL，只读）· 制度与资料类问题自动转阿实带出处作答</div>

        <!-- Composer：查询按钮内嵌；图片可粘贴/拖拽/选择（服务端按魔数再校验） -->
        <div
          class="asy-composer"
          :class="{ 'asy-composer--busy': busy, 'asy-composer--drop': dragActive }"
          @paste="handlePaste"
          @dragover.prevent="dragActive = true"
          @dragleave="dragActive = false"
          @drop.prevent="handleDrop"
        >
          <span class="asy-composer__prompt" aria-hidden="true">❯</span>
          <el-input
            ref="inputRef"
            v-model="question"
            type="textarea"
            :rows="2"
            resize="none"
            placeholder="输入问题，例如：这学期我开设了哪些实验课程？（也可贴一张图片再问）"
            class="asy-composer__input"
            @keydown.ctrl.enter="handleSubmit"
            @keydown.meta.enter="handleSubmit"
          />

          <!-- 附图缩略图（提交成功后清空；改道/建议追问会从上一张图复原） -->
          <div v-if="attached" class="asy-attach">
            <img class="asy-attach__thumb" :src="attached.previewUrl" :alt="attached.name" />
            <div class="asy-attach__meta">
              <span class="asy-attach__name">{{ attached.name }}</span>
              <span class="asy-attach__size">{{ formatImageSize(attached.size) }}</span>
            </div>
            <button type="button" class="asy-attach__remove" title="移除图片" @click="clearAttached">✕</button>
          </div>

          <div class="asy-composer__bar">
            <span class="asy-composer__hint">CTRL+⏎ 执行 · 可贴图提问 · 只读查询 · 强制 LIMIT</span>
            <div class="asy-composer__actions">
              <input
                ref="fileInputRef"
                type="file"
                class="asy-composer__file"
                :accept="IMAGE_ACCEPT"
                @change="handleFilePicked"
              />
              <el-button
                class="asy-composer__attach"
                :disabled="busy"
                title="支持粘贴 / 拖拽 / 选择图片：jpg、png、webp，单张不超过 10MB"
                @click="pickFile"
              >
                <el-icon><Picture /></el-icon>附图
              </el-button>
              <el-button
                class="asy-composer__mine"
                :loading="dossierLoading"
                :disabled="querying || imageLoading"
                @click="handleMyDossier"
              >
                我的档案
              </el-button>
              <el-button
                class="asy-composer__exec"
                :class="{ 'asy-composer__exec--ready': canSubmit }"
                :loading="querying || imageLoading"
                :disabled="dossierLoading || !canSubmit"
                @click="handleSubmit"
              >
                {{ querying || imageLoading ? '检索中' : '查询 ⏎' }}
              </el-button>
            </div>
          </div>
          <span class="asy-composer__scan" aria-hidden="true"></span>
        </div>

        <!-- 提示词：垂直渐虚堆叠（上清晰 → 下模糊，hover 恢复） -->
        <div class="asy-examples">
          <button
            v-for="(example, index) in NLQ_EXAMPLES"
            :key="example"
            type="button"
            class="asy-examples__card"
            :style="{ '--stagger': index }"
            @click="question = example"
          >
            <span class="asy-examples__index">{{ String(index + 1).padStart(2, '0') }}</span>
            <span class="asy-examples__text">{{ example }}</span>
            <span class="asy-examples__load">载入 ↵</span>
          </button>
        </div>

        <!-- 空态引导行（无结果时显示） -->
        <div v-if="!result && !querying && !consult" class="asy-boot">
          <span class="asy-boot__chevron">❯</span>
          <span class="asy-boot__text">等待指令</span>
          <span class="asy-boot__caret" aria-hidden="true"></span>
        </div>
      </div>

      <!-- 附图识图：先出「图片理解摘要」，再出分路结果（课表草稿 / 资料出处 / 通知转述） -->
      <el-card v-if="imageResult" class="nlq-page__result-card" shadow="never">
        <!-- 低置信/通道异常：不给猜测性回答，只给拍摄建议与文字提问引导 -->
        <div v-if="!imageResult.success" class="nlq-page__vision-fail">
          <span class="nlq-page__vision-fail-title">{{ imageResult.error ?? '没有看清图片内容' }}</span>
          <ul v-if="imageResult.suggestions.length > 0" class="nlq-page__vision-tips">
            <li v-for="tip in imageResult.suggestions" :key="tip">{{ tip }}</li>
          </ul>
        </div>

        <template v-else>
          <div class="nlq-page__result-meta">
            <span class="nlq-page__meta-dot" aria-hidden="true"></span>
            <span class="nlq-page__meta-text">图片理解摘要 · {{ routeLabel }}</span>
            <span v-if="imageResult.low_confidence" class="nlq-page__meta-text nlq-page__meta-text--dim">置信偏低</span>
            <span class="nlq-page__meta-scope">{{ isAdmin ? 'SCOPE:ALL' : 'SCOPE:SELF' }}</span>
          </div>

          <p v-if="imageResult.digest" class="nlq-page__digest">{{ imageResult.digest }}</p>

          <!-- 路由判定可被人工推翻（F3-3）：换一类重新解析同一张图 -->
          <div v-if="imageResult.route !== 'unknown'" class="nlq-page__reroute">
            <span class="nlq-page__reroute-label">改道重解析：</span>
            <el-button
              v-for="option in REROUTE_OPTIONS"
              :key="option.value"
              size="small"
              text
              type="primary"
              :disabled="imageResult.route === option.value || imageLoading"
              @click="handleReroute(option.value)"
            >
              {{ option.label }}
            </el-button>
          </div>

          <p v-if="imageResult.answer" class="nlq-page__dossier-answer">{{ imageResult.answer }}</p>

          <!-- 课表链路：查询不自动写回，先给「可生成导入草稿」提示；教师点开才进草稿确认卡 -->
          <template v-if="imageResult.route === 'schedule' && imageResult.rows.length > 0">
            <div v-if="isTeacher" class="nlq-page__draft-entry">
              <span class="nlq-page__draft-hint">
                检测到 {{ imageResult.rows.length }} 条课程条目{{ draftOpen ? '' : '，可生成导入草稿' }}
              </span>
              <el-button v-if="!draftOpen" size="small" type="primary" @click="draftOpen = true">生成导入草稿</el-button>
            </div>
            <el-collapse v-else class="nlq-page__sql-collapse">
              <el-collapse-item name="rows">
                <template #title>
                  <span class="font-mono nlq-page__sql-title">查看识别到的 {{ imageResult.rows.length }} 条课程条目（草稿写回仅教师可用）</span>
                </template>
                <ScheduleDraftTable :rows="imageResult.rows" readonly />
              </el-collapse-item>
            </el-collapse>
          </template>

          <!-- 资料页链路：图内文字 + 知识库出处（回答正文已区分「来自你的图片 / 来自知识库」） -->
          <template v-if="imageResult.route === 'document' && imageResult.document_text">
            <el-collapse class="nlq-page__sql-collapse">
              <el-collapse-item name="doc">
                <template #title>
                  <span class="font-mono nlq-page__sql-title">识别到的图内文字</span>
                </template>
                <pre class="sql-block">{{ imageResult.document_text }}</pre>
              </el-collapse-item>
            </el-collapse>
            <KnowledgeSources :steps="imageResult.steps" />
          </template>

          <!-- 通知链路：结构化转述（标题/时间/地点/事项），不写入任何数据 -->
          <div v-if="imageResult.route === 'notice' && noticeVisible" class="nlq-page__notice">
            <div v-if="imageResult.notice.title" class="nlq-page__notice-title">{{ imageResult.notice.title }}</div>
            <div class="nlq-page__notice-fields">
              <span v-if="imageResult.notice.time">时间：{{ imageResult.notice.time }}</span>
              <span v-if="imageResult.notice.place">地点：{{ imageResult.notice.place }}</span>
            </div>
            <ul v-if="imageResult.notice.items?.length" class="nlq-page__notice-items">
              <li v-for="(item, index) in imageResult.notice.items" :key="index">{{ item }}</li>
            </ul>
          </div>

          <div v-if="imageResult.suggestions.length > 0" class="nlq-page__suggestions">
            <span class="nlq-page__suggestions-label">建议追问：</span>
            <el-tag
              v-for="suggestion in imageResult.suggestions"
              :key="suggestion"
              class="nlq-page__example-tag"
              effect="plain"
              @click="handleImageSuggestion(suggestion)"
            >
              {{ suggestion }}
            </el-tag>
          </div>
        </template>
      </el-card>

      <!-- 草稿确认卡：写回只在教师点「确认写入」后发生，模型输出永不直接入库 -->
      <el-card v-if="isTeacher && draftOpen && imageResult?.rows.length" class="nlq-page__result-card" shadow="never">
        <div class="nlq-page__result-meta">
          <span class="nlq-page__meta-dot" aria-hidden="true"></span>
          <span class="nlq-page__meta-text">导入草稿 · {{ imageResult.rows.length }} 行（可逐格修正后确认写入）</span>
          <span class="nlq-page__meta-scope">WRITE:DRAFT</span>
        </div>

        <ScheduleDraftTable :rows="imageResult.rows" />

        <div class="nlq-page__draft-actions">
          <el-button
            type="primary"
            size="small"
            :loading="draftConfirming"
            :disabled="draftWritten !== null"
            @click="handleDraftConfirm"
          >
            确认写入
          </el-button>
          <span class="nlq-page__draft-note">写回走课表导入的行级校验；教师条目强制归属本人</span>
        </div>

        <ul v-if="draftErrors.length > 0" class="nlq-page__draft-errors">
          <li v-for="error in draftErrors" :key="`${error.rowNum}-${error.message}`">
            第 {{ error.rowNum }} 行：{{ error.message }}
          </li>
        </ul>
        <p v-if="draftWritten !== null" class="nlq-page__draft-done">
          已写入 {{ draftWritten }} 行，可到课表页查看。
        </p>
      </el-card>

      <!-- 结果区（逻辑与数据结构保持不变，仅外壳风格化） -->
      <el-card v-if="result" class="nlq-page__result-card" shadow="never">
        <!-- 失败 -->
        <el-result
          v-if="!result.success"
          icon="warning"
          :title="result.error ?? '该问题无法安全执行'"
          :sub-title="result.suggestions?.[0]"
        />

        <!-- 成功 -->
        <template v-else>
          <div class="nlq-page__result-meta">
            <span class="nlq-page__meta-dot" aria-hidden="true"></span>
            <span class="nlq-page__meta-text">返回 {{ result.row_count ?? result.rowCount ?? 0 }} 条记录</span>
            <span v-if="(result.execution_time ?? result.executionTime) != null" class="nlq-page__meta-text nlq-page__meta-text--dim">
              耗时 {{ ((result.execution_time ?? result.executionTime ?? 0) / 1000).toFixed(1) }}s
            </span>
            <span class="nlq-page__meta-scope">{{ isAdmin ? 'SCOPE:ALL' : 'SCOPE:SELF' }}</span>
          </div>

          <el-collapse v-if="result.sql" class="nlq-page__sql-collapse">
            <el-collapse-item name="sql">
              <template #title>
                <span class="font-mono nlq-page__sql-title">生成的 SQL · 自动注入数据权限</span>
              </template>
              <pre class="sql-block">{{ result.sql }}</pre>
            </el-collapse-item>
          </el-collapse>

          <el-table
            v-if="tableVisible"
            :data="tableRows"
            border
            stripe
            max-height="420"
            class="nlq-page__result-table"
          >
            <el-table-column
              v-for="(col, index) in result.columns"
              :key="String(col)"
              :prop="`c${index}`"
              :label="String(col)"
              min-width="120"
            />
          </el-table>
          <div v-else class="nlq-page__empty-hint">
            未查询到数据：请核对姓名、编号拼写与学期范围（实验室编号形如 36-401）。
          </div>

          <!-- 继续追问 -->
          <div v-if="result.suggestions && result.suggestions.length > 0" class="nlq-page__suggestions">
            <span class="nlq-page__suggestions-label">继续追问：</span>
            <el-tag
              v-for="suggestion in result.suggestions"
              :key="suggestion"
              class="nlq-page__example-tag"
              effect="plain"
              @click="question = suggestion"
            >
              {{ suggestion }}
            </el-tag>
          </div>
        </template>
      </el-card>

      <!-- 顾问作答：非数据问题（制度/流程/资料）转阿实，出处随答案上屏 -->
      <el-card v-if="consult" class="nlq-page__result-card" shadow="never">
        <div class="nlq-page__result-meta">
          <span class="nlq-page__meta-dot" aria-hidden="true"></span>
          <span class="nlq-page__meta-text">阿实顾问作答 · 「{{ consult.question }}」不是数据问题，已转知识库检索</span>
          <span class="nlq-page__meta-scope">{{ isAdmin ? 'SCOPE:ALL' : 'SCOPE:SELF' }} · KB</span>
        </div>

        <p class="nlq-page__dossier-answer">{{ consult.answer }}</p>

        <KnowledgeSources :steps="consult.steps" />
      </el-card>

      <!-- 我的档案：服务端锁定 person_ref 为当前用户，跳过人员解析一轮 -->
      <el-card v-if="dossier" class="nlq-page__result-card" shadow="never">
        <div class="nlq-page__result-meta">
          <span class="nlq-page__meta-dot" aria-hidden="true"></span>
          <span class="nlq-page__meta-text">
            本人档案{{ dossier.name ? ` · ${dossier.name}` : '' }}
          </span>
          <span v-if="dossier.steps.length > 0" class="nlq-page__meta-text nlq-page__meta-text--dim">
            {{ dossier.steps.length }} 次取数
          </span>
          <span class="nlq-page__meta-scope">SCOPE:SELF · LOCKED</span>
        </div>

        <p v-if="dossier.answer" class="nlq-page__dossier-answer">{{ dossier.answer }}</p>

        <PersonDossier :steps="dossier.steps" />
      </el-card>
    </div>

    <!-- 历史侧栏（结构保持原样） -->
    <div class="nlq-page__history">
      <div class="nlq-page__history-title">
        <span>查询历史</span>
        <el-button link type="primary" size="small" @click="loadHistory">刷新</el-button>
      </div>
      <el-empty v-if="history.length === 0" description="暂无记录" :image-size="60" />
      <el-timeline v-else class="nlq-page__timeline">
        <el-timeline-item
          v-for="item in history"
          :key="item.id"
          :type="item.success === 1 ? 'primary' : 'danger'"
          :timestamp="item.createdAt"
        >
          <div class="nlq-page__history-item">
            <div class="nlq-page__history-question" @click="question = item.question">{{ item.question }}</div>
            <div class="nlq-page__history-actions">
              <span class="nlq-page__history-summary">{{ item.resultSummary }}</span>
              <el-button link type="danger" size="small" @click="handleDeleteHistory(item.id)">删除</el-button>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Picture } from '@element-plus/icons-vue';
import KnowledgeSources from '@/components/KnowledgeSources.vue';
import PersonDossier from '@/components/PersonDossier.vue';
import ScheduleDraftTable from '@/components/ScheduleDraftTable.vue';
import { apiDeleteNlqHistory, apiGetNlqHistory, apiNlqAgent, apiNlqImageQuery, apiNlqQuery, type INlqImageResult } from '@/api/nlq';
import { apiGetCurrentSemester } from '@/api/semester';
import { ERROR_CODE, NLQ_EXAMPLES } from '@/constants';
import { useUserStore } from '@/store/user';
import type { IAgentStep, INlqResult, IQueryHistory } from '@/types/api';
import {
  IMAGE_ACCEPT,
  formatImageSize,
  readImageFile,
  releaseImage,
  type IAttachedImage,
} from '@/utils/imageAttach';
import { confirmScheduleDraft } from '@/utils/scheduleDraft';
import type { IBizError } from '@/utils/request';

const userStore = useUserStore();
const isAdmin = computed(() => userStore.isAdmin);
/** 草稿写回仅教师（管理员沿用全局只读边界，学生只读提问） */
const isTeacher = computed(() => userStore.user?.role === 'TEACHER');

/** 改道目标（F3-3：判定可被人工推翻） */
const REROUTE_OPTIONS = [
  { value: 'schedule', label: '课表' },
  { value: 'document', label: '资料页' },
  { value: 'notice', label: '通知' },
];

const inputRef = ref<{ focus: () => void } | null>(null);
const question = ref('');
const querying = ref(false);
const result = ref<INlqResult | null>(null);
const history = ref<IQueryHistory[]>([]);

/** 「我的档案」结果：steps 内即档案工具的双份载荷（display 上屏） */
const dossierLoading = ref(false);
const dossier = ref<{ steps: IAgentStep[]; answer: string; name: string | null } | null>(null);

/** 顾问作答：服务端判定「非数据问题」时同请求接力阿实（知识库检索带出处） */
const consult = ref<{ question: string; answer: string; steps: IAgentStep[] } | null>(null);

/** 附图：待提交的图片（提交成功后清空输入区） */
const attached = ref<IAttachedImage | null>(null);
/** 上一张已提交的图：供「改道」与建议追问复用；只活在本次会话，刷新即失效 */
const lastImage = ref<IAttachedImage | null>(null);
const lastImageQuestion = ref('');
const imageLoading = ref(false);
const imageResult = ref<INlqImageResult | null>(null);
const dragActive = ref(false);
const fileInputRef = ref<HTMLInputElement | null>(null);

/** 草稿确认卡：查询不自动写回，教师点开草稿卡并确认后才调课表导入 */
const draftOpen = ref(false);
const draftConfirming = ref(false);
const draftWritten = ref<number | null>(null);
const draftErrors = ref<{ rowNum: number; message: string }[]>([]);

const busy = computed(() => querying.value || dossierLoading.value || imageLoading.value);
const canSubmit = computed(() => Boolean(question.value.trim() || attached.value));

const ROUTE_LABELS: Record<string, string> = {
  schedule: '已当作课表解析',
  document: '已当作资料页解析',
  notice: '已当作通知转述',
};
const routeLabel = computed(() => ROUTE_LABELS[imageResult.value?.route ?? ''] ?? '未能判定图片类型');

/** 通知链路：结构化转述有内容才渲染（避免空壳区块） */
const noticeVisible = computed(() => {
  const notice = imageResult.value?.notice;
  return Boolean(notice?.title || notice?.time || notice?.place || notice?.items?.length);
});

/** 时间感知问候（Claude 式 temporal greeting，让终端有人味） */
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '夜深了';
  if (hour < 12) return '上午好';
  if (hour < 14) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

/** 后端 rows 是二维数组，转成 el-table 需要的对象行 */
const tableRows = computed(() => {
  const rows = result.value?.rows ?? [];
  return rows.map((row) => {
    const rowObject: Record<string, unknown> = {};
    row.forEach((cell, index) => {
      rowObject[`c${index}`] = cell;
    });
    return rowObject;
  });
});

/** 0 行结果用提示文案替代空表，避免用户面对空框猜测原因 */
const tableVisible = computed(() => {
  const res = result.value;
  const rowCount = res?.row_count ?? res?.rowCount ?? 0;
  return rowCount > 0 && (res?.columns?.length ?? 0) > 0;
});

/** 结果区互斥：新一轮提交清空上一轮所有结果卡（草稿只活在当轮会话内） */
function resetResults(): void {
  result.value = null;
  dossier.value = null;
  consult.value = null;
  imageResult.value = null;
  draftOpen.value = false;
  draftWritten.value = null;
  draftErrors.value = [];
}

/** 智能查询各入口共用的业务错误提示（其余错误码由 request 拦截器统一提示） */
function showQueryError(error: unknown): void {
  const bizError = error as IBizError;
  if (bizError.code === ERROR_CODE.NLQ_SERVICE_UNAVAILABLE) {
    ElMessage.warning('智能查询服务未启动或连接失败');
  } else if (bizError.code === ERROR_CODE.NLQ_MODEL_TIMEOUT) {
    ElMessage.warning('AI 服务暂时不可用，请稍后重试');
  } else if (bizError.code === 429) {
    ElMessage.warning('查询过于频繁（每分钟最多 10 次），请稍后再试');
  }
}

/** 执行入口：有图先走识图链路（纯图无配文也允许），否则走纯文字查询 */
async function handleSubmit(): Promise<void> {
  if (busy.value) return;
  if (attached.value) {
    await submitImage(attached.value, question.value.trim());
    return;
  }
  await handleQuery();
}

async function submitImage(image: IAttachedImage, asked: string, forceRoute?: string): Promise<void> {
  imageLoading.value = true;
  resetResults();
  try {
    const res = await apiNlqImageQuery(image.base64, asked, forceRoute);
    imageResult.value = res;
    if (lastImage.value && lastImage.value !== image) {
      releaseImage(lastImage.value);
    }
    lastImage.value = image;
    lastImageQuestion.value = asked;
    // 提交成功即清空输入区（图片本体留在 lastImage 供改道复用，故不释放预览地址）
    attached.value = null;
    question.value = '';
    // 纯图提交的课表提取：识别结果本身就是交付物，直接开草稿卡（写回仅教师，管理员只读展示）；
    // 配文查询时只给「可生成导入草稿」提示——查询不自动触发写回承诺
    draftOpen.value = res.success && res.route === 'schedule' && !asked && isTeacher.value;
    await loadHistory();
  } catch (error) {
    // 提交失败：图片与配文保留在输入区，可直接重试
    showQueryError(error);
  } finally {
    imageLoading.value = false;
  }
}

/** 改道：同一张图按指定类型重新解析，覆盖展示上一轮结果 */
async function handleReroute(route: string): Promise<void> {
  if (!lastImage.value || imageLoading.value) return;
  await submitImage(lastImage.value, lastImageQuestion.value, route);
}

/** 建议追问：把上一张图带回输入区再提交（通知/资料类追问要靠图内文字作答） */
async function handleImageSuggestion(suggestion: string): Promise<void> {
  if (imageLoading.value) return;
  question.value = suggestion;
  if (lastImage.value) {
    attached.value = lastImage.value;
    await submitImage(lastImage.value, suggestion);
    return;
  }
  await handleQuery();
}

/** 确认写入：走课表导入的行级校验与归属规则（部分成功口径，失败行逐行回显） */
async function handleDraftConfirm(): Promise<void> {
  const rows = imageResult.value?.rows ?? [];
  if (rows.length === 0 || draftConfirming.value) return;
  draftConfirming.value = true;
  draftErrors.value = [];
  try {
    const semester = await apiGetCurrentSemester();
    if (!semester?.id) {
      ElMessage.warning('未找到当前学期，暂不能写入课表');
      return;
    }
    const result = await confirmScheduleDraft(semester.id, rows);
    if (!result) return; // 用户在覆盖确认处取消：留在草稿编辑态
    draftWritten.value = result.successCount ?? 0;
    draftErrors.value = result.errors ?? [];
  } catch {
    // 错误由 request 拦截器统一提示（403 亦在此列）
  } finally {
    draftConfirming.value = false;
  }
}

function pickFile(): void {
  fileInputRef.value?.click();
}

async function handleFilePicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = ''; // 同一文件可再次选择
  if (file) {
    await attachFile(file);
  }
}

/** 粘贴附图：剪贴板里没有图片时交回输入框原生文本粘贴 */
async function handlePaste(event: ClipboardEvent): Promise<void> {
  const file = Array.from(event.clipboardData?.files ?? [])[0];
  if (!file) return;
  event.preventDefault();
  await attachFile(file);
}

async function handleDrop(event: DragEvent): Promise<void> {
  dragActive.value = false;
  const file = event.dataTransfer?.files?.[0];
  if (file) {
    await attachFile(file);
  }
}

async function attachFile(file: File): Promise<void> {
  if (imageLoading.value) return;
  try {
    const image = await readImageFile(file, { preview: true });
    releaseImage(attached.value);
    attached.value = image;
  } catch (error) {
    // 格式/体积在提交前拦截，不发起请求（发送端魔数校验仍是最终把关）
    ElMessage.warning((error as Error).message);
  }
}

function clearAttached(): void {
  releaseImage(attached.value);
  attached.value = null;
}

async function handleQuery(): Promise<void> {
  const asked = question.value.trim();
  if (!asked || busy.value) return;
  querying.value = true;
  resetResults();
  try {
    const res = await apiNlqQuery(asked);
    // 非数据问题由服务端同请求接力顾问（consult 字段），前端只负责上屏
    if (res.consult) {
      consult.value = {
        question: asked,
        answer: res.consult.answer || '（未获得回复）',
        steps: res.consult.steps ?? [],
      };
      question.value = '';
    } else {
      result.value = res;
      if (res.success) {
        // 成功即清空输入框；失败保留原文，避免报错时把用户输入吞掉
        question.value = '';
      }
    }
    await loadHistory();
  } catch (error) {
    // 44001/44002（无法安全转换/权限注入失败）等由拦截器按错误码提示
    showQueryError(error);
  } finally {
    querying.value = false;
  }
}

/** 「我的档案」：服务端把 person_ref 锁成当前登录用户，教师/管理员同一入口 */
async function handleMyDossier(): Promise<void> {
  if (busy.value) return;
  dossierLoading.value = true;
  resetResults();
  try {
    const res = await apiNlqAgent('查看我的完整档案', undefined, { selfDossier: true });
    if (!res.steps?.length && !res.answer) {
      ElMessage.warning('档案生成失败，请稍后重试');
      return;
    }
    dossier.value = {
      steps: res.steps ?? [],
      answer: res.answer,
      name: res.routed_person_name ?? null,
    };
  } catch (error) {
    const bizError = error as IBizError;
    if (bizError.code === ERROR_CODE.NLQ_SERVICE_UNAVAILABLE) {
      ElMessage.warning('智能查询服务未启动或连接失败');
    } else if (bizError.code === ERROR_CODE.NLQ_MODEL_TIMEOUT || bizError.code === ERROR_CODE.NLQ_UNSAFE) {
      ElMessage.warning('AI 服务暂时不可用，请稍后重试');
    } else if (bizError.code === 429) {
      ElMessage.warning('查询过于频繁（每分钟最多 10 次），请稍后再试');
    }
  } finally {
    dossierLoading.value = false;
  }
}

async function loadHistory(): Promise<void> {
  const pageResult = await apiGetNlqHistory({ page: 1, size: 20 });
  history.value = pageResult.records;
}

async function handleDeleteHistory(id: number): Promise<void> {
  await ElMessageBox.confirm('确定删除这条查询历史吗？', '提示', { type: 'warning' });
  await apiDeleteNlqHistory(id);
  ElMessage.success('已删除');
  await loadHistory();
}

onMounted(() => {
  void loadHistory().catch(() => undefined);
  inputRef.value?.focus();
});

onBeforeUnmount(() => {
  releaseImage(attached.value);
  releaseImage(lastImage.value);
});
</script>

<style scoped lang="scss">
/**
 * ASY-01 检索终端 —— 与阿实悬浮面板同源的磷光绿终端语言。
 * 磷光绿色板为页面局部变量（先例：NlqWidget 悬浮面板，tokens.css 未收录）；
 * 其余间距/圆角/阴影/文字色一律引用 design tokens。
 */
$phosphor-bg: #0c1f16;      // 终端底（同 NlqWidget 步骤条）
$phosphor-border: #1e3a2b;  // 终端描边
$phosphor-text: #6ee7a0;    // 磷光绿主文字
$phosphor-dim: #3f8f66;     // 暗磷光（次要文字）
$phosphor-bright: #a7f3c9;  // 高亮磷光（hover）

.nlq-page {
  display: flex;
  gap: 0;
  height: 100%;

  &__main {
    flex: 1;
    min-width: 0;
    padding: var(--content-pad);
    overflow-y: auto;
    display: flex;
    flex-direction: column;
  }

  /* ===== 查询台容器：空态 hero；激活态退化为底部输入坞（见 __main--active） ===== */
  .asy-terminal {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 100%;
    max-width: 720px;
    margin: 0 auto;
    padding-top: var(--space-16);
  }

  /* ===== 终端机头 ===== */
  .asy-terminal__head {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-bottom: var(--space-8);
    font-family: var(--font-mono);
    font-size: 12px;
    letter-spacing: 0.08em;
    color: var(--text-tertiary);
  }

  .asy-terminal__lamp {
    width: 8px;
    height: 8px;
    border-radius: var(--radius-full);
    background: $phosphor-text;
    box-shadow: 0 0 8px rgba(110, 231, 160, 0.8);
    animation: asy-breathe 2.4s var(--ease-in-out) infinite;
  }

  .asy-terminal__scope {
    margin-left: var(--space-2);
    padding: 2px 10px;
    border: 1px solid var(--border-default);
    border-radius: var(--radius-full);
    font-size: 11px;
    color: var(--text-secondary);
    background: var(--bg-elevated);
    cursor: help;
  }

  /* ===== 问候 ===== */
  .asy-terminal__greeting {
    font-size: 30px;
    font-weight: 600;
    color: var(--text-primary);
    line-height: 1.3;
    letter-spacing: 0.01em;
  }

  .asy-terminal__sub {
    margin-top: var(--space-2);
    margin-bottom: var(--space-5);
    font-size: 13px;
    color: var(--text-tertiary);
  }

  /* ===== Composer：深色终端块（同 SQL 代码块的深底先例） ===== */
  .asy-composer {
    position: relative;
    width: 100%;
    background: $phosphor-bg;
    border: 1px solid $phosphor-border;
    border-radius: var(--radius-2xl);
    box-shadow: var(--shadow-lg), inset 0 0 32px rgba(110, 231, 160, 0.05);
    padding: var(--space-4) var(--space-4) var(--space-3);
    overflow: hidden;
    transition: border-color var(--duration-normal) var(--ease-out), box-shadow var(--duration-normal) var(--ease-out);

    &:focus-within {
      border-color: $phosphor-dim;
      box-shadow: var(--shadow-lg), 0 0 0 3px rgba(110, 231, 160, 0.12), inset 0 0 32px rgba(110, 231, 160, 0.07);
    }

    /* 查询中：磷光扫描线掠过 */
    &--busy .asy-composer__scan {
      opacity: 1;
      animation: asy-scan 1.6s linear infinite;
    }
  }

  /* 扫描线（默认隐藏） */
  .asy-composer__scan {
    position: absolute;
    inset: 0;
    pointer-events: none;
    opacity: 0;
    background: linear-gradient(
      to bottom,
      transparent 0%,
      rgba(110, 231, 160, 0.06) 46%,
      rgba(110, 231, 160, 0.14) 50%,
      rgba(110, 231, 160, 0.06) 54%,
      transparent 100%
    );
    background-size: 100% 240%;
  }

  /* mono 提示符 */
  .asy-composer__prompt {
    position: absolute;
    top: 22px;
    left: 18px;
    font-family: var(--font-mono);
    font-size: 14px;
    color: $phosphor-dim;
    user-select: none;
  }

  .asy-composer__input {
    padding-left: var(--space-6);

    :deep(.el-textarea__inner) {
      background: transparent;
      border: none;
      box-shadow: none;
      padding: 0;
      font-family: var(--font-mono);
      font-size: 14px;
      line-height: 1.7;
      color: $phosphor-text;
      caret-color: $phosphor-bright;

      &::placeholder {
        color: rgba(110, 231, 160, 0.38);
      }
    }
  }

  .asy-composer__bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: var(--space-3);
  }

  .asy-composer__hint {
    font-family: var(--font-mono);
    font-size: 11px;
    letter-spacing: 0.06em;
    color: rgba(110, 231, 160, 0.4);
    user-select: none;
  }

  .asy-composer__actions {
    display: flex;
    align-items: center;
    gap: var(--space-2);
  }

  /* 附图入口：与「我的档案」同组的描边次级动作 */
  .asy-composer__attach {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    border: 1px solid $phosphor-border;
    border-radius: var(--radius-lg);
    background: transparent;
    color: $phosphor-dim;
    font-family: var(--font-mono);
    font-weight: 500;
    transition: all var(--duration-normal) var(--ease-out);

    &:hover:not(.is-disabled) {
      background: rgba(110, 231, 160, 0.1);
      border-color: $phosphor-dim;
      color: $phosphor-text;
    }

    &.is-disabled {
      background: transparent;
      border-color: rgba(30, 58, 43, 0.7);
      color: rgba(63, 143, 102, 0.45);
    }
  }

  /* 原生 file input 仅由「附图」按钮唤起 */
  .asy-composer__file {
    display: none;
  }

  /* 拖拽悬停：整块终端亮起，提示可投放 */
  .asy-composer--drop {
    border-color: $phosphor-text;
    box-shadow: var(--shadow-lg), 0 0 0 3px rgba(110, 231, 160, 0.18), inset 0 0 32px rgba(110, 231, 160, 0.1);
  }

  /* 附图缩略图条：提交成功后消失（上一张图仍可用于改道/建议追问） */
  .asy-attach {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    margin: var(--space-2) 0 var(--space-3) var(--space-6);
    padding: var(--space-2);
    border: 1px solid $phosphor-border;
    border-radius: var(--radius-lg);
    background: rgba(110, 231, 160, 0.05);
  }

  .asy-attach__thumb {
    width: 48px;
    height: 48px;
    object-fit: cover;
    border: 1px solid $phosphor-border;
    border-radius: var(--radius-md);
  }

  .asy-attach__meta {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
  }

  .asy-attach__name {
    font-family: var(--font-mono);
    font-size: 12px;
    color: $phosphor-text;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .asy-attach__size {
    font-family: var(--font-mono);
    font-size: 11px;
    color: $phosphor-dim;
  }

  .asy-attach__remove {
    margin-left: auto;
    width: 22px;
    height: 22px;
    border: 1px solid $phosphor-border;
    border-radius: var(--radius-full);
    background: transparent;
    color: $phosphor-dim;
    font-size: 11px;
    line-height: 1;
    cursor: pointer;
    transition: all var(--duration-fast) var(--ease-out);

    &:hover {
      border-color: $phosphor-text;
      color: $phosphor-text;
    }
  }

  /* 「我的档案」锁定入口：描边磷光，与实底主执行按钮区分主次 */
  .asy-composer__mine {
    border: 1px solid $phosphor-border;
    border-radius: var(--radius-lg);
    background: transparent;
    color: $phosphor-dim;
    font-family: var(--font-mono);
    font-weight: 500;
    transition: all var(--duration-normal) var(--ease-out);

    &:hover:not(.is-loading):not(.is-disabled) {
      background: rgba(110, 231, 160, 0.1);
      border-color: $phosphor-dim;
      color: $phosphor-text;
    }

    &.is-loading {
      border-color: $phosphor-dim;
      color: $phosphor-text;
    }

    &.is-disabled {
      background: transparent;
      border-color: rgba(30, 58, 43, 0.7);
      color: rgba(63, 143, 102, 0.45);
    }
  }

  /* 内嵌执行按钮：磷光绿实底，有问题可发时点亮 */
  .asy-composer__exec {
    border: none;
    border-radius: var(--radius-lg);
    background: rgba(110, 231, 160, 0.14);
    color: $phosphor-dim;
    font-family: var(--font-mono);
    font-weight: 600;
    transition: all var(--duration-normal) var(--ease-out);

    &--ready {
      background: $phosphor-text;
      color: $phosphor-bg;

      &:hover {
        background: $phosphor-bright;
        color: $phosphor-bg;
      }
    }

    &:hover:not(&--ready):not(.is-loading) {
      background: rgba(110, 231, 160, 0.22);
      color: $phosphor-text;
    }

    &.is-loading {
      background: rgba(110, 231, 160, 0.14);
      color: $phosphor-text;
    }
  }

  /* ===== 提示词：垂直渐虚堆叠（上清晰 → 下模糊） ===== */
  .asy-examples {
    width: 100%;
    margin-top: var(--space-8);
    display: flex;
    flex-direction: column;
  }

  .asy-examples__card {
    position: relative;
    display: flex;
    align-items: center;
    gap: var(--space-3);
    width: 100%;
    padding: var(--space-4) var(--space-5);
    text-align: left;
    background: var(--bg-elevated);
    border: 1px solid var(--border-default);
    border-radius: var(--radius-xl);
    box-shadow: var(--shadow-sm);
    cursor: pointer;
    transition:
      filter var(--duration-normal) var(--ease-out),
      opacity var(--duration-normal) var(--ease-out),
      transform var(--duration-normal) var(--ease-out),
      border-color var(--duration-normal) var(--ease-out),
      box-shadow var(--duration-normal) var(--ease-out);
    animation: asy-rise 0.5s var(--ease-out) both;
    animation-delay: calc(var(--stagger) * 90ms);

    /* 渐虚堆叠：负边距叠压 + blur/opacity 逐级递增 */
    & + & {
      margin-top: -10px;
      padding-top: calc(var(--space-4) + 10px);
    }

    &:nth-child(1) {
      filter: blur(0);
      opacity: 1;
      z-index: 3;
    }

    &:nth-child(2) {
      filter: blur(1.2px);
      opacity: 0.6;
      z-index: 2;
    }

    &:nth-child(3) {
      filter: blur(2.4px);
      opacity: 0.32;
      z-index: 1;
    }

    &:hover {
      filter: blur(0);
      opacity: 1;
      transform: translateY(-2px);
      border-color: var(--brand-500);
      box-shadow: var(--shadow-md);
      z-index: 5;
    }
  }

  .asy-examples__index {
    flex-shrink: 0;
    font-family: var(--font-mono);
    font-size: 12px;
    color: var(--text-quaternary);
  }

  .asy-examples__text {
    flex: 1;
    min-width: 0;
    font-size: 14px;
    color: var(--text-primary);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .asy-examples__load {
    flex-shrink: 0;
    font-family: var(--font-mono);
    font-size: 11px;
    color: var(--text-quaternary);
    opacity: 0;
    transition: opacity var(--duration-fast) var(--ease-out);
  }

  .asy-examples__card:hover .asy-examples__load {
    opacity: 1;
    color: var(--brand-600);
  }

  /* ===== boot 空态行：闪烁光标 ===== */
  .asy-boot {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-top: var(--space-10);
    font-family: var(--font-mono);
    font-size: 13px;
    color: var(--text-tertiary);
  }

  .asy-boot__chevron {
    color: $phosphor-dim;
  }

  .asy-boot__caret {
    width: 8px;
    height: 15px;
    background: $phosphor-dim;
    animation: asy-caret 1.1s steps(1) infinite;
  }

  /* ===== 结果区 ===== */
  &__result-card {
    width: 100%;
    max-width: 980px;
    margin: var(--space-6) auto 0;
    border: 1px solid var(--border-subtle);
    animation: asy-rise 0.4s var(--ease-out) both;
  }

  &__result-meta {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    margin-bottom: var(--space-3);
    font-family: var(--font-mono);
    font-size: 12px;
  }

  &__meta-dot {
    width: 7px;
    height: 7px;
    border-radius: var(--radius-full);
    background: var(--success-500);
    box-shadow: 0 0 6px rgba(16, 185, 129, 0.6);
  }

  &__meta-text {
    color: var(--text-secondary);

    &--dim {
      color: var(--text-quaternary);
    }
  }

  &__meta-scope {
    margin-left: auto;
    padding: 2px 10px;
    border: 1px solid var(--border-default);
    border-radius: var(--radius-full);
    font-size: 11px;
    color: var(--text-tertiary);
  }

  /* 阿实的档案结论：少说多列，正文交给 PersonDossier 的原始行 */
  &__dossier-answer {
    margin: 0 0 var(--space-4);
    font-size: 14px;
    line-height: 1.7;
    color: var(--text-secondary);
    white-space: pre-wrap;
  }

  /* 图片理解摘要：先于回答上屏（路由判定在此明示） */
  &__digest {
    margin: 0 0 var(--space-3);
    padding: var(--space-3) var(--space-4);
    border: 1px dashed var(--border-default);
    border-radius: var(--radius-lg);
    font-size: 13px;
    line-height: 1.7;
    color: var(--text-secondary);
    white-space: pre-wrap;
  }

  /* 改道：路由判定的人工推翻入口 */
  &__reroute {
    display: flex;
    align-items: center;
    gap: var(--space-1);
    margin-bottom: var(--space-3);
    font-size: 12px;
  }

  &__reroute-label {
    color: var(--text-tertiary);
  }

  /* 低置信/通道异常兜底：只给拍摄建议与文字引导，不给猜测性回答 */
  &__vision-fail {
    padding: var(--space-4);
    border: 1px solid var(--warning-500);
    border-radius: var(--radius-lg);
    background: var(--warning-50);
  }

  &__vision-fail-title {
    font-size: 14px;
    font-weight: 500;
    color: var(--warning-600);
  }

  &__vision-tips {
    margin: var(--space-2) 0 0;
    padding-left: var(--space-5);
    font-size: 13px;
    line-height: 1.9;
    color: var(--text-secondary);
  }

  /* 课表链路：草稿入口（查询不自动写回，点开才进草稿卡） */
  &__draft-entry {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    margin-top: var(--space-3);
    padding: var(--space-3) var(--space-4);
    border: 1px solid var(--border-default);
    border-radius: var(--radius-lg);
    background: var(--bg-elevated);
  }

  &__draft-hint {
    font-size: 13px;
    color: var(--text-secondary);
  }

  &__draft-actions {
    display: flex;
    align-items: center;
    gap: var(--space-3);
    margin-top: var(--space-3);
  }

  &__draft-note {
    font-size: 12px;
    color: var(--text-quaternary);
  }

  &__draft-errors {
    margin: var(--space-3) 0 0;
    padding-left: var(--space-5);
    font-size: 13px;
    line-height: 1.9;
    color: var(--danger-600);
  }

  &__draft-done {
    margin: var(--space-2) 0 0;
    font-size: 13px;
    color: var(--success-600);
  }

  /* 通知链路：结构化转述（标题/时间/地点/事项） */
  &__notice {
    margin-top: var(--space-3);
    padding: var(--space-4);
    border: 1px solid var(--border-default);
    border-radius: var(--radius-lg);
    background: var(--bg-elevated);
  }

  &__notice-title {
    margin-bottom: var(--space-2);
    font-size: 14px;
    font-weight: 600;
    color: var(--text-primary);
  }

  &__notice-fields {
    display: flex;
    flex-wrap: wrap;
    gap: var(--space-4);
    font-size: 13px;
    color: var(--text-secondary);
  }

  &__notice-items {
    margin: var(--space-2) 0 0;
    padding-left: var(--space-5);
    font-size: 13px;
    line-height: 1.9;
    color: var(--text-secondary);
  }

  &__sql-collapse {
    margin-bottom: var(--space-4);
    border: none;
  }

  &__sql-title {
    font-size: 13px;
    color: var(--text-secondary);
  }

  &__result-table {
    width: 100%;
  }

  &__empty-hint {
    padding: var(--space-6) var(--space-4);
    border: 1px dashed var(--border-default);
    border-radius: var(--radius-lg);
    font-size: 13px;
    color: var(--text-tertiary);
    text-align: center;
  }

  &__suggestions {
    display: flex;
    align-items: center;
    gap: var(--space-2);
    margin-top: var(--space-4);
    flex-wrap: wrap;
  }

  &__suggestions-label {
    font-size: 13px;
    color: var(--text-tertiary);
  }

  &__example-tag {
    cursor: pointer;

    &:hover {
      color: var(--brand-700);
      border-color: var(--brand-600);
    }
  }

  /* ===== 激活态（查询中/有结果）：内容在上、输入坞沉底，对齐主流 AI 智能体布局 ===== */
  &__main--active {
    overflow: hidden;

    /* 终端外壳退化为底部输入坞 */
    .asy-terminal {
      order: 2;
      flex-shrink: 0;
      margin-top: auto;
      padding-top: var(--space-4);
    }

    /* 机头/问候/示例卡属于空态引导，结果态隐去，让内容从顶部开始 */
    .asy-terminal__head,
    .asy-terminal__greeting,
    .asy-terminal__sub,
    .asy-examples {
      display: none;
    }

    /* 结果卡片占据上方空间，超高时卡片内部滚动 */
    .nlq-page__result-card {
      order: 1;
      flex: 0 1 auto;
      min-height: 0;
      overflow-y: auto;
    }
  }

  /* ===== 历史侧栏（样式保持原样） ===== */
  &__history {
    width: 304px;
    flex-shrink: 0;
    padding: var(--content-pad) var(--space-6);
    background: var(--bg-elevated);
    border-left: 1px solid var(--border-subtle);
    overflow-y: auto;
  }

  &__history-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: var(--space-4);
    font-size: 16px;
    font-weight: 500;
  }

  &__timeline {
    padding-left: var(--space-1);
  }

  &__history-item {
    cursor: default;
  }

  &__history-question {
    font-size: 14px;
    color: var(--text-primary);
    cursor: pointer;

    &:hover {
      color: var(--text-link);
    }
  }

  &__history-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-top: var(--space-1);
  }

  &__history-summary {
    font-size: 12px;
    color: var(--text-quaternary);
  }
}

/* ===== 动效 ===== */
@keyframes asy-breathe {
  0%, 100% { opacity: 1; box-shadow: 0 0 8px rgba(110, 231, 160, 0.8); }
  50% { opacity: 0.55; box-shadow: 0 0 3px rgba(110, 231, 160, 0.4); }
}

@keyframes asy-scan {
  0% { background-position: 0 120%; }
  100% { background-position: 0 -120%; }
}

@keyframes asy-caret {
  0%, 49% { opacity: 1; }
  50%, 100% { opacity: 0; }
}

@keyframes asy-rise {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
