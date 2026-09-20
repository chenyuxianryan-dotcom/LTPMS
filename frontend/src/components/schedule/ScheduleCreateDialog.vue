<template>
  <el-dialog
    :model-value="modelValue"
    title="新增课程"
    width="560px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @open="handleOpen"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="88px">
      <el-form-item v-if="selectTeacher" label="授课教师" prop="teacherId">
        <el-select v-model="form.teacherId" filterable placeholder="选择教师" style="width: 100%">
          <el-option
            v-for="teacher in teachers"
            :key="teacher.id"
            :label="`${teacher.realName ?? teacher.username}（${teacher.username}）`"
            :value="teacher.id"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="课程号" prop="courseCode">
        <el-input v-model="form.courseCode" maxlength="32" placeholder="如：CS201" />
      </el-form-item>

      <el-form-item label="课程名" prop="courseName">
        <el-input v-model="form.courseName" maxlength="64" placeholder="如：数据结构实验" />
      </el-form-item>

      <el-form-item label="实验学时">
        <el-input-number v-model="form.totalHours" :min="0" :max="200" style="width: 100%" />
      </el-form-item>

      <el-form-item label="上课地点" prop="labCode">
        <el-select v-model="form.labCode" filterable placeholder="选择实验室" style="width: 100%">
          <el-option
            v-for="lab in labs"
            :key="lab.labCode"
            :label="`${lab.labCode} ${lab.labName}`"
            :value="lab.labCode"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="上课班级">
        <el-input v-model="form.classNames" placeholder="如：2023级软件工程1班" />
      </el-form-item>

      <el-form-item label="选课人数" prop="studentCount">
        <el-input-number v-model="form.studentCount" :min="1" :max="500" style="width: 100%" />
      </el-form-item>

      <el-form-item label="上课时间" prop="rawScheduleText">
        <el-input v-model="form.rawScheduleText" placeholder="如：周一第1-2节(1-16周)" />
        <div class="schedule-create__hint">保存后按此文本解析时间槽，无法解析则不予保存</div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { apiGetLabOpeningOverview } from '@/api/labOpening';
import { apiCreateSchedule } from '@/api/schedule';
import { apiListTeachers } from '@/api/teacher';
import type { ITeacher } from '@/types/api';

const props = defineProps<{
  modelValue: boolean;
  semesterId: number | null;
  /** 管理员视角：需选择授课教师（教师视角自动归属本人） */
  selectTeacher?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void;
  (e: 'success'): void;
}>();

const saving = ref(false);
const formRef = ref<FormInstance>();
const labs = ref<{ labCode: string; labName: string }[]>([]);
const teachers = ref<ITeacher[]>([]);
const form = reactive<{
  teacherId: number | null;
  courseCode: string;
  courseName: string;
  totalHours: number | null;
  labCode: string;
  classNames: string;
  studentCount: number | null;
  rawScheduleText: string;
}>({
  teacherId: null,
  courseCode: '',
  courseName: '',
  totalHours: null,
  labCode: '',
  classNames: '',
  studentCount: null,
  rawScheduleText: '',
});

const rules: FormRules = {
  teacherId: [{ required: true, message: '请选择授课教师', trigger: 'change' }],
  courseCode: [{ required: true, message: '请输入课程号', trigger: 'blur' }],
  courseName: [{ required: true, message: '请输入课程名', trigger: 'blur' }],
  labCode: [{ required: true, message: '请选择上课地点', trigger: 'change' }],
  studentCount: [{ required: true, message: '请输入选课人数', trigger: 'change' }],
  rawScheduleText: [{ required: true, message: '请输入上课时间', trigger: 'blur' }],
};

async function handleOpen(): Promise<void> {
  form.teacherId = null;
  form.courseCode = '';
  form.courseName = '';
  form.totalHours = null;
  form.labCode = '';
  form.classNames = '';
  form.studentCount = null;
  form.rawScheduleText = '';
  formRef.value?.clearValidate();
  if (labs.value.length === 0 && props.semesterId) {
    // 实验室清单经「开放总览」获取：该接口对教师开放（/laboratories 仅管理员）
    const overview = await apiGetLabOpeningOverview(props.semesterId);
    labs.value = overview.labs.map((lab) => ({ labCode: lab.labCode, labName: lab.labName }));
  }
  if (props.selectTeacher && teachers.value.length === 0) {
    const page = await apiListTeachers({ page: 1, size: 500 });
    teachers.value = page.records;
  }
}

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid || !props.semesterId) return;
  saving.value = true;
  try {
    await apiCreateSchedule(props.semesterId, {
      courseCode: form.courseCode.trim(),
      courseName: form.courseName.trim(),
      totalHours: form.totalHours,
      teacherId: props.selectTeacher ? (form.teacherId ?? undefined) : undefined,
      labCode: form.labCode,
      classNames: form.classNames.trim() || undefined,
      studentCount: form.studentCount,
      rawScheduleText: form.rawScheduleText.trim(),
    });
    ElMessage.success('课程已新增');
    emit('update:modelValue', false);
    emit('success');
  } catch {
    // 错误由 request 拦截器统一提示
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped lang="scss">
.schedule-create {
  &__hint {
    font-size: 12px;
    color: var(--text-quaternary);
    line-height: 18px;
  }
}
</style>
